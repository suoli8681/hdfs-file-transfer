package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.VerifyResultDTO;
import com.hdfs.transfer.server.service.VerifyResultService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    private final VerifyResultService verifyResultService;

    public VerifyController(VerifyResultService verifyResultService) {
        this.verifyResultService = verifyResultService;
    }

    @GetMapping("/page")
    public ApiResponse page(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "10") int pageSize,
                            @RequestParam(required = false) String taskName) {
        return ApiResponse.success(verifyResultService.page(pageNum, pageSize, taskName));
    }

    @GetMapping("/latest/{taskId}")
    public ApiResponse latest(@PathVariable Long taskId) {
        return ApiResponse.success(verifyResultService.getLatestByTaskId(taskId));
    }

    @PostMapping("/report")
    public ApiResponse report(@RequestBody VerifyResultDTO dto) {
        verifyResultService.saveResult(dto);
        return ApiResponse.success();
    }
}