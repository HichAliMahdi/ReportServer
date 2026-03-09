package com.reportserver.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
    import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Encrypts and decrypts sensitive data (like datasource passwords)
 * using AES-256 symmetric encryption with a key from environment variables.
 */
@Service
public class PasswordEncryptor {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordEncryptor.class);
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    
    @Value("${reportserver.encryption.key}")
    private String encryptionKeyString;
    
    private SecretKey secretKey;
    
    /**
     * Initialize the secret key on first use
     */
    private synchronized SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = deriveKeyFromString(encryptionKeyString);
        }
        return secretKey;
    }
    
    /**
     * Derive a 256-bit key from a string using SHA-256 hashing
     */
    private SecretKey deriveKeyFromString(String keyString) {
        try {
            // Create a hash of the key string to ensure it's 32 bytes (256 bits)
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashedKey = md.digest(keyString.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hashedKey, 0, hashedKey.length, ALGORITHM);
        } catch (Exception e) {
            logger.error("Failed to derive encryption key", e);
            throw new RuntimeException("Encryption initialization failed", e);
        }
    }
    
    /**
     * Encrypt plaintext password
     * @param plaintext the password to encrypt
     * @return Base64-encoded encrypted password
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.trim().isEmpty()) {
            return plaintext;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt password", e);
        }
    }
    
    /**
     * Decrypt an encrypted password
     * @param encrypted Base64-encoded encrypted password
     * @return decrypted plaintext password
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.trim().isEmpty()) {
            return encrypted;
        }
        
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey());
            byte[] decodedBytes = Base64.getDecoder().decode(encrypted);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid encrypted password format - treating as plaintext");
            return encrypted; // Return as-is if not validly encrypted (for backward compatibility)
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt password", e);
        }
    }
}
