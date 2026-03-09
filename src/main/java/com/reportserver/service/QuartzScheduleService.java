package com.reportserver.service;

import com.reportserver.model.ScheduledReport;
import com.reportserver.repository.ScheduledReportRepository;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
public class QuartzScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(QuartzScheduleService.class);

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private ScheduledReportRepository scheduledReportRepository;

    @PostConstruct
    public void initializeFromDatabase() {
        try {
            List<ScheduledReport> enabledSchedules = scheduledReportRepository.findByEnabledTrue();
            for (ScheduledReport schedule : enabledSchedules) {
                scheduleOrUpdate(schedule);
            }
            logger.info("Quartz initialized with {} enabled schedules", enabledSchedules.size());
        } catch (Exception e) {
            logger.error("Failed to initialize Quartz schedules from database", e);
        }
    }

    public void scheduleOrUpdate(ScheduledReport schedule) {
        String cronExpression = resolveCronExpression(schedule);
        if (schedule.getId() == null || cronExpression == null || cronExpression.isBlank()) {
            return;
        }

        try {
            JobKey jobKey = JobKey.jobKey(jobKey(schedule.getId()), "report-schedules");
            TriggerKey triggerKey = TriggerKey.triggerKey(triggerKey(schedule.getId()), "report-schedules");

            JobDetail jobDetail = JobBuilder.newJob(QuartzReportJob.class)
                .withIdentity(jobKey)
                .usingJobData("scheduleId", schedule.getId())
                .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .withSchedule(
                    CronScheduleBuilder.cronSchedule(cronExpression)
                        .withMisfireHandlingInstructionDoNothing()
                )
                .build();

            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            scheduler.scheduleJob(jobDetail, trigger);
            logger.info("Scheduled Quartz job for report {} with cron {}", schedule.getName(), cronExpression);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule Quartz job for scheduleId=" + schedule.getId(), e);
        }
    }

    public void unschedule(Long scheduleId) {
        try {
            JobKey jobKey = JobKey.jobKey(jobKey(scheduleId), "report-schedules");
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to unschedule Quartz job for scheduleId=" + scheduleId, e);
        }
    }

    private String jobKey(Long scheduleId) {
        return "scheduled-report-" + scheduleId;
    }

    private String triggerKey(Long scheduleId) {
        return "scheduled-report-trigger-" + scheduleId;
    }

    private String resolveCronExpression(ScheduledReport schedule) {
        if (schedule.getCronExpression() != null && !schedule.getCronExpression().isBlank()) {
            return schedule.getCronExpression();
        }
        int minute = schedule.getMinuteOfHour() != null ? schedule.getMinuteOfHour() : 0;
        int hour = schedule.getHourOfDay() != null ? schedule.getHourOfDay() : 0;

        if (schedule.getScheduleType() == null) {
            return null;
        }

        switch (schedule.getScheduleType().toUpperCase()) {
            case "HOURLY":
                return String.format("0 %d * * * ?", minute);
            case "DAILY":
                return String.format("0 %d %d * * ?", minute, hour);
            case "WEEKLY":
                return String.format("0 %d %d ? * %s", minute, hour, toQuartzDay(schedule.getDayOfWeek()));
            case "MONTHLY":
                return String.format("0 %d %d %d * ?", minute, hour, safeDayOfMonth(schedule.getDayOfMonth()));
            case "YEARLY":
                int month = schedule.getMonthOfYear() != null ? schedule.getMonthOfYear() : 1;
                return String.format("0 %d %d %d %d ?", minute, hour, safeDayOfMonth(schedule.getDayOfMonth()), month);
            default:
                return null;
        }
    }

    private String toQuartzDay(Integer dayOfWeek) {
        int day = dayOfWeek != null ? dayOfWeek : 1;
        switch (day) {
            case 1:
                return "MON";
            case 2:
                return "TUE";
            case 3:
                return "WED";
            case 4:
                return "THU";
            case 5:
                return "FRI";
            case 6:
                return "SAT";
            case 7:
                return "SUN";
            default:
                return "MON";
        }
    }

    private int safeDayOfMonth(Integer dayOfMonth) {
        if (dayOfMonth == null) {
            return 1;
        }
        return Math.max(1, Math.min(31, dayOfMonth));
    }
}
