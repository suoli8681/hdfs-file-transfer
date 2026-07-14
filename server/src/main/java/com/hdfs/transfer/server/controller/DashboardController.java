package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.server.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse overview() {
        return ApiResponse.success(dashboardService.getOverview());
    }

    @GetMapping("/recent-tasks")
    public ApiResponse recentTasks(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(dashboardService.getRecentTasks(limit));
    }
}