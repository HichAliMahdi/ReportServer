package com.reportserver.repository;

import com.reportserver.model.ReportThumbnail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReportThumbnailRepository extends JpaRepository<ReportThumbnail, Long> {
    Optional<ReportThumbnail> findByReportTemplateId(Long reportTemplateId);
    void deleteByReportTemplateId(Long reportTemplateId);
}
