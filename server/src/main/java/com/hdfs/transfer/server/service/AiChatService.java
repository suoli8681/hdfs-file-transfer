package com.hdfs.transfer.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hdfs.transfer.server.entity.*;
import com.hdfs.transfer.server.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiConfigMapper configMapper;
    private final MigrationTaskMapper taskMapper;
    private final TaskInstanceMapper instanceMapper;
    private final AgentNodeMapper agentNodeMapper;
    private final ClusterConfigMapper clusterConfigMapper;

    public AiChatService(AiConversationMapper conversationMapper, AiMessageMapper messageMapper,
                         AiConfigMapper configMapper, MigrationTaskMapper taskMapper,
                         TaskInstanceMapper instanceMapper,
                         AgentNodeMapper agentNodeMapper, ClusterConfigMapper clusterConfigMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.configMapper = configMapper;
        this.taskMapper = taskMapper;
        this.instanceMapper = instanceMapper;
        this.agentNodeMapper = agentNodeMapper;
        this.clusterConfigMapper = clusterConfigMapper;
    }

    public Long createConversation(String username, Long configId) {
        AiConversationEntity conv = new AiConversationEntity();
        conv.setTitle("New Conversation");
        conv.setUsername(username);
        conv.setConfigId(configId);
        conv.setCreateTime(LocalDateTime.now());
        conv.setUpdateTime(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv.getId();
    }

    public List<AiConversationEntity> listConversations(String username) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<AiConversationEntity>()
                        .eq(AiConversationEntity::getUsername, username)
                        .orderByDesc(AiConversationEntity::getUpdateTime));
    }

    public List<AiMessageEntity> getMessages(Long conversationId, String username) {
        AiConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUsername().equals(username)) {
            return Collections.emptyList();
        }
        return messageMapper.selectList(
                new LambdaQueryWrapper<AiMessageEntity>()
                        .eq(AiMessageEntity::getConversationId, conversationId)
                        .orderByAsc(AiMessageEntity::getId));
    }

    public void deleteConversation(Long conversationId, String username) {
        AiConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUsername().equals(username)) return;
        messageMapper.delete(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    public SseEmitter chat(Long conversationId, String userMessage, String username, Long configId) {
        SseEmitter emitter = new SseEmitter(300000L);

        AiConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUsername().equals(username)) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Conversation not found"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        Long effectiveConfigId = configId != null ? configId : conv.getConfigId();
        AiConfigEntity config = getActiveConfig(effectiveConfigId);
        if (config == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("No AI config available, please configure one first"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        saveMessage(conversationId, "user", userMessage);

        if (configId != null && !configId.equals(conv.getConfigId())) {
            conv.setConfigId(configId);
        }
        if ("New Conversation".equals(conv.getTitle())) {
            String title = userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
            conv.setTitle(title);
        }
        conv.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conv);

        final AiConfigEntity finalConfig = config;
        final Long finalConvId = conversationId;
        Thread thread = new Thread(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                List<AiMessageEntity> history = messageMapper.selectList(
                        new LambdaQueryWrapper<AiMessageEntity>()
                                .eq(AiMessageEntity::getConversationId, finalConvId)
                                .orderByAsc(AiMessageEntity::getId));

                JSONArray messages = new JSONArray();
                messages.add(new JSONObject().fluentPut("role", "system").fluentPut("content",
                        "You are an AI assistant for an HDFS file migration platform. " +
                        "You can analyze migration tasks, query task statistics, and provide insights. " +
                        "When users ask about migration status, task statistics, or cluster info, " +
                        "use the provided function to query real-time data. " +
                        "Always respond in Chinese."));

                for (AiMessageEntity msg : history) {
                    messages.add(new JSONObject().fluentPut("role", msg.getRole()).fluentPut("content", msg.getContent()));
                }

                callOpenAIWithFunctionCalling(finalConfig, messages, emitter, fullResponse, 0);

                saveMessage(finalConvId, "assistant", fullResponse.toString());

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.error("AI chat error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage() != null ? e.getMessage() : "Unknown error"));
                    if (fullResponse.length() > 0) {
                        saveMessage(finalConvId, "assistant", fullResponse.toString());
                    }
                } catch (Exception ignored) {}
                emitter.complete();
            }
        }, "ai-chat-" + conversationId);
        thread.setDaemon(true);
        thread.start();

        return emitter;
    }

    private void callOpenAIWithFunctionCalling(AiConfigEntity config, JSONArray messages,
                                                SseEmitter emitter, StringBuilder fullResponse, int depth) throws Exception {
        if (depth > 5) {
            fullResponse.append("Too many function calls, stopping.");
            emitter.send(SseEmitter.event().name("content").data("Too many function calls, stopping."));
            return;
        }

        JSONObject body = new JSONObject();
        body.put("model", config.getModelName());
        body.put("messages", messages);
        body.put("temperature", config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7);
        body.put("max_tokens", config.getMaxTokens() != null ? config.getMaxTokens() : 2000);
        body.put("stream", true);

        JSONObject function = new JSONObject();
        function.put("type", "function");
        JSONObject functionDef = new JSONObject();
        functionDef.put("name", "query_migration_data");
        functionDef.put("description", "Query migration task data, agent status, cluster info, and task statistics. Call this when user asks about migration tasks, progress, status, or statistics.");
        JSONObject params = new JSONObject();
        params.put("type", "object");
        JSONObject properties = new JSONObject();
        JSONObject queryType = new JSONObject();
        queryType.put("type", "string");
        queryType.put("description", "Type of query: 'task_stats', 'task_list', 'agent_status', 'cluster_list', 'task_detail'");
        queryType.put("enum", Arrays.asList("task_stats", "task_list", "agent_status", "cluster_list", "task_detail"));
        properties.put("query_type", queryType);
        JSONObject sourcePath = new JSONObject();
        sourcePath.put("type", "string");
        sourcePath.put("description", "Source HDFS path to filter tasks by, e.g. /public-data/ods_ai_model_request_api_log");
        properties.put("source_path", sourcePath);
        JSONObject status = new JSONObject();
        status.put("type", "string");
        status.put("description", "Task status filter: draft, pending, dispatching, running, success, failed, stopped, killed, retrying");
        properties.put("status", status);
        params.put("properties", properties);
        params.put("required", Collections.singletonList("query_type"));
        functionDef.put("parameters", params);
        function.put("function", functionDef);
        body.put("tools", Collections.singletonList(function));

        String url = config.getBaseUrl().replaceAll("/$", "") + "/chat/completions";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        byte[] bodyBytes = body.toJSONString().getBytes(StandardCharsets.UTF_8);
        log.info("AI chat request body (depth={}): {}", depth, body.toJSONString().substring(0, Math.min(body.toJSONString().length(), 500)));
        conn.getOutputStream().write(bodyBytes);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errorResponse = readAll(conn.getErrorStream());
            throw new RuntimeException("AI API returned " + responseCode + ": " + errorResponse);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        Map<Integer, StringBuilder> toolCallArgsMap = new LinkedHashMap<>();
        Map<Integer, String> toolCallIds = new LinkedHashMap<>();
        Map<Integer, String> toolCallNames = new LinkedHashMap<>();
        String line;

        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data: ")) continue;
            String data = line.substring(6).trim();
            if ("[DONE]".equals(data)) break;

            JSONObject chunk = JSON.parseObject(data);
            JSONArray choices = chunk.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) continue;

            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            String finishReason = choice.getString("finish_reason");

            if (delta != null && delta.containsKey("tool_calls")) {
                JSONArray toolCalls = delta.getJSONArray("tool_calls");
                if (toolCalls != null) {
                    for (int i = 0; i < toolCalls.size(); i++) {
                        JSONObject tc = toolCalls.getJSONObject(i);
                        int idx = tc.containsKey("index") ? tc.getIntValue("index") : 0;
                        if (tc.containsKey("id")) {
                            toolCallIds.put(idx, tc.getString("id"));
                        }
                        if (tc.containsKey("function")) {
                            JSONObject func = tc.getJSONObject("function");
                            if (func.containsKey("name")) {
                                toolCallNames.put(idx, func.getString("name"));
                            }
                            if (func.containsKey("arguments")) {
                                String argsPart = func.getString("arguments");
                                if (argsPart != null) {
                                    toolCallArgsMap.computeIfAbsent(idx, k -> new StringBuilder()).append(argsPart);
                                }
                            }
                        }
                    }
                }
            }

            if (delta != null && delta.containsKey("content") && delta.getString("content") != null) {
                String content = delta.getString("content");
                fullResponse.append(content);
                emitter.send(SseEmitter.event().name("content").data(content));
            }

            if (finishReason != null) {
                log.info("AI chat finish_reason={}, toolCallIds={}, toolCallNames={}, toolCallArgsMap={}",
                        finishReason, toolCallIds, toolCallNames, toolCallArgsMap);
            }
        }
        reader.close();

        if (!toolCallArgsMap.isEmpty()) {
            for (Map.Entry<Integer, StringBuilder> entry : toolCallArgsMap.entrySet()) {
                int idx = entry.getKey();
                String tcId = toolCallIds.get(idx);
                String tcName = toolCallNames.get(idx);
                StringBuilder tcArgs = entry.getValue();

                if (tcId == null) {
                    tcId = "call_" + System.currentTimeMillis() + "_" + idx;
                }
                if (tcName == null) {
                    tcName = "query_migration_data";
                }

                emitter.send(SseEmitter.event().name("tool").data("Querying: " + tcName));

                JSONObject toolArgs = tcArgs.length() > 0
                        ? JSON.parseObject(tcArgs.toString())
                        : new JSONObject();
                String queryResult = executeFunctionCall(tcName, toolArgs);

                JSONObject assistantMsg = new JSONObject();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", "");
                JSONArray toolCallsArr = new JSONArray();
                JSONObject toolCallObj = new JSONObject();
                toolCallObj.put("id", tcId);
                toolCallObj.put("type", "function");
                JSONObject funcObj = new JSONObject();
                funcObj.put("name", tcName);
                funcObj.put("arguments", tcArgs.length() > 0 ? tcArgs.toString() : "{}");
                toolCallObj.put("function", funcObj);
                toolCallsArr.add(toolCallObj);
                assistantMsg.put("tool_calls", toolCallsArr);
                messages.add(assistantMsg);

                JSONObject toolMessage = new JSONObject();
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", tcId);
                toolMessage.put("content", queryResult);
                messages.add(toolMessage);
            }

            callOpenAIWithFunctionCalling(config, messages, emitter, fullResponse, depth + 1);
        }
    }

    private String executeFunctionCall(String functionName, JSONObject args) {
        try {
            String queryType = args.getString("query_type");
            String sourcePath = args.getString("source_path");
            String statusFilter = args.getString("status");
            String taskName = args.getString("task_name");

            if ("agent_status".equals(queryType)) {
                List<AgentNodeEntity> agents = agentNodeMapper.selectList(null);
                JSONArray arr = new JSONArray();
                for (AgentNodeEntity a : agents) {
                    JSONObject o = new JSONObject();
                    o.put("agentId", a.getAgentId());
                    o.put("host", a.getAgentHost());
                    o.put("status", a.getStatus());
                    o.put("runningTasks", a.getRunningTaskCount());
                    o.put("maxParallelTasks", a.getMaxParallelTasks());
                    o.put("cpuUsage", a.getCpuUsage());
                    o.put("memoryUsage", a.getMemoryUsage());
                    arr.add(o);
                }
                return arr.toJSONString();
            } else if ("cluster_list".equals(queryType)) {
                List<ClusterConfigEntity> clusters = clusterConfigMapper.selectList(null);
                JSONArray arr = new JSONArray();
                for (ClusterConfigEntity c : clusters) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getId());
                    o.put("name", c.getClusterName());
                    o.put("namenode", c.getNameNodeRpc());
                    arr.add(o);
                }
                return arr.toJSONString();
            }

            // task_stats / task_list / task_detail 查询 task_instance 表
            LambdaQueryWrapper<TaskInstanceEntity> wrapper = new LambdaQueryWrapper<>();
            if (sourcePath != null && !sourcePath.isEmpty()) {
                wrapper.like(TaskInstanceEntity::getSourcePath, sourcePath);
            }
            if (statusFilter != null && !statusFilter.isEmpty()) {
                wrapper.eq(TaskInstanceEntity::getStatus, statusFilter);
            }
            if (taskName != null && !taskName.isEmpty()) {
                wrapper.like(TaskInstanceEntity::getInstanceName, taskName);
            }
            wrapper.orderByDesc(TaskInstanceEntity::getCreateTime);

            List<TaskInstanceEntity> instances = instanceMapper.selectList(wrapper);

            if ("task_stats".equals(queryType)) {
                JSONObject stats = new JSONObject();
                stats.put("total", instances.size());
                Map<String, Long> statusCount = new LinkedHashMap<>();
                long totalSize = 0;
                long completedSize = 0;
                for (TaskInstanceEntity t : instances) {
                    String s = t.getStatus();
                    statusCount.put(s, statusCount.getOrDefault(s, 0L) + 1);
                    if (t.getTotalSize() != null) totalSize += t.getTotalSize();
                    if (t.getCompletedSize() != null) completedSize += t.getCompletedSize();
                }
                stats.put("status_breakdown", statusCount);
                stats.put("total_size_bytes", totalSize);
                stats.put("completed_size_bytes", completedSize);
                return stats.toJSONString();
            } else {
                JSONArray arr = new JSONArray();
                for (TaskInstanceEntity t : instances) {
                    arr.add(instanceToJson(t));
                }
                return arr.toJSONString();
            }
        } catch (Exception e) {
            return JSON.toJSONString(Collections.singletonMap("error", e.getMessage()));
        }
    }

    private JSONObject instanceToJson(TaskInstanceEntity t) {
        JSONObject o = new JSONObject();
        o.put("id", t.getId());
        o.put("instanceName", t.getInstanceName());
        o.put("sourcePath", t.getSourcePath());
        o.put("targetPath", t.getTargetPath());
        o.put("status", t.getStatus());
        o.put("totalFiles", t.getTotalFiles());
        o.put("totalSize", t.getTotalSize());
        o.put("completedFiles", t.getCompletedFiles());
        o.put("completedSize", t.getCompletedSize());
        o.put("agentId", t.getAgentId());
        o.put("retryCount", t.getRetryCount());
        o.put("lastExecTime", t.getLastExecTime());
        o.put("completeTime", t.getCompleteTime());
        o.put("errorMsg", t.getErrorMsg());
        return o;
    }

    private AiConfigEntity getActiveConfig(Long configId) {
        if (configId != null) {
            AiConfigEntity config = configMapper.selectById(configId);
            if (config != null && config.getStatus() == 1) return config;
        }
        AiConfigEntity config = configMapper.selectOne(
                new LambdaQueryWrapper<AiConfigEntity>()
                        .eq(AiConfigEntity::getIsDefault, 1)
                        .eq(AiConfigEntity::getStatus, 1));
        if (config != null) return config;
        List<AiConfigEntity> list = configMapper.selectList(
                new LambdaQueryWrapper<AiConfigEntity>()
                        .eq(AiConfigEntity::getStatus, 1)
                        .orderByAsc(AiConfigEntity::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    private void saveMessage(Long conversationId, String role, String content) {
        AiMessageEntity msg = new AiMessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private String readAll(java.io.InputStream is) {
        if (is == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public List<AiConfigEntity> listConfigs() {
        List<AiConfigEntity> list = configMapper.selectList(
                new LambdaQueryWrapper<AiConfigEntity>().orderByDesc(AiConfigEntity::getIsDefault));
        for (AiConfigEntity c : list) {
            c.setApiKey(null);
        }
        return list;
    }

    public void addConfig(AiConfigEntity config) {
        config.setIsDefault(config.getIsDefault() != null ? config.getIsDefault() : 0);
        config.setStatus(1);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        configMapper.insert(config);
    }

    public void updateConfig(AiConfigEntity config) {
        AiConfigEntity existing = configMapper.selectById(config.getId());
        if (existing == null) throw new RuntimeException("Config not found");
        if (config.getConfigName() != null) existing.setConfigName(config.getConfigName());
        if (config.getBaseUrl() != null) existing.setBaseUrl(config.getBaseUrl());
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) existing.setApiKey(config.getApiKey());
        if (config.getModelName() != null) existing.setModelName(config.getModelName());
        if (config.getTemperature() != null) existing.setTemperature(config.getTemperature());
        if (config.getMaxTokens() != null) existing.setMaxTokens(config.getMaxTokens());
        if (config.getIsDefault() != null) existing.setIsDefault(config.getIsDefault());
        if (config.getStatus() != null) existing.setStatus(config.getStatus());
        existing.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(existing);
    }

    public void setDefaultConfig(Long id) {
        AiConfigEntity config = configMapper.selectById(id);
        if (config == null) throw new RuntimeException("Config not found");
        List<AiConfigEntity> all = configMapper.selectList(null);
        for (AiConfigEntity c : all) {
            c.setIsDefault(c.getId().equals(id) ? 1 : 0);
            c.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(c);
        }
    }

    public String testConnection(Long configId) {
        AiConfigEntity config;
        if (configId != null) {
            config = configMapper.selectById(configId);
        } else {
            throw new RuntimeException("Config ID is required");
        }
        if (config == null) throw new RuntimeException("Config not found");

        try {
            JSONObject body = new JSONObject();
            body.put("model", config.getModelName());
            body.put("messages", Collections.singletonList(
                    new JSONObject().fluentPut("role", "user").fluentPut("content", "Hi")));
            body.put("max_tokens", 10);
            body.put("stream", false);

            String url = config.getBaseUrl().replaceAll("/$", "") + "/chat/completions";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            byte[] bodyBytes = body.toJSONString().getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(bodyBytes);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String resp = readAll(conn.getInputStream());
                JSONObject respJson = JSON.parseObject(resp);
                JSONArray choices = respJson.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    String reply = choices.getJSONObject(0).getJSONObject("message").getString("content");
                    return "连接成功，模型回复: " + (reply != null ? reply.substring(0, Math.min(reply.length(), 50)) : "");
                }
                return "连接成功";
            } else {
                String errorResp = readAll(conn.getErrorStream());
                return "连接失败 (" + responseCode + "): " + errorResp;
            }
        } catch (Exception e) {
            return "连接异常: " + e.getMessage();
        }
    }
}
