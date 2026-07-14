package com.hdfs.transfer.server.scheduler;

import com.hdfs.transfer.server.service.TaskLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupJob.class);

    private final TaskLogService taskLogService;

    @Value("${hdfs.transfer.task-log-retention-days:30}")
    private int retentionDays;

    public LogCleanupJob(TaskLogService taskLogService) {
        this.taskLogService = taskLogService;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredLogs() {
        log.info("Cleaning up task logs older than {} days", retentionDays);
        taskLogService.cleanExpiredLogs(retentionDays);
    }
}