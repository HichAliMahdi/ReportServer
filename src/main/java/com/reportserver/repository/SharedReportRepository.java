package com.reportserver.repository;

import com.reportserver.model.SharedReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedReportRepository extends JpaRepository<SharedReport, Long> {
    Optional<SharedReport> findByReportFileName(String reportFileName);
    List<SharedReport> findBySharedWithReadOnlyTrue();
    Page<SharedReport> findBySharedWithReadOnlyTrue(Pageable pageable);
    Page<SharedReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SharedReport> findBySharedWithReadOnlyTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<SharedReport> findByCategoryContainingIgnoreCaseOrderByCreatedAtDesc(String category, Pageable pageable);
    Page<SharedReport> findByTagsContainingIgnoreCaseOrderByCreatedAtDesc(String tags, Pageable pageable);
    Page<SharedReport> findByCategoryContainingIgnoreCaseAndTagsContainingIgnoreCaseOrderByCreatedAtDesc(String category, String tags, Pageable pageable);
    List<SharedReport> findAll();
}
