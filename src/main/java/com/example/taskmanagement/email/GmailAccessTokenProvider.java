package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

@Component
public class GmailAccessTokenProvider {
    private static final long EXPIRY_SAFETY_SECONDS = 60;

    private final MailProperties properties;
    private final Clock clock;
    private final RestClient client;
    private CachedToken cachedToken;

    public GmailAccessTokenProvider(MailProperties properties, Clock clock,
                                    @Qualifier("gmailOAuthClient") RestClient client) {
        this.properties = properties;
        this.clock = clock;
        this.client = client;
    }

    public synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && cachedToken.expiresAt().isAfter(now.plusSeconds(EXPIRY_SAFETY_SECONDS))) {
            return cachedToken.value();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.gmailClientId());
        form.add("client_secret", properties.gmailClientSecret());
        form.add("refresh_token", properties.gmailRefreshToken());
        form.add("grant_type", "refresh_token");

        TokenResponse response = client.post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                || response.expiresIn() == null || response.expiresIn() <= 0) {
            throw new EmailDeliveryException("Google OAuth returned an incomplete access-token response");
        }
        cachedToken = new CachedToken(response.accessToken(), now.plusSeconds(response.expiresIn()));
        return cachedToken.value();
    }

    public synchronized void invalidate(String rejectedToken) {
        if (cachedToken != null && cachedToken.value().equals(rejectedToken)) {
            cachedToken = null;
        }
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
