package com.hdfs.transfer.server.alert;

import com.hdfs.transfer.server.entity.AlertConfigEntity;
import com.hdfs.transfer.server.entity.AlertWebhookEntity;
import com.hdfs.transfer.server.service.AlertConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertConfigService alertConfigService;

    public AlertService(AlertConfigService alertConfigService) {
        this.alertConfigService = alertConfigService;
    }

    public void sendAlert(String alertType, boolean taskAlertEnabled, String event, String content) {
        sendAlert(alertType, taskAlertEnabled, event, content, null);
    }

    public void sendAlert(String alertType, boolean taskAlertEnabled, String event, String content, String agentHost) {
        if (!taskAlertEnabled) {
            return;
        }
        AlertConfigEntity config = alertConfigService.getByAlertType(alertType);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        StringBuilder fullContent = new StringBuilder();
        fullContent.append("告警事件: ").append(event).append("\n");
        fullContent.append("告警时间: ").append(LocalDateTime.now().format(DTF)).append("\n");
        fullContent.append("告警主机: ").append(agentHost != null ? agentHost : "—").append("\n");
        fullContent.append("告警信息: ").append(content);

        log.warn("ALERT [{}] - {}: {}", alertType, event, content);
        AlertWebhookEntity wechat = alertConfigService.getWebhook("wechat");
        if (wechat != null && Boolean.TRUE.equals(wechat.getEnabled()) && wechat.getWebhook() != null && !wechat.getWebhook().isEmpty()) {
            sendWechat(wechat.getWebhook(), event, fullContent.toString());
        }
        AlertWebhookEntity dingtalk = alertConfigService.getWebhook("dingtalk");
        if (dingtalk != null && Boolean.TRUE.equals(dingtalk.getEnabled()) && dingtalk.getWebhook() != null && !dingtalk.getWebhook().isEmpty()) {
            sendDingtalk(dingtalk.getWebhook(), event, fullContent.toString());
        }
    }

    public void notifyTaskFailed(Long instanceId, String instanceName, String errorMsg, boolean taskAlertEnabled) {
        sendAlert("task_failed", taskAlertEnabled, "迁移任务实例【" + instanceName + "】失败",
                errorMsg != null ? errorMsg : "");
    }

    public void notifyAgentOffline(String agentId, String agentHost) {
        sendAlert("agent_offline", true, "Agent实例【" + agentId + "】离线",
                "该Agent已超过60秒未上报心跳", agentHost);
    }

    public void notifyAgentOnline(String agentId, String agentHost) {
        sendAlert("agent_online", true, "Agent实例【" + agentId + "】上线",
                "该Agent已恢复在线", agentHost);
    }

    public void notifyVerifyMismatch(Long instanceId, String instanceName, String sourcePath, String targetPath, boolean taskAlertEnabled) {
        sendAlert("verify_mismatch", taskAlertEnabled, "迁移任务实例【" + instanceName + "】校验不一致",
                String.format("源路径: %s\n目标路径: %s\n源目标端文件不一致，请检查", sourcePath, targetPath));
    }

    private void sendWechat(String webhook, String title, String content) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("msgtype", "text");
            java.util.Map<String, Object> text = new java.util.HashMap<>();
            text.put("content", content);
            body.put("text", text);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(new org.springframework.http.MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8));
            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(
                    com.alibaba.fastjson.JSON.toJSONString(body), headers);
            new org.springframework.web.client.RestTemplate().postForObject(webhook, request, String.class);
        } catch (Exception e) {
            log.error("Send wechat alert failed", e);
        }
    }

    private void sendDingtalk(String webhook, String title, String content) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            java.util.Map<String, Object> markdown = new java.util.HashMap<>();
            markdown.put("title", title);
            markdown.put("text", content);
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(new org.springframework.http.MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8));
            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(
                    com.alibaba.fastjson.JSON.toJSONString(body), headers);
            new org.springframework.web.client.RestTemplate().postForObject(webhook, request, String.class);
        } catch (Exception e) {
            log.error("Send dingtalk alert failed", e);
        }
    }
}
