package com.reportserver.repository;

import com.reportserver.model.ReportExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportExecutionLogRepository extends JpaRepository<ReportExecutionLog, Long> {
    Page<ReportExecutionLog> findByExecutedByOrderByStartedAtDesc(String executedBy, Pageable pageable);
    Page<ReportExecutionLog> findAllByOrderByStartedAtDesc(Pageable pageable);
}
