package com.hdfs.transfer.agent.communication;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.common.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
public class ServerCommunicator {

    private static final Logger log = LoggerFactory.getLogger(ServerCommunicator.class);

    private final RestTemplate restTemplate;
    private final AgentConfig agentConfig;

    public ServerCommunicator(AgentConfig agentConfig) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
        this.agentConfig = agentConfig;
    }

    public void sendHeartbeat(HeartbeatDTO heartbeat) {
        try {
            String url = agentConfig.getServerBaseUrl() + "/api/report/heartbeat";
            HttpEntity<HeartbeatDTO> request = new HttpEntity<>(heartbeat, createJsonHeaders());
            ResponseEntity<ApiResponse> response = restTemplate.postForEntity(url, request, ApiResponse.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Heartbeat send failed with status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send heartbeat", e);
        }
    }

    public List<TaskProgressDTO> fetchPendingTasks() {
        try {
            String url = agentConfig.getServerBaseUrl() + "/api/tasks/pending?agentId=" + agentConfig.getAgentId();
            ResponseEntity<ApiResponse> response = restTemplate.getForEntity(url, ApiResponse.class);
            if (response.getBody() != null && response.getBody().getData() != null) {
                String dataJson = JSON.toJSONString(response.getBody().getData());
                return JSON.parseArray(dataJson, TaskProgressDTO.class);
            }
        } catch (Exception e) {
            log.error("Failed to fetch pending tasks", e);
        }
        return Collections.emptyList();
    }

    public void uploadLogs(List<LogEntryDTO> logs) {
        if (logs.isEmpty()) return;
        try {
            String url = agentConfig.getServerBaseUrl() + "/api/report/logs";
            HttpEntity<List<LogEntryDTO>> request = new HttpEntity<>(logs, createJsonHeaders());
            restTemplate.postForEntity(url, request, ApiResponse.class);
        } catch (Exception e) {
            log.error("Failed to upload logs", e);
        }
    }

    public void uploadVerifyResult(VerifyResultDTO result) {
        try {
            String url = agentConfig.getServerBaseUrl() + "/api/report/verify";
            HttpEntity<VerifyResultDTO> request = new HttpEntity<>(result, createJsonHeaders());
            restTemplate.postForEntity(url, request, ApiResponse.class);
        } catch (Exception e) {
            log.error("Failed to upload verify result", e);
        }
    }

    public void reportTaskStatus(Long taskId, String status, long completedFiles, long completedSize,
                                 long totalFiles, long totalSize, String errorMsg) {
        try {
            String url = agentConfig.getServerBaseUrl() + "/api/tasks/" + taskId + "/status";
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("status", status);
            body.put("completedFiles", completedFiles);
            body.put("completedSize", completedSize);
            body.put("totalFiles", totalFiles);
            body.put("totalSize", totalSize);
            body.put("errorMsg", errorMsg);
            HttpEntity<java.util.Map<String, Object>> request = new HttpEntity<>(body, createJsonHeaders());
            restTemplate.postForEntity(url, request, ApiResponse.class);
        } catch (Exception e) {
            log.error("Failed to report task status", e);
        }
    }

    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}