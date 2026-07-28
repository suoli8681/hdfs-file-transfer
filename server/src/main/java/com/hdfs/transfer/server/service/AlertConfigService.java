package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hdfs.transfer.server.entity.AlertConfigEntity;
import com.hdfs.transfer.server.entity.AlertWebhookEntity;
import com.hdfs.transfer.server.mapper.AlertConfigMapper;
import com.hdfs.transfer.server.mapper.AlertWebhookMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertConfigService {

    private final AlertConfigMapper alertConfigMapper;
    private final AlertWebhookMapper alertWebhookMapper;

    public AlertConfigService(AlertConfigMapper alertConfigMapper, AlertWebhookMapper alertWebhookMapper) {
        this.alertConfigMapper = alertConfigMapper;
        this.alertWebhookMapper = alertWebhookMapper;
    }

    public List<AlertConfigEntity> listAll() {
        return alertConfigMapper.selectList(
                new LambdaQueryWrapper<AlertConfigEntity>()
                        .orderByAsc(AlertConfigEntity::getAlertType));
    }

    public AlertConfigEntity getById(Long id) {
        return alertConfigMapper.selectById(id);
    }

    public AlertConfigEntity getByAlertType(String alertType) {
        return alertConfigMapper.selectOne(
                new LambdaQueryWrapper<AlertConfigEntity>()
                        .eq(AlertConfigEntity::getAlertType, alertType));
    }

    @Transactional
    public void update(AlertConfigEntity entity) {
        alertConfigMapper.updateById(entity);
    }

    public List<AlertWebhookEntity> listWebhooks() {
        return alertWebhookMapper.selectList(
                new LambdaQueryWrapper<AlertWebhookEntity>()
                        .orderByAsc(AlertWebhookEntity::getWebhookType));
    }

    @Transactional
    public void updateWebhook(AlertWebhookEntity entity) {
        alertWebhookMapper.updateById(entity);
    }

    public AlertWebhookEntity getWebhook(String webhookType) {
        return alertWebhookMapper.selectOne(
                new LambdaQueryWrapper<AlertWebhookEntity>()
                        .eq(AlertWebhookEntity::getWebhookType, webhookType));
    }

    @Transactional
    public void initDefaultConfigs() {
        ensureConfig("task_failed", "任务失败告警");
        ensureConfig("agent_offline", "Agent离线告警");
        ensureConfig("agent_online", "Agent上线告警");
        ensureConfig("verify_mismatch", "校验不一致告警");

        ensureWebhook("wechat", "企业微信");
        ensureWebhook("dingtalk", "钉钉");
    }

    private void ensureConfig(String alertType, String remark) {
        long count = alertConfigMapper.selectCount(
                new LambdaQueryWrapper<AlertConfigEntity>()
                        .eq(AlertConfigEntity::getAlertType, alertType));
        if (count == 0) {
            AlertConfigEntity entity = new AlertConfigEntity();
            entity.setAlertType(alertType);
            entity.setEnabled(false);
            entity.setRemark(remark);
            alertConfigMapper.insert(entity);
        }
    }

    private void ensureWebhook(String webhookType, String remark) {
        long count = alertWebhookMapper.selectCount(
                new LambdaQueryWrapper<AlertWebhookEntity>()
                        .eq(AlertWebhookEntity::getWebhookType, webhookType));
        if (count == 0) {
            AlertWebhookEntity entity = new AlertWebhookEntity();
            entity.setWebhookType(webhookType);
            entity.setWebhook("");
            entity.setEnabled(false);
            alertWebhookMapper.insert(entity);
        }
    }

    public boolean testWebhook(AlertWebhookEntity entity) {
        if (entity.getWebhook() == null || entity.getWebhook().isEmpty()) {
            return false;
        }
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("msgtype", "text");
            java.util.Map<String, Object> text = new java.util.HashMap<>();
            text.put("content", "【告警测试】\n这是一条测试消息，收到说明webhook配置正确。");
            body.put("text", text);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(new org.springframework.http.MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8));
            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(
                    com.alibaba.fastjson.JSON.toJSONString(body), headers);
            new org.springframework.web.client.RestTemplate().postForObject(entity.getWebhook(), request, String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
