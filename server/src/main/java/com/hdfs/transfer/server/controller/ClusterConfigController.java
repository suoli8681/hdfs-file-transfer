package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.server.entity.ClusterConfigEntity;
import com.hdfs.transfer.server.service.ClusterConfigService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/clusters")
public class ClusterConfigController {

    private final ClusterConfigService clusterConfigService;

    public ClusterConfigController(ClusterConfigService clusterConfigService) {
        this.clusterConfigService = clusterConfigService;
    }

    @GetMapping("/page")
    public ApiResponse page(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "10") int pageSize,
                            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(clusterConfigService.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/list")
    public ApiResponse list() {
        return ApiResponse.success(clusterConfigService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse getById(@PathVariable Long id) {
        return ApiResponse.success(clusterConfigService.getById(id));
    }

    @PostMapping
    public ApiResponse add(@RequestBody ClusterConfigEntity entity) {
        clusterConfigService.add(entity);
        return ApiResponse.success();
    }

    @PutMapping
    public ApiResponse update(@RequestBody ClusterConfigEntity entity) {
        clusterConfigService.update(entity);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        clusterConfigService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/test-connect/{id}")
    public ApiResponse testConnect(@PathVariable Long id) {
        String error = clusterConfigService.testConnect(id);
        return error == null ? ApiResponse.success() : ApiResponse.error(500, error);
    }
}