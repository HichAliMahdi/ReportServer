package com.reportserver.repository;

import com.reportserver.model.ReportDeliveryOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportDeliveryOptionRepository extends JpaRepository<ReportDeliveryOption, Long> {
    List<ReportDeliveryOption> findByScheduleId(Long scheduleId);
    List<ReportDeliveryOption> findByScheduleIdAndEnabledTrue(Long scheduleId);
    List<ReportDeliveryOption> findByScheduleIdAndType(Long scheduleId, ReportDeliveryOption.DeliveryType type);
    void deleteByScheduleId(Long scheduleId);
}
