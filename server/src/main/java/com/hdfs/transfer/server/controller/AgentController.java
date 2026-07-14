package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.HeartbeatDTO;
import com.hdfs.transfer.server.entity.AgentNodeEntity;
import com.hdfs.transfer.server.service.AgentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/list")
    public ApiResponse list() {
        return ApiResponse.success(agentService.listAll());
    }

    @PostMapping("/register")
    public ApiResponse register(@RequestBody AgentNodeEntity entity) {
        agentService.register(entity);
        return ApiResponse.success();
    }

    @PostMapping("/heartbeat")
    public ApiResponse heartbeat(@RequestBody HeartbeatDTO dto) {
        agentService.processHeartbeat(dto);
        return ApiResponse.success();
    }
}