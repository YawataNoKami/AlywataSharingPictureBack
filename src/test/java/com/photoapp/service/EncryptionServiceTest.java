package com.photoapp.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    // Exactly 32 bytes for AES-256, matching the constraint enforced by EncryptionService.
    private static final String TEST_KEY = "0123456789abcdef0123456789abcdef";

    private final EncryptionService encryptionService = new EncryptionService(TEST_KEY);

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        var plaintext = "This is a very sensitive photo caption.".getBytes(StandardCharsets.UTF_8);
        var iv = encryptionService.generateIv();

        var ciphertext = encryptionService.encrypt(plaintext, iv);
        var decrypted = encryptionService.decrypt(ciphertext, iv);

        assertThat(decrypted).isEqualTo(plaintext);
        assertThat(ciphertext).isNotEqualTo(plaintext);
    }

    @Test
    void generateIv_producesADifferentIvOnEachCall() {
        Set<String> seenIvs = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            var iv = encryptionService.generateIv();
            assertThat(iv).hasSize(12);
            seenIvs.add(EncryptionService.toBase64(iv));
        }
        assertThat(seenIvs).hasSize(50);
    }

    @Test
    void decrypt_withWrongIv_throwsEncryptionException() {
        var plaintext = "secret".getBytes(StandardCharsets.UTF_8);
        var iv = encryptionService.generateIv();
        var otherIv = encryptionService.generateIv();

        var ciphertext = encryptionService.encrypt(plaintext, iv);

        assertThatThrownBy(() -> encryptionService.decrypt(ciphertext, otherIv))
                .isInstanceOf(com.photoapp.exception.EncryptionException.class);
    }

    @Test
    void constructor_rejectsKeyWithWrongLength() {
        assertThatThrownBy(() -> new EncryptionService("too-short-key"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void streamingEncryptThenDecrypt_returnsOriginalPlaintext() throws Exception {
        var plaintext = "Streamed content for a large photo file".repeat(1000).getBytes(StandardCharsets.UTF_8);
        var iv = encryptionService.generateIv();

        var encryptedBuffer = new java.io.ByteArrayOutputStream();
        try (var encryptingOut = encryptionService.encryptingOutputStream(encryptedBuffer, iv)) {
            encryptingOut.write(plaintext);
        }

        try (var decryptingIn = encryptionService.decryptingInputStream(
                new java.io.ByteArrayInputStream(encryptedBuffer.toByteArray()), iv)) {
            var decrypted = decryptingIn.readAllBytes();
            assertThat(decrypted).isEqualTo(plaintext);
        }
    }
}
