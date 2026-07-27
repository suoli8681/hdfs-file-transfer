package com.hdfs.transfer.server.scheduler;

import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.service.MigrationTaskService;
import com.hdfs.transfer.server.service.TaskInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class CronTaskManager {

    private static final Logger log = LoggerFactory.getLogger(CronTaskManager.class);

    private final TaskScheduler taskScheduler;
    private final MigrationTaskService taskService;
    private final TaskInstanceService instanceService;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public CronTaskManager(TaskScheduler taskScheduler,
                           @Lazy MigrationTaskService taskService,
                           @Lazy TaskInstanceService instanceService) {
        this.taskScheduler = taskScheduler;
        this.taskService = taskService;
        this.instanceService = instanceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreOnStartup() {
        try {
            java.util.List<MigrationTaskEntity> runningTemplates = taskService.listScheduledOnline();
            for (MigrationTaskEntity task : runningTemplates) {
                try {
                    register(task.getId(), task.getCronExpr());
                    log.info("Restored scheduled task: {} (cron={})", task.getTaskName(), task.getCronExpr());
                } catch (Exception e) {
                    log.error("Failed to restore scheduled task {}: {}", task.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to restore scheduled tasks on startup: {}", e.getMessage());
        }
    }

    public void register(Long templateId, String cronExpr) {
        unregister(templateId);
        if (cronExpr == null || cronExpr.isEmpty()) {
            log.warn("Cannot register task {}: cronExpr is empty", templateId);
            return;
        }
        try {
            CronTrigger trigger = new CronTrigger(cronExpr);
            ScheduledFuture<?> future = taskScheduler.schedule(() -> triggerInstanceCreation(templateId), trigger);
            scheduledTasks.put(templateId, future);
            log.info("Registered scheduled task: templateId={}, cron={}", templateId, cronExpr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid cron expression '{}' for task {}: {}", cronExpr, templateId, e.getMessage());
            throw new RuntimeException("无效的Cron表达式: " + cronExpr);
        }
    }

    public void unregister(Long templateId) {
        ScheduledFuture<?> future = scheduledTasks.remove(templateId);
        if (future != null) {
            future.cancel(false);
            log.info("Unregistered scheduled task: templateId={}", templateId);
        }
    }

    private void triggerInstanceCreation(Long templateId) {
        try {
            instanceService.createInstanceFromTemplate(templateId);
            log.info("Triggered instance creation for template: {}", templateId);
        } catch (Exception e) {
            log.error("Failed to create instance for template {}: {}", templateId, e.getMessage());
        }
    }
}
