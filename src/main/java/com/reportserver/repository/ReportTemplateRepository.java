package com.reportserver.repository;

import com.reportserver.model.ReportTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {
    Optional<ReportTemplate> findByReportFileName(String reportFileName);

    Page<ReportTemplate> findByCategoryContainingIgnoreCase(String category, Pageable pageable);

    Page<ReportTemplate> findByTagsContainingIgnoreCase(String tag, Pageable pageable);

    Page<ReportTemplate> findByCategoryContainingIgnoreCaseAndTagsContainingIgnoreCase(String category, String tags, Pageable pageable);
}
