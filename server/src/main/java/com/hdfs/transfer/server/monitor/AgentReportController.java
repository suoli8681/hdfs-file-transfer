package com.hdfs.transfer.server.monitor;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.HeartbeatDTO;
import com.hdfs.transfer.common.dto.LogEntryDTO;
import com.hdfs.transfer.common.dto.VerifyResultDTO;
import com.hdfs.transfer.server.alert.AlertService;
import com.hdfs.transfer.server.entity.TaskInstanceEntity;
import com.hdfs.transfer.server.service.AgentService;
import com.hdfs.transfer.server.service.MigrationTaskService;
import com.hdfs.transfer.server.service.TaskInstanceService;
import com.hdfs.transfer.server.service.TaskLogService;
import com.hdfs.transfer.server.service.VerifyResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/report")
public class AgentReportController {

    private static final Logger log = LoggerFactory.getLogger(AgentReportController.class);

    private final AgentService agentService;
    private final TaskLogService taskLogService;
    private final VerifyResultService verifyResultService;
    private final TaskInstanceService instanceService;
    private final MigrationTaskService migrationTaskService;
    private final AlertService alertService;

    public AgentReportController(AgentService agentService, TaskLogService taskLogService,
                                 VerifyResultService verifyResultService,
                                 TaskInstanceService instanceService,
                                 MigrationTaskService migrationTaskService,
                                 AlertService alertService) {
        this.agentService = agentService;
        this.taskLogService = taskLogService;
        this.verifyResultService = verifyResultService;
        this.instanceService = instanceService;
        this.migrationTaskService = migrationTaskService;
        this.alertService = alertService;
    }

    @PostMapping("/heartbeat")
    public ApiResponse reportHeartbeat(@RequestBody HeartbeatDTO dto) {
        agentService.processHeartbeat(dto);
        return ApiResponse.success();
    }

    @PostMapping("/logs")
    public ApiResponse reportLogs(@RequestBody List<LogEntryDTO> logList) {
        taskLogService.batchAppend(logList);
        return ApiResponse.success();
    }

    @PostMapping("/verify")
    public ApiResponse reportVerify(@RequestBody VerifyResultDTO dto) {
        verifyResultService.saveResult(dto);
        if ("mismatch".equals(dto.getVerifyStatus())) {
            TaskInstanceEntity instance = instanceService.getById(dto.getTaskId());
            if (instance != null && ("killed".equals(instance.getStatus()) || "stopped".equals(instance.getStatus()))) {
                return ApiResponse.success();
            }
            String instanceName = instance != null ? instance.getInstanceName() : "instance-" + dto.getTaskId();
            String sourcePath = instance != null ? instance.getSourcePath() : "";
            String targetPath = instance != null ? instance.getTargetPath() : "";
            boolean taskAlertEnabled = false;
            if (instance != null && instance.getParentTaskId() != null) {
                com.hdfs.transfer.server.entity.MigrationTaskEntity template = migrationTaskService.getById(instance.getParentTaskId());
                taskAlertEnabled = template != null && Boolean.TRUE.equals(template.getAlertEnabled());
            }
            alertService.notifyVerifyMismatch(dto.getTaskId(), instanceName, sourcePath, targetPath, taskAlertEnabled);
        }
        return ApiResponse.success();
    }
}