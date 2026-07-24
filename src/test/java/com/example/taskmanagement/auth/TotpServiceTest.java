package com.example.taskmanagement.auth;

import com.example.taskmanagement.config.IdentityProperties;
import com.example.taskmanagement.user.User;
import com.example.taskmanagement.user.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    private final TotpService service = new TotpService();

    @Test
    void acceptsTheRfcVectorWithinTheCurrentStepAndRejectsReplay() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        TotpService.Match match = service.verify(secret, "287082", Instant.ofEpochSecond(59), null);

        assertThat(match).isNotNull();
        assertThat(match.step()).isEqualTo(1);
        assertThat(service.verify(secret, "287082", Instant.ofEpochSecond(59), match.step())).isNull();
    }

    @Test
    void encryptsAndAuthenticatesTheStoredSecret() {
        IdentityProperties properties = new IdentityProperties(Duration.ofHours(12), Duration.ofHours(24),
                Duration.ofMinutes(30), Duration.ofMinutes(5),
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        MfaCryptoService crypto = new MfaCryptoService(properties);
        MfaCryptoService.EncryptedSecret encrypted = crypto.encrypt("SECRET-VALUE");
        MfaTotpCredential credential = new MfaTotpCredential(
                new User("Mfa", "User", "mfa@example.test", "hash", UserRole.WORKER),
                encrypted.ciphertext(), encrypted.nonce());

        assertThat(encrypted.ciphertext()).doesNotContain("SECRET-VALUE");
        assertThat(crypto.decrypt(credential)).isEqualTo("SECRET-VALUE");
    }
}
