package com.reportserver.repository;

import com.reportserver.model.SmtpSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SmtpSettingsRepository extends JpaRepository<SmtpSettings, Long> {
    Optional<SmtpSettings> findTopByOrderByIdAsc();
}
