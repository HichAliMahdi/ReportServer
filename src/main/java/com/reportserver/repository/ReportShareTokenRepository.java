package com.reportserver.repository;

import com.reportserver.model.ReportShareToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportShareTokenRepository extends JpaRepository<ReportShareToken, Long> {
    Optional<ReportShareToken> findByToken(String token);
    List<ReportShareToken> findByReportFileNameAndRevokedFalse(String reportFileName);
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
