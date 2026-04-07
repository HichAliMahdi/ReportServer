package com.reportserver.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "report_share_recipients",
    uniqueConstraints = @UniqueConstraint(name = "uk_report_share_recipients_report_user", columnNames = {"report_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class ReportShareRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private SharedReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "shared_at", nullable = false)
    private LocalDateTime sharedAt;

    @Column(name = "shared_by", nullable = false, length = 255)
    private String sharedBy;

    @PrePersist
    protected void onCreate() {
        if (sharedAt == null) {
            sharedAt = LocalDateTime.now();
        }
    }
}
