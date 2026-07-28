package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.server.entity.AlertConfigEntity;
import com.hdfs.transfer.server.entity.AlertWebhookEntity;
import com.hdfs.transfer.server.service.AlertConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alert-config")
public class AlertConfigController {

    private final AlertConfigService alertConfigService;

    public AlertConfigController(AlertConfigService alertConfigService) {
        this.alertConfigService = alertConfigService;
    }

    @GetMapping
    public ApiResponse list() {
        Map<String, Object> result = new HashMap<>();
        result.put("configs", alertConfigService.listAll());
        result.put("webhooks", alertConfigService.listWebhooks());
        return ApiResponse.success(result);
    }

    @PutMapping("/config")
    public ApiResponse updateConfig(@RequestBody AlertConfigEntity entity) {
        alertConfigService.update(entity);
        return ApiResponse.success();
    }

    @PutMapping("/webhook")
    public ApiResponse updateWebhook(@RequestBody AlertWebhookEntity entity) {
        alertConfigService.updateWebhook(entity);
        return ApiResponse.success();
    }

    @PostMapping("/test")
    public ApiResponse test(@RequestBody AlertWebhookEntity entity) {
        boolean ok = alertConfigService.testWebhook(entity);
        return ok ? ApiResponse.success() : ApiResponse.error(400, "测试发送失败，请检查webhook地址");
    }
}
