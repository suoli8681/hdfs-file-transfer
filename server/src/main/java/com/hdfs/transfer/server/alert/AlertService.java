package com.hdfs.transfer.server.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    @Value("${alert.dingtalk.enabled:false}")
    private boolean dingtalkEnabled;
    @Value("${alert.dingtalk.webhook:}")
    private String dingtalkWebhook;
    @Value("${alert.wechat.enabled:false}")
    private boolean wechatEnabled;
    @Value("${alert.wechat.webhook:}")
    private String wechatWebhook;

    public void sendAlert(String title, String content) {
        log.warn("ALERT - {}: {}", title, content);
        if (dingtalkEnabled && !dingtalkWebhook.isEmpty()) {
            sendDingtalk(title, content);
        }
        if (wechatEnabled && !wechatWebhook.isEmpty()) {
            sendWechat(title, content);
        }
    }

    public void notifyTaskFailed(Long taskId, String taskName, String errorMsg) {
        String title = "迁移任务失败";
        String content = String.format("任务ID: %d\n任务名称: %s\n错误信息: %s", taskId, taskName, errorMsg);
        sendAlert(title, content);
    }

    public void notifyAgentOffline(String agentId, String agentHost) {
        String title = "Agent节点失联";
        String content = String.format("Agent ID: %s\n主机地址: %s", agentId, agentHost);
        sendAlert(title, content);
    }

    public void notifyVerifyMismatch(Long taskId, String taskName) {
        String title = "数据校验不一致";
        String content = String.format("任务ID: %d\n任务名称: %s\n源目标端文件不一致，请检查", taskId, taskName);
        sendAlert(title, content);
    }

    private void sendDingtalk(String title, String content) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            java.util.Map<String, Object> markdown = new java.util.HashMap<>();
            markdown.put("title", title);
            markdown.put("text", content);
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);
            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(
                    com.alibaba.fastjson.JSON.toJSONString(body),
                    new org.springframework.http.HttpHeaders());
            new org.springframework.web.client.RestTemplate().postForObject(dingtalkWebhook, request, String.class);
        } catch (Exception e) {
            log.error("Send dingtalk alert failed", e);
        }
    }

    private void sendWechat(String title, String content) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("msgtype", "text");
            java.util.Map<String, Object> text = new java.util.HashMap<>();
            text.put("content", title + "\n" + content);
            body.put("text", text);
            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(
                    com.alibaba.fastjson.JSON.toJSONString(body),
                    new org.springframework.http.HttpHeaders());
            new org.springframework.web.client.RestTemplate().postForObject(wechatWebhook, request, String.class);
        } catch (Exception e) {
            log.error("Send wechat alert failed", e);
        }
    }
}