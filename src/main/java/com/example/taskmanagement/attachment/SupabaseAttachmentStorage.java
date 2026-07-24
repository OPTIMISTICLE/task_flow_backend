package com.example.taskmanagement.attachment;

import com.example.taskmanagement.common.StorageException;
import com.example.taskmanagement.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")
public class SupabaseAttachmentStorage implements AttachmentStorage {

    private static final String OBJECT_DIRECTORY = "attachments";

    private final RestClient restClient;
    private final String bucket;

    public SupabaseAttachmentStorage(StorageProperties properties, RestClient.Builder restClientBuilder) {
        StorageProperties.Supabase settings = requireSettings(properties.supabase());
        String baseUrl = normalizeBaseUrl(settings.url());
        String secretKey = requireValue(settings.secretKey(), "SUPABASE_SECRET_KEY");
        this.bucket = requireValue(settings.bucket(), "SUPABASE_STORAGE_BUCKET");
        this.restClient = restClientBuilder
                .baseUrl(baseUrl + "/storage/v1")
                .defaultHeader("apikey", secretKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .build();
    }

    @Override
    public StoredFile store(MultipartFile file) {
        String storedName = UUID.randomUUID().toString();
        String storagePath = OBJECT_DIRECTORY + "/" + storedName;
        try {
            restClient.post()
                    .uri(builder -> objectUri(builder, storagePath))
                    .contentType(contentType(file))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .header("x-upsert", "false")
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
            return new StoredFile(storedName, storagePath);
        } catch (IOException exception) {
            throw new StorageException("Could not read attachment content.", exception);
        } catch (RestClientResponseException exception) {
            throw remoteFailure("upload", exception);
        } catch (RestClientException exception) {
            throw new StorageException("Supabase Storage upload failed.", exception);
        }
    }

    @Override
    public Resource load(String storagePath) {
        try {
            byte[] content = restClient.get()
                    .uri(builder -> objectUri(builder, storagePath))
                    .retrieve()
                    .body(byte[].class);
            if (content == null) {
                throw new StorageException("Supabase Storage returned empty attachment content.");
            }
            return new ByteArrayResource(content);
        } catch (RestClientResponseException exception) {
            throw remoteFailure("download", exception);
        } catch (RestClientException exception) {
            throw new StorageException("Supabase Storage download failed.", exception);
        }
    }

    @Override
    public void deleteQuietly(String storagePath) {
        try {
            validateStoragePath(storagePath);
            restClient.method(HttpMethod.DELETE)
                    .uri(builder -> builder.pathSegment("object", bucket).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", new String[]{storagePath}))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | StorageException ignored) {
            // Best-effort cleanup after a failed database write.
        }
    }

    private URI objectUri(UriBuilder builder, String storagePath) {
        String storedName = validateStoragePath(storagePath);
        return builder.pathSegment("object", bucket, OBJECT_DIRECTORY, storedName).build();
    }

    private String validateStoragePath(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            throw new StorageException("Attachment content is unavailable.");
        }
        String prefix = OBJECT_DIRECTORY + "/";
        if (!storagePath.startsWith(prefix)) {
            throw new StorageException("Attachment content is unavailable.");
        }
        String storedName = storagePath.substring(prefix.length());
        try {
            UUID.fromString(storedName);
        } catch (IllegalArgumentException exception) {
            throw new StorageException("Attachment content is unavailable.", exception);
        }
        return storedName;
    }

    private MediaType contentType(MultipartFile file) {
        if (!StringUtils.hasText(file.getContentType())) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(file.getContentType());
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private StorageException remoteFailure(String operation, RestClientResponseException exception) {
        return new StorageException("Supabase Storage " + operation + " failed with status "
                + exception.getStatusCode().value() + ".", exception);
    }

    private StorageProperties.Supabase requireSettings(StorageProperties.Supabase settings) {
        if (settings == null) {
            throw new IllegalStateException("Supabase storage configuration is missing.");
        }
        return settings;
    }

    private String normalizeBaseUrl(String value) {
        String baseUrl = requireValue(value, "SUPABASE_URL").replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("SUPABASE_URL must be a valid absolute URL.", exception);
        }
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("SUPABASE_URL must be an absolute HTTP(S) URL.");
        }
        return baseUrl;
    }

    private String requireValue(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentVariable
                    + " is required when ATTACHMENT_STORAGE_PROVIDER=supabase.");
        }
        return value.strip();
    }
}
