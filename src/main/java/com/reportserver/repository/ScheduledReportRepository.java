package com.reportserver.repository;

import com.reportserver.model.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, Long> {
    
    List<ScheduledReport> findByEnabledTrue();
    
    List<ScheduledReport> findByNextRunTimeLessThanEqualAndEnabledTrue(LocalDateTime dateTime);
    
    List<ScheduledReport> findByCreatedBy(String username);
}
