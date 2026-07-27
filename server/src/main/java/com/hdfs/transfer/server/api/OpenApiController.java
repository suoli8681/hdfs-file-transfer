package com.hdfs.transfer.server.api;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.TaskDTO;
import com.hdfs.transfer.server.entity.ClusterConfigEntity;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.entity.VerifyResultEntity;
import com.hdfs.transfer.server.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/open-api")
public class OpenApiController {

    private final MigrationTaskService migrationTaskService;
    private final ClusterConfigService clusterConfigService;
    private final VerifyResultService verifyResultService;
    private final DashboardService dashboardService;

    public OpenApiController(MigrationTaskService migrationTaskService,
                             ClusterConfigService clusterConfigService,
                             VerifyResultService verifyResultService,
                             DashboardService dashboardService) {
        this.migrationTaskService = migrationTaskService;
        this.clusterConfigService = clusterConfigService;
        this.verifyResultService = verifyResultService;
        this.dashboardService = dashboardService;
    }

    @PostMapping("/tasks")
    public ApiResponse createTask(@RequestBody TaskDTO dto) {
        migrationTaskService.add(dto);
        return ApiResponse.success();
    }

    @GetMapping("/tasks")
    public ApiResponse listTasks(@RequestParam(required = false) String status) {
        return ApiResponse.success(migrationTaskService.page(1, 100, null, status, null, null, null));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse getTask(@PathVariable Long id) {
        return ApiResponse.success(migrationTaskService.getById(id));
    }

    @PostMapping("/tasks/{id}/online")
    public ApiResponse onlineTask(@PathVariable Long id) {
        try {
            boolean ok = migrationTaskService.online(id);
            return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/offline")
    public ApiResponse offlineTask(@PathVariable Long id) {
        try {
            boolean ok = migrationTaskService.offline(id);
            return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/execute")
    public ApiResponse executeTask(@PathVariable Long id) {
        try {
            boolean ok = migrationTaskService.execute(id);
            return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/tasks/{id}/verify")
    public ApiResponse getVerifyResult(@PathVariable Long id) {
        return ApiResponse.success(verifyResultService.getLatestByTaskId(id));
    }

    @GetMapping("/clusters")
    public ApiResponse listClusters() {
        return ApiResponse.success(clusterConfigService.listAll());
    }

    @GetMapping("/dashboard")
    public ApiResponse dashboard() {
        return ApiResponse.success(dashboardService.getOverview());
    }
}