package com.photoapp.service;

import com.photoapp.exception.EncryptionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides AES-256-GCM encryption/decryption of binary streams (photo
 * originals and thumbnails) before they are persisted to GridFS.
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>A single static key is read from the {@code ENCRYPTION_KEY} environment
 *   variable (32 raw bytes, UTF-8). There is no key rotation support in this
 *   version: if the key must change, existing data must be manually
 *   decrypted with the old key and re-encrypted with the new one.</li>
 *   <li>Each encryption operation generates a fresh random 12-byte IV
 *   (as recommended for GCM) via {@link SecureRandom}. The IV is not secret
 *   and is stored alongside the ciphertext (base64-encoded on the
 *   {@code Photo} document) so it can be reused for decryption.</li>
 *   <li>GCM provides both confidentiality and integrity (authentication tag),
 *   so tampering with ciphertext is detected at decryption time.</li>
 * </ul>
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(@Value("${app.encryption.key}") String encryptionKey) {
        var keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "app.encryption.key (ENCRYPTION_KEY) must be exactly 32 bytes for AES-256, got " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Generates a new cryptographically random 12-byte IV suitable for a
     * single AES-GCM encryption operation. A fresh IV must be generated for
     * every encryption call to preserve GCM's security guarantees.
     */
    public byte[] generateIv() {
        var iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    /**
     * Wraps {@code destination} so that bytes written to the returned stream
     * are AES-256-GCM encrypted using the given IV before being forwarded to
     * {@code destination}. The caller must close the returned stream to
     * flush the authentication tag.
     */
    public OutputStream encryptingOutputStream(OutputStream destination, byte[] iv) {
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new CipherOutputStream(destination, cipher);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to initialize encryption cipher", e);
        }
    }

    /**
     * Wraps {@code source} so that bytes read from the returned stream are
     * the AES-256-GCM decrypted plaintext of {@code source}, using the
     * given IV. Throws {@link EncryptionException} if the ciphertext has
     * been tampered with (authentication tag mismatch) once fully consumed.
     */
    public InputStream decryptingInputStream(InputStream source, byte[] iv) {
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new CipherInputStream(source, cipher);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to initialize decryption cipher", e);
        }
    }

    /** Encrypts an in-memory byte array; returns the ciphertext (including the GCM auth tag). */
    public byte[] encrypt(byte[] plaintext, byte[] iv) {
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /** Decrypts an in-memory ciphertext previously produced by {@link #encrypt}. */
    public byte[] decrypt(byte[] ciphertext, byte[] iv) {
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }

    public static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}
