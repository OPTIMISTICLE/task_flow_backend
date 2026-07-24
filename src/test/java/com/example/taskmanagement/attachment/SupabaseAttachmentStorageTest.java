package com.example.taskmanagement.attachment;

import com.example.taskmanagement.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseAttachmentStorageTest {

    private MockRestServiceServer server;
    private SupabaseAttachmentStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        StorageProperties properties = new StorageProperties(
                StorageProperties.Provider.SUPABASE,
                "./unused",
                new StorageProperties.Supabase(
                        "https://project.supabase.co/",
                        "backend-secret-key",
                        "task-attachments"));
        storage = new SupabaseAttachmentStorage(properties, builder);
    }

    @Test
    void uploadsAndDownloadsThroughPrivateSupabaseStorage() throws Exception {
        byte[] artifact = "verified output".getBytes();
        server.expect(once(), requestTo(org.hamcrest.Matchers.matchesPattern(
                        "https://project\\.supabase\\.co/storage/v1/object/task-attachments/attachments/"
                                + "[0-9a-f-]{36}")))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("apikey", "backend-secret-key"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer backend-secret-key"))
                .andExpect(header("x-upsert", "false"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE))
                .andExpect(content().bytes(artifact))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo(org.hamcrest.Matchers.matchesPattern(
                        "https://project\\.supabase\\.co/storage/v1/object/task-attachments/attachments/"
                                + "[0-9a-f-]{36}")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("apikey", "backend-secret-key"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer backend-secret-key"))
                .andRespond(withSuccess(artifact, MediaType.TEXT_PLAIN));

        AttachmentStorage.StoredFile stored = storage.store(new MockMultipartFile(
                "file", "result.txt", MediaType.TEXT_PLAIN_VALUE, artifact));

        assertThat(stored.storedName()).isNotBlank();
        assertThat(stored.storagePath()).isEqualTo("attachments/" + stored.storedName());

        assertThat(storage.load(stored.storagePath()).getContentAsByteArray()).isEqualTo(artifact);
        server.verify();
    }

    @Test
    void deletesAnUploadedObjectDuringBestEffortCleanup() {
        String path = "attachments/00000000-0000-0000-0000-000000000001";
        server.expect(once(), requestTo(
                        "https://project.supabase.co/storage/v1/object/task-attachments"))
                .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                .andExpect(header("apikey", "backend-secret-key"))
                .andExpect(content().json("""
                        {"prefixes":["attachments/00000000-0000-0000-0000-000000000001"]}
                        """))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        storage.deleteQuietly(path);

        server.verify();
    }
}
