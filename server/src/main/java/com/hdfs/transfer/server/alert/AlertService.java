package com.hdfs.transfer.server.alert;

import com.hdfs.transfer.server.entity.AlertConfigEntity;
import com.hdfs.transfer.server.entity.AlertWebhookEntity;
import com.hdfs.transfer.server.service.AlertConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertConfigService alertConfigService;

    public AlertService(AlertConfigService alertConfigService) {
        this.alertConfigService = alertConfigService;
    }

    public void sendAlert(String alertType, boolean taskAlertEnabled, String title, String content) {
        if (!taskAlertEnabled) {
            return;
        }
        AlertConfigEntity config = alertConfigService.getByAlertType(alertType);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        log.warn("ALERT [{}] - {}: {}", alertType, title, content);
        AlertWebhookEntity wechat = alertConfigService.getWebhook("wechat");
        if (wechat != null && Boolean.TRUE.equals(wechat.getEnabled()) && wechat.getWebhook() != null && !wechat.getWebhook().isEmpty()) {
            sendWechat(wechat.getWebhook(), title, content);
        }
        AlertWebhookEntity dingtalk = alertConfigService.getWebhook("dingtalk");
        if (dingtalk != null && Boolean.TRUE.equals(dingtalk.getEnabled()) && dingtalk.getWebhook() != null && !dingtalk.getWebhook().isEmpty()) {
            sendDingtalk(dingtalk.getWebhook(), title, content);
        }
    }

    public void notifyTaskFailed(Long instanceId, String instanceName, String errorMsg, boolean taskAlertEnabled) {
        sendAlert("task_failed", taskAlertEnabled, "迁移任务失败",
                String.format("实例ID: %d\n实例名称: %s\n错误信息: %s", instanceId, instanceName, errorMsg));
    }

    public void notifyAgentOffline(String agentId, String agentHost) {
        sendAlert("agent_offline", true, "Agent节点离线",
                String.format("Agent ID: %s\n主机地址: %s\n该Agent已超过60秒未上报心跳", agentId, agentHost));
    }

    public void notifyAgentOnline(String agentId, String agentHost) {
        sendAlert("agent_online", true, "Agent节点上线",
                String.format("Agent ID: %s\n主机地址: %s\n该Agent已恢复在线", agentId, agentHost));
    }

    public void notifyVerifyMismatch(Long instanceId, String instanceName, String sourcePath, String targetPath, boolean taskAlertEnabled) {
        sendAlert("verify_mismatch", taskAlertEnabled, "数据校验不一致",
                String.format("实例ID: %d\n实例名称: %s\n源路径: %s\n目标路径: %s\n源目标端文件不一致，请检查",
                        instanceId, instanceName, sourcePath, targetPath));
    }

    private void sendWechat(String webhook, String title, String content) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("msgtype", "text");
            java.util.Map<String, Object> text = new java.util.HashMap<>();
            text.put("content", "【" + title + "】\n" + content);
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
            markdown.put("text", "**" + title + "**\n\n" + content);
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
