package com.reportserver.service;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QuartzReportJob implements Job {

    @Autowired
    private ReportSchedulerService reportSchedulerService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        Long scheduleId = dataMap.getLong("scheduleId");
        if (scheduleId <= 0) {
            throw new JobExecutionException("Invalid scheduleId in Quartz trigger");
        }

        try {
            reportSchedulerService.executeScheduledReportNow(scheduleId);
        } catch (Exception e) {
            throw new JobExecutionException("Scheduled report execution failed for scheduleId=" + scheduleId, e);
        }
    }
}
