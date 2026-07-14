package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.LogEntryDTO;
import com.hdfs.transfer.server.service.TaskLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final TaskLogService taskLogService;

    public LogController(TaskLogService taskLogService) {
        this.taskLogService = taskLogService;
    }

    @GetMapping("/page")
    public ApiResponse page(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "50") int pageSize,
                            @RequestParam(required = false) Long taskId) {
        return ApiResponse.success(taskLogService.page(pageNum, pageSize, taskId));
    }

    @PostMapping("/upload")
    public ApiResponse upload(@RequestBody List<LogEntryDTO> logList) {
        taskLogService.batchAppend(logList);
        return ApiResponse.success();
    }
}