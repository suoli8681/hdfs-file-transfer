package com.hdfs.transfer.agent.communication;

import com.hdfs.transfer.agent.config.AgentConfig;
import com.hdfs.transfer.agent.executor.TaskExecutionManager;
import com.hdfs.transfer.common.dto.TaskDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Service
public class TaskPollerService {

    private static final Logger log = LoggerFactory.getLogger(TaskPollerService.class);

    private final AgentConfig agentConfig;
    private final TaskExecutionManager executionManager;
    private final RestTemplate restTemplate;
    private final ExecutorService taskExecutor;

    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();

    public TaskPollerService(AgentConfig agentConfig, TaskExecutionManager executionManager,
                             @Qualifier("taskExecutorService") ExecutorService taskExecutor) {
        this.agentConfig = agentConfig;
        this.executionManager = executionManager;
        this.taskExecutor = taskExecutor;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Scheduled(fixedDelay = 15000)
    public void pollTasks() {
        try {
            String url = agentConfig.getServerBaseUrl() + "/api/tasks/dispatch?agentId=" + agentConfig.getAgentId();
            com.hdfs.transfer.common.dto.ApiResponse response = restTemplate.getForObject(
                    url, com.hdfs.transfer.common.dto.ApiResponse.class);

            if (response != null && response.getData() != null) {
                String json = com.alibaba.fastjson.JSON.toJSONString(response.getData());
                List<TaskDTO> tasks = com.alibaba.fastjson.JSON.parseArray(json, TaskDTO.class);
                for (TaskDTO task : tasks) {
                    // Skip already running tasks
                    if (runningTaskIds.contains(task.getTaskId())) {
                        log.debug("Task {} already running, skip", task.getTaskId());
                        continue;
                    }
                    // Skip if max parallel reached
                    if (runningTaskIds.size() >= agentConfig.getMaxParallelTasks()) {
                        log.debug("Max parallel tasks reached ({}), skip task {}",
                                runningTaskIds.size(), task.getTaskId());
                        break;
                    }

                    final Long taskId = task.getTaskId();
                    runningTaskIds.add(taskId);
                    log.info("Submitting task {} to executor, running={}", taskId, runningTaskIds.size());

                    taskExecutor.submit(() -> {
                        try {
                            executionManager.executeTask(
                                    taskId,
                                    task.getSourcePath(),
                                    task.getTargetPath(),
                                    task.getSourceCluster(),
                                    task.getTargetCluster(),
                                    task.getDistcpOptions()
                            );
                        } catch (Exception e) {
                            log.error("Task {} execution failed", taskId, e);
                        } finally {
                            runningTaskIds.remove(taskId);
                            log.info("Task {} finished, running={}", taskId, runningTaskIds.size());
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.debug("No tasks to poll (server might be unavailable): {}", e.getMessage());
        }
    }

    public int getRunningTaskCount() {
        return runningTaskIds.size();
    }
}