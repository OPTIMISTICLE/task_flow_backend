package com.example.taskmanagement.auth;

import com.example.taskmanagement.config.IdentityProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class MfaCryptoService {
    private static final int TAG_BITS = 128;
    private final IdentityProperties properties;
    private final SecureRandom random = new SecureRandom();

    public MfaCryptoService(IdentityProperties properties) {
        this.properties = properties;
        key();
    }

    public EncryptedSecret encrypt(String secret) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not protect the MFA secret", exception);
        }
    }

    public String decrypt(MfaTotpCredential credential) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS,
                    Base64.getDecoder().decode(credential.getNonce())));
            return new String(cipher.doFinal(Base64.getDecoder().decode(credential.getEncryptedSecret())),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not read the MFA secret", exception);
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] decoded = Base64.getDecoder().decode(properties.mfaEncryptionKey());
            if (decoded.length != 32) {
                throw new IllegalStateException("MFA_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MFA_ENCRYPTION_KEY must be valid base64", exception);
        }
    }

    public record EncryptedSecret(String ciphertext, String nonce) {
    }
}
