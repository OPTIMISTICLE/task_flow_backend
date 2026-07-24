package com.example.taskmanagement.email;

import com.example.taskmanagement.config.MailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailAccessTokenProviderTest {
    private MockRestServiceServer server;
    private GmailAccessTokenProvider tokens;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://oauth2.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-07-24T12:00:00Z"));
        tokens = new GmailAccessTokenProvider(properties(), clock, builder.build());
    }

    @Test
    void refreshesAndCachesAnAccessToken() {
        server.expect(once(), requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("client_id=client-id"),
                        containsString("client_secret=client-secret"),
                        containsString("refresh_token=refresh-token"),
                        containsString("grant_type=refresh_token"))))
                .andRespond(withSuccess("""
                        {"access_token":"access-1","expires_in":3600,"token_type":"Bearer"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(tokens.accessToken()).isEqualTo("access-1");
        assertThat(tokens.accessToken()).isEqualTo("access-1");
        server.verify();
    }

    @Test
    void refreshesBeforeTheCachedTokenExpires() {
        server.expect(once(), requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access-1\",\"expires_in\":120}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access-2\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        assertThat(tokens.accessToken()).isEqualTo("access-1");
        clock.advance(Duration.ofSeconds(61));

        assertThat(tokens.accessToken()).isEqualTo("access-2");
        server.verify();
    }

    @Test
    void refreshesAgainAfterTheRejectedTokenIsInvalidated() {
        server.expect(once(), requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access-1\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"access-2\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        String rejected = tokens.accessToken();
        tokens.invalidate(rejected);

        assertThat(tokens.accessToken()).isEqualTo("access-2");
        server.verify();
    }

    private MailProperties properties() {
        return new MailProperties(true, "TaskFlow", "sender@gmail.com", "client-id",
                "client-secret", "refresh-token", "https://taskflow.example");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
