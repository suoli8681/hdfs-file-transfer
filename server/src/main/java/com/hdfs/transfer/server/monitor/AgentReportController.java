package com.hdfs.transfer.server.monitor;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.HeartbeatDTO;
import com.hdfs.transfer.common.dto.LogEntryDTO;
import com.hdfs.transfer.common.dto.VerifyResultDTO;
import com.hdfs.transfer.server.alert.AlertService;
import com.hdfs.transfer.server.service.AgentService;
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
    private final AlertService alertService;

    public AgentReportController(AgentService agentService, TaskLogService taskLogService,
                                 VerifyResultService verifyResultService, AlertService alertService) {
        this.agentService = agentService;
        this.taskLogService = taskLogService;
        this.verifyResultService = verifyResultService;
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
            alertService.notifyVerifyMismatch(dto.getTaskId(), "verify-" + dto.getTaskId());
        }
        return ApiResponse.success();
    }
}