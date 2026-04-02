package com.reportserver.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "smtp_settings")
@Getter
@Setter
@NoArgsConstructor
public class SmtpSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port = 587;

    @Column
    private String username;

    @Column(name = "password_encrypted", length = 1000)
    private String passwordEncrypted;

    @Column(name = "from_email")
    private String fromEmail;

    @Column(name = "auth_enabled", nullable = false)
    private boolean authEnabled = true;

    @Column(name = "starttls_enabled", nullable = false)
    private boolean starttlsEnabled = true;

    @Column(name = "starttls_required", nullable = false)
    private boolean starttlsRequired = true;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
