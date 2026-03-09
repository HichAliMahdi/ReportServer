package com.reportserver.service;

import com.reportserver.model.ApiKey;
import com.reportserver.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Generate a new API key for a user
     * Returns the plaintext key once (must be saved by user)
     */
    public ApiKey generateNewKey(String userId, String keyName, String permissions) {
        try {
            // Generate 32-byte random key and encode to Base64
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String plainKey = "reportkey_" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

            ApiKey apiKey = new ApiKey(keyName, userId, plainKey);
            apiKey.setPermissions(permissions);

            ApiKey saved = apiKeyRepository.save(apiKey);
            logger.info("Generated new API key '{}' for user {}", keyName, userId);

            return saved; // plainKey is included but will only be shown once to user

        } catch (Exception e) {
            logger.error("Failed to generate API key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate API key", e);
        }
    }

    /**
     * Validate an API key (called during API authentication)
     */
    public Optional<ApiKey> validateKey(String plainKey) {
        try {
            // Hash the provided key
            String keyHash = ApiKey.hashKey(plainKey);

            Optional<ApiKey> key = apiKeyRepository.findByKeyHash(keyHash);
            if (key.isPresent()) {
                ApiKey apiKey = key.get();
                if (apiKey.getActive()) {
                    // Update last used timestamp
                    apiKey.setLastUsedAt(LocalDateTime.now());
                    apiKey.setLastUsedTimestamp(System.currentTimeMillis());
                    apiKeyRepository.save(apiKey);
                    return Optional.of(apiKey);
                }
            }
            return Optional.empty();

        } catch (Exception e) {
            logger.error("Failed to validate API key: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get all keys for a user (paginated)
     */
    public Page<ApiKey> getUserKeys(String userId, Pageable pageable) {
        return apiKeyRepository.findByUserIdAndActiveTrue(userId, pageable);
    }

    /**
     * Get all active keys for a user (non-paginated for quick lookup)
     */
    public List<ApiKey> getUserActiveKeys(String userId) {
        return apiKeyRepository.findByUserIdAndActiveTrueOrderByLastUsedTimestampDesc(userId);
    }

    /**
     * Revoke an API key
     */
    public void revokeKey(String userId, Long keyId) {
        try {
            Optional<ApiKey> key = apiKeyRepository.findById(keyId);
            if (key.isPresent() && key.get().getUserId().equals(userId)) {
                key.get().setActive(false);
                apiKeyRepository.save(key.get());
                logger.info("Revoked API key {} for user {}", keyId, userId);
            } else {
                throw new RuntimeException("API key not found or does not belong to user");
            }
        } catch (Exception e) {
            logger.error("Failed to revoke API key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to revoke API key", e);
        }
    }

    /**
     * Rename an API key
     */
    public ApiKey renameKey(String userId, Long keyId, String newName) {
        try {
            Optional<ApiKey> key = apiKeyRepository.findById(keyId);
            if (key.isPresent() && key.get().getUserId().equals(userId)) {
                key.get().setName(newName);
                ApiKey updated = apiKeyRepository.save(key.get());
                logger.info("Renamed API key {} for user {} to '{}'", keyId, userId, newName);
                return updated;
            } else {
                throw new RuntimeException("API key not found or does not belong to user");
            }
        } catch (Exception e) {
            logger.error("Failed to rename API key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to rename API key", e);
        }
    }

    /**
     * Check if user can perform action based on key permissions
     */
    public boolean hasPermission(ApiKey apiKey, String requiredPermission) {
        if (apiKey == null || apiKey.getPermissions() == null) {
            return false;
        }

        // Simple permission check: look for permission string in permissions JSON
        return apiKey.getPermissions().contains(requiredPermission);
    }
}
