package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.server.service.TaskOperationLogService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/task-logs")
public class TaskOperationLogController {

    private final TaskOperationLogService operationLogService;

    public TaskOperationLogController(TaskOperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping("/page")
    public ApiResponse page(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "10") int pageSize,
                            @RequestParam(required = false) Long taskId) {
        return ApiResponse.success(operationLogService.page(pageNum, pageSize, taskId));
    }

    @GetMapping("/list/{taskId}")
    public ApiResponse listByTaskId(@PathVariable Long taskId) {
        return ApiResponse.success(operationLogService.listByTaskId(taskId));
    }
}