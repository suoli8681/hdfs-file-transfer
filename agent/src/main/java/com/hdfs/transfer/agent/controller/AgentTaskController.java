package com.hdfs.transfer.agent.controller;

import com.hdfs.transfer.agent.executor.TaskExecutionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/task")
public class AgentTaskController {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskController.class);

    private final TaskExecutionManager executionManager;

    public AgentTaskController(TaskExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    @PostMapping("/{taskId}/stop")
    public Map<String, Object> stopTask(@PathVariable Long taskId) {
        log.info("Received stop request for task {}", taskId);
        boolean ok = executionManager.stopTask(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "Stop signal sent" : "Task process not found or already dead");
        return result;
    }

    @PostMapping("/{taskId}/kill")
    public Map<String, Object> forceKillTask(@PathVariable Long taskId) {
        log.info("Received force-kill request for task {}", taskId);
        boolean ok = executionManager.forceKillTask(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "Force-killed and cleanup initiated" : "Force-kill failed");
        return result;
    }
}
