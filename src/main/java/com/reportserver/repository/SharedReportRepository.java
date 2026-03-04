package com.reportserver.repository;

import com.reportserver.model.SharedReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedReportRepository extends JpaRepository<SharedReport, Long> {
    Optional<SharedReport> findByReportFileName(String reportFileName);
    List<SharedReport> findBySharedWithReadOnlyTrue();
    List<SharedReport> findAll();
}
