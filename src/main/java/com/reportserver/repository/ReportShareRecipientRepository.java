package com.reportserver.repository;

import com.reportserver.model.ReportShareRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportShareRecipientRepository extends JpaRepository<ReportShareRecipient, Long> {
    List<ReportShareRecipient> findByReport_Id(Long reportId);
    long countByReport_Id(Long reportId);
    void deleteByReport_Id(Long reportId);
}
