package com.reportserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportserver.dto.ScheduledReportDTO;
import com.reportserver.model.ScheduledReport;
import com.reportserver.repository.ScheduledReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScheduledReportService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledReportService.class);

    @Autowired
    private ScheduledReportRepository scheduledReportRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public List<ScheduledReportDTO> getAllScheduledReports() {
        return scheduledReportRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ScheduledReportDTO> getScheduledReportsByUser(String username) {
        return scheduledReportRepository.findByCreatedBy(username).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ScheduledReportDTO getScheduledReportById(Long id) {
        return scheduledReportRepository.findById(id)
                .map(this::convertToDTO)
                .orElse(null);
    }

    public ScheduledReportDTO createScheduledReport(ScheduledReportDTO dto, String username) {
        ScheduledReport scheduledReport = convertToEntity(dto);
        scheduledReport.setCreatedBy(username);
        scheduledReport.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        
        // Calculate next run time
        LocalDateTime nextRun = calculateNextRunTime(scheduledReport);
        scheduledReport.setNextRunTime(nextRun);
        
        ScheduledReport saved = scheduledReportRepository.save(scheduledReport);
        logger.info("Created scheduled report: {} by user: {}, next run: {}", 
                   saved.getName(), username, nextRun);
        
        return convertToDTO(saved);
    }

    public ScheduledReportDTO updateScheduledReport(Long id, ScheduledReportDTO dto) {
        ScheduledReport existing = scheduledReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Scheduled report not found: " + id));
        
        updateEntityFromDTO(existing, dto);
        
        // Recalculate next run time if schedule parameters changed
        LocalDateTime nextRun = calculateNextRunTime(existing);
        existing.setNextRunTime(nextRun);
        
        ScheduledReport updated = scheduledReportRepository.save(existing);
        logger.info("Updated scheduled report: {}, next run: {}", updated.getName(), nextRun);
        
        return convertToDTO(updated);
    }

    public void deleteScheduledReport(Long id) {
        scheduledReportRepository.deleteById(id);
        logger.info("Deleted scheduled report: {}", id);
    }

    public void toggleScheduledReport(Long id, boolean enabled) {
        ScheduledReport report = scheduledReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Scheduled report not found: " + id));
        
        report.setEnabled(enabled);
        
        if (enabled) {
            // Recalculate next run time when enabling
            LocalDateTime nextRun = calculateNextRunTime(report);
            report.setNextRunTime(nextRun);
        }
        
        scheduledReportRepository.save(report);
        logger.info("Toggled scheduled report: {} to {}", report.getName(), enabled);
    }

    public LocalDateTime calculateNextRunTime(ScheduledReport schedule) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun;
        
        switch (schedule.getScheduleType().toUpperCase()) {
            case "HOURLY":
                nextRun = now.plusHours(1)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                break;
                
            case "DAILY":
                nextRun = now.plusDays(1)
                        .withHour(schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                
                // If the time hasn't passed today, schedule for today
                LocalDateTime todayRun = now.withHour(schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                if (todayRun.isAfter(now)) {
                    nextRun = todayRun;
                }
                break;
                
            case "WEEKLY":
                DayOfWeek targetDay = DayOfWeek.of(schedule.getDayOfWeek() != null ? schedule.getDayOfWeek() : 1);
                nextRun = now.with(TemporalAdjusters.next(targetDay))
                        .withHour(schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                
                // If the day and time hasn't passed this week, schedule for this week
                LocalDateTime thisWeekRun = now.with(TemporalAdjusters.nextOrSame(targetDay))
                        .withHour(schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                if (thisWeekRun.isAfter(now)) {
                    nextRun = thisWeekRun;
                }
                break;
                
            case "MONTHLY":
                int dayOfMonth = schedule.getDayOfMonth() != null ? schedule.getDayOfMonth() : 1;
                nextRun = now.plusMonths(1)
                        .withDayOfMonth(Math.min(dayOfMonth, now.plusMonths(1).toLocalDate().lengthOfMonth()))
                        .withHour(schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                
                // If the day and time hasn't passed this month, schedule for this month
                LocalDateTime thisMonthRun = now.withDayOfMonth(Math.min(dayOfMonth, now.toLocalDate().lengthOfMonth()))
                        .withHour(schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                if (thisMonthRun.isAfter(now)) {
                    nextRun = thisMonthRun;
                }
                break;
                
            case "YEARLY":
                int month = schedule.getMonthOfYear() != null ? schedule.getMonthOfYear() : 1;
                int day = schedule.getDayOfMonth() != null ? schedule.getDayOfMonth() : 1;
                
                nextRun = now.plusYears(1)
                        .withMonth(month)
                        .withDayOfMonth(Math.min(day, LocalDateTime.of(now.getYear() + 1, month, 1, 0, 0).toLocalDate().lengthOfMonth()))
                        .withHour(schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                
                // If the date and time hasn't passed this year, schedule for this year
                LocalDateTime thisYearRun = now.withMonth(month)
                        .withDayOfMonth(Math.min(day, LocalDateTime.of(now.getYear(), month, 1, 0, 0).toLocalDate().lengthOfMonth()))
                        .withHour(schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0)
                        .withMinute(schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0)
                        .withSecond(0)
                        .withNano(0);
                if (thisYearRun.isAfter(now)) {
                    nextRun = thisYearRun;
                }
                break;
                
            default:
                throw new IllegalArgumentException("Invalid schedule type: " + schedule.getScheduleType());
        }
        
        return nextRun;
    }

    private ScheduledReportDTO convertToDTO(ScheduledReport entity) {
        ScheduledReportDTO dto = new ScheduledReportDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setReportName(entity.getReportName());
        dto.setFormat(entity.getFormat());
        dto.setScheduleType(entity.getScheduleType());
        dto.setDatasourceId(entity.getDatasourceId());
        dto.setEnabled(entity.getEnabled());
        dto.setLastRunTime(entity.getLastRunTime());
        dto.setNextRunTime(entity.getNextRunTime());
        dto.setOutputPath(entity.getOutputPath());
        dto.setDescription(entity.getDescription());
        dto.setDayOfWeek(entity.getDayOfWeek());
        dto.setDayOfMonth(entity.getDayOfMonth());
        dto.setMonthOfYear(entity.getMonthOfYear());
        dto.setHourOfDay(entity.getHourOfDay());
        dto.setMinuteOfHour(entity.getMinuteOfHour());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        
        // Convert JSON string to Map
        if (entity.getParameters() != null && !entity.getParameters().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = objectMapper.readValue(entity.getParameters(), Map.class);
                dto.setParameters(params);
            } catch (JsonProcessingException e) {
                logger.error("Error parsing parameters JSON", e);
            }
        }
        
        return dto;
    }

    private ScheduledReport convertToEntity(ScheduledReportDTO dto) {
        ScheduledReport entity = new ScheduledReport();
        updateEntityFromDTO(entity, dto);
        return entity;
    }

    private void updateEntityFromDTO(ScheduledReport entity, ScheduledReportDTO dto) {
        entity.setName(dto.getName());
        entity.setReportName(dto.getReportName());
        entity.setFormat(dto.getFormat());
        entity.setScheduleType(dto.getScheduleType());
        entity.setDatasourceId(dto.getDatasourceId());
        entity.setEnabled(dto.getEnabled());
        entity.setOutputPath(dto.getOutputPath());
        entity.setDescription(dto.getDescription());
        entity.setDayOfWeek(dto.getDayOfWeek());
        entity.setDayOfMonth(dto.getDayOfMonth());
        entity.setMonthOfYear(dto.getMonthOfYear());
        entity.setHourOfDay(dto.getHourOfDay());
        entity.setMinuteOfHour(dto.getMinuteOfHour());
        
        // Convert Map to JSON string
        if (dto.getParameters() != null) {
            try {
                String paramsJson = objectMapper.writeValueAsString(dto.getParameters());
                entity.setParameters(paramsJson);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing parameters to JSON", e);
            }
        }
    }

    public void updateLastRunTime(Long id, LocalDateTime lastRunTime) {
        ScheduledReport report = scheduledReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Scheduled report not found: " + id));
        
        report.setLastRunTime(lastRunTime);
        
        // Calculate and set next run time
        LocalDateTime nextRun = calculateNextRunTime(report);
        report.setNextRunTime(nextRun);
        
        scheduledReportRepository.save(report);
    }
}
