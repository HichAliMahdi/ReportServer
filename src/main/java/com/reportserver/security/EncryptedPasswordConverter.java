package com.reportserver.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * JPA AttributeConverter that automatically encrypts/decrypts datasource passwords.
 * Applied to the password field in the DataSource entity.
 * Sensitive data is encrypted before being written to the database and decrypted when read.
 */
@Converter(autoApply = false) // Manual application to avoid applying to all String fields
public class EncryptedPasswordConverter implements AttributeConverter<String, String> {
    
    @Autowired
    private PasswordEncryptor encryptor;
    
    /**
     * Called when persisting (saving) the entity - encrypts the password
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        // Only encrypt if not already encrypted (basic check)
        if (!isEncrypted(attribute)) {
            return encryptor.encrypt(attribute);
        }
        return attribute;
    }
    
    /**
     * Called when retrieving the entity - decrypts the password
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        // Try to decrypt; if it fails or is plaintext, return as-is for backward compatibility
        if (isEncrypted(dbData)) {
            return encryptor.decrypt(dbData);
        }
        return dbData;
    }
    
    /**
     * Check if a string looks like it's encrypted (valid Base64 with reasonable length)
     */
    private boolean isEncrypted(String value) {
        if (value == null || value.length() < 20) {
            return false;
        }
        // Try to decode as Base64
        try {
            java.util.Base64.getDecoder().decode(value);
            // Additional heuristic: encrypted data usually has more Base64 padding characters
            return value.matches("^[A-Za-z0-9+/]*={0,2}$");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
