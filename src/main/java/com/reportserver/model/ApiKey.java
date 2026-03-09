package com.reportserver.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Entity
@Table(name = "api_keys", uniqueConstraints = {
    @UniqueConstraint(columnNames = "key_hash")
})
public class ApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name; // User-friendly name for the key

    @Column(nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, length = 500)
    private String keyHash; // BCrypt hash of the actual key for security

    @Column(nullable = false, length = 500)
    private String plainKey; // Shown only once to user; not persisted after creation response

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastUsedAt;

    @Column(nullable = false)
    private Long lastUsedTimestamp = 0L; // For efficient sorting by last used

    @Column(columnDefinition = "LONGTEXT")
    private String permissions; // JSON array of permissions: ["reports:read", "reports:generate", "schedules:manage"]

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public ApiKey() {}

    public ApiKey(String name, String userId, String plainKey) {
        this.name = name;
        this.userId = userId;
        this.plainKey = plainKey;
        this.keyHash = hashKey(plainKey);
        this.active = true;
    }

    public static String hashKey(String plainKey) {
        return new BCryptPasswordEncoder().encode(plainKey);
    }

    public boolean validateKey(String plainKey) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.matches(plainKey, this.keyHash);
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public String getPlainKey() { return plainKey; }
    public void setPlainKey(String plainKey) { this.plainKey = plainKey; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public Long getLastUsedTimestamp() { return lastUsedTimestamp; }
    public void setLastUsedTimestamp(Long lastUsedTimestamp) { this.lastUsedTimestamp = lastUsedTimestamp; }

    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
}
