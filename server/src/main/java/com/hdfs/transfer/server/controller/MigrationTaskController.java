package com.hdfs.transfer.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.TaskDTO;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.service.MigrationTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class MigrationTaskController {

    private final MigrationTaskService migrationTaskService;

    public MigrationTaskController(MigrationTaskService migrationTaskService) {
        this.migrationTaskService = migrationTaskService;
    }

    @GetMapping("/page")
    public ApiResponse page(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "10") int pageSize,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String agentId,
                            @RequestParam(required = false) String startTime,
                            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(migrationTaskService.page(pageNum, pageSize, keyword, status, agentId, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ApiResponse getById(@PathVariable Long id) {
        return ApiResponse.success(migrationTaskService.getById(id));
    }

    @PostMapping
    public ApiResponse create(@RequestBody TaskDTO dto) {
        try {
            migrationTaskService.add(dto);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping
    public ApiResponse update(@RequestBody TaskDTO dto) {
        migrationTaskService.update(dto);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/start")
    public ApiResponse start(@PathVariable Long id) {
        try {
            boolean ok = migrationTaskService.start(id);
            return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/stop")
    public ApiResponse stop(@PathVariable Long id) {
        boolean ok = migrationTaskService.stop(id);
        return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
    }

    @PostMapping("/{id}/force-kill")
    public ApiResponse forceKill(@PathVariable Long id) {
        boolean ok = migrationTaskService.forceKill(id);
        return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
    }

    @GetMapping("/dispatch")
    public ApiResponse dispatchTasks(@RequestParam String agentId) {
        List<MigrationTaskEntity> tasks = migrationTaskService.listDispatched(agentId);
        List<TaskDTO> result = new ArrayList<>();
        for (MigrationTaskEntity task : tasks) {
            boolean updated = migrationTaskService.updateStatusIfMatch(task.getId(), "running", "dispatching");
            if (!updated) {
                continue;
            }
            TaskDTO dto = new TaskDTO();
            dto.setTaskId(task.getId());
            dto.setTaskName(task.getTaskName());
            dto.setTaskType(task.getTaskType());
            dto.setSourceCluster(String.valueOf(task.getSourceClusterId()));
            dto.setSourcePath(task.getSourcePath());
            dto.setTargetCluster(String.valueOf(task.getTargetClusterId()));
            dto.setTargetPath(task.getTargetPath());
            dto.setDistcpOptions(task.getDistcpOptions());
            dto.setCronExpr(task.getCronExpr());
            dto.setAgentId(task.getAgentId());
            result.add(dto);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/status")
    public ApiResponse updateStatus(@PathVariable Long id,
                                    @RequestBody java.util.Map<String, Object> body) {
        String status = (String) body.get("status");
        long completedFiles = body.get("completedFiles") != null ?
                Long.parseLong(body.get("completedFiles").toString()) : 0;
        long completedSize = body.get("completedSize") != null ?
                Long.parseLong(body.get("completedSize").toString()) : 0;
        long totalFiles = body.get("totalFiles") != null ?
                Long.parseLong(body.get("totalFiles").toString()) : 0;
        long totalSize = body.get("totalSize") != null ?
                Long.parseLong(body.get("totalSize").toString()) : 0;
        String errorMsg = (String) body.get("errorMsg");
        migrationTaskService.updateProgress(id, completedFiles, completedSize, totalFiles, totalSize, status, errorMsg);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        try {
            migrationTaskService.delete(id);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}