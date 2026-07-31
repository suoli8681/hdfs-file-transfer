package com.hdfs.transfer.agent.executor;

import com.hdfs.transfer.agent.config.AgentConfig;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskStateStore {

    private static final Logger log = LoggerFactory.getLogger(TaskStateStore.class);

    private final AgentConfig agentConfig;
    private final Map<Long, TaskMetadata> cache = new ConcurrentHashMap<>();

    public TaskStateStore(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        ensureStateDir();
    }

    private void ensureStateDir() {
        try {
            Files.createDirectories(Paths.get(agentConfig.getWorkDir(), "state"));
        } catch (IOException e) {
            log.error("Failed to create state directory", e);
        }
    }

    private Path getStateFilePath(Long taskId) {
        return Paths.get(agentConfig.getWorkDir(), "state", "task-" + taskId + ".json");
    }

    public void save(TaskMetadata metadata) {
        cache.put(metadata.getTaskId(), metadata);
        try {
            String json = JSON.toJSONString(metadata, true);
            Files.write(getStateFilePath(metadata.getTaskId()), json.getBytes("UTF-8"));
        } catch (IOException e) {
            log.error("Failed to save task state for task {}", metadata.getTaskId(), e);
        }
    }

    public void remove(Long taskId) {
        cache.remove(taskId);
        try {
            Files.deleteIfExists(getStateFilePath(taskId));
        } catch (IOException e) {
            log.warn("Failed to remove task state file for task {}", taskId, e);
        }
    }

    public List<TaskMetadata> loadUnfinished() {
        List<TaskMetadata> result = new ArrayList<>();
        try {
            Path stateDir = Paths.get(agentConfig.getWorkDir(), "state");
            if (!Files.exists(stateDir)) return result;

            Files.list(stateDir)
                    .filter(p -> p.getFileName().toString().startsWith("task-") && p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            String content = new String(Files.readAllBytes(p), "UTF-8");
                            TaskMetadata metadata = JSON.parseObject(content, new TypeReference<TaskMetadata>() {});
                            if (metadata != null && metadata.getTaskId() != null) {
                                result.add(metadata);
                                cache.put(metadata.getTaskId(), metadata);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to read task state file: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list task state files", e);
        }
        return result;
    }

    public static class TaskMetadata {
        private Long taskId;
        private String sourcePath;
        private String targetPath;
        private String sourceCluster;
        private String targetCluster;
        private String distcpOptions;
        private long totalFiles;
        private long totalSize;

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getSourcePath() { return sourcePath; }
        public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
        public String getTargetPath() { return targetPath; }
        public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
        public String getSourceCluster() { return sourceCluster; }
        public void setSourceCluster(String sourceCluster) { this.sourceCluster = sourceCluster; }
        public String getTargetCluster() { return targetCluster; }
        public void setTargetCluster(String targetCluster) { this.targetCluster = targetCluster; }
        public String getDistcpOptions() { return distcpOptions; }
        public void setDistcpOptions(String distcpOptions) { this.distcpOptions = distcpOptions; }
        public long getTotalFiles() { return totalFiles; }
        public void setTotalFiles(long totalFiles) { this.totalFiles = totalFiles; }
        public long getTotalSize() { return totalSize; }
        public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    }
}
