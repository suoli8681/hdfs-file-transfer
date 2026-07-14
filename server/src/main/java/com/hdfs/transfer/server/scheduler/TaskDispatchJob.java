package com.hdfs.transfer.server.scheduler;

import com.hdfs.transfer.server.service.TaskDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchJob.class);

    private final TaskDispatchService taskDispatchService;

    public TaskDispatchJob(TaskDispatchService taskDispatchService) {
        this.taskDispatchService = taskDispatchService;
    }

    @Scheduled(fixedDelay = 10000)
    public void dispatchPending() {
        try {
            taskDispatchService.dispatchPendingTasks();
        } catch (Exception e) {
            log.error("Task dispatch failed", e);
        }
    }
}