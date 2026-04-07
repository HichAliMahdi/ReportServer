package com.reportserver.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "installation_state")
@Getter
@Setter
@NoArgsConstructor
public class InstallationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_configured", nullable = false)
    private boolean adminConfigured = false;

    @Column(name = "smtp_configured", nullable = false)
    private boolean smtpConfigured = false;

    @Column(name = "setup_completed", nullable = false)
    private boolean setupCompleted = false;

    @Column(name = "default_language", nullable = false, length = 10)
    private String defaultLanguage = "en";

    @Column(name = "setup_completed_at")
    private LocalDateTime setupCompletedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        if (defaultLanguage == null || defaultLanguage.isBlank()) {
            defaultLanguage = "en";
        }
        updatedAt = LocalDateTime.now();
    }
}
