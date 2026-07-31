package com.hdfs.transfer.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.TaskDTO;
import com.hdfs.transfer.server.entity.ClusterConfigEntity;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.entity.TaskInstanceEntity;
import com.hdfs.transfer.server.mapper.ClusterConfigMapper;
import com.hdfs.transfer.server.service.MigrationTaskService;
import com.hdfs.transfer.server.service.TaskInstanceService;
import com.hdfs.transfer.server.alert.AlertService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class MigrationTaskController {

    private final MigrationTaskService migrationTaskService;
    private final TaskInstanceService instanceService;
    private final AlertService alertService;
    private final ClusterConfigMapper clusterConfigMapper;

    public MigrationTaskController(MigrationTaskService migrationTaskService, TaskInstanceService instanceService,
                                   AlertService alertService, ClusterConfigMapper clusterConfigMapper) {
        this.migrationTaskService = migrationTaskService;
        this.instanceService = instanceService;
        this.alertService = alertService;
        this.clusterConfigMapper = clusterConfigMapper;
    }

    @GetMapping("/page")
    public ApiResponse page(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "10") int pageSize,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String agentId,
                            @RequestParam(required = false) String startTime,
                            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(migrationTaskService.page(pageNum, pageSize, keyword, status, agentId, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ApiResponse getById(@PathVariable Long id) {
        return ApiResponse.success(migrationTaskService.getById(id));
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String agentId,
                       @RequestParam(required = false) String startTime,
                       @RequestParam(required = false) String endTime,
                       HttpServletResponse response) throws Exception {
        List<MigrationTaskEntity> list = migrationTaskService.listForExport(keyword, status, agentId, startTime, endTime);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("迁移任务");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        String[] headers = {"任务名称", "任务类型", "源集群", "源路径", "目标集群", "目标路径",
                "distcp参数", "执行Agent", "状态", "最大重试次数",
                "最近执行时间", "创建时间"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < list.size(); i++) {
            MigrationTaskEntity task = list.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(task.getTaskName() != null ? task.getTaskName() : "");
            row.createCell(1).setCellValue("scheduled".equals(task.getTaskType()) ? "定时" : "一次性");
            row.createCell(2).setCellValue(task.getSourceClusterName() != null ? task.getSourceClusterName() : "");
            row.createCell(3).setCellValue(task.getSourcePath() != null ? task.getSourcePath() : "");
            row.createCell(4).setCellValue(task.getTargetClusterName() != null ? task.getTargetClusterName() : "");
            row.createCell(5).setCellValue(task.getTargetPath() != null ? task.getTargetPath() : "");
            row.createCell(6).setCellValue(task.getDistcpOptions() != null ? task.getDistcpOptions() : "");
            row.createCell(7).setCellValue(task.getAgentId() != null ? task.getAgentId() : "");
            row.createCell(8).setCellValue(task.getStatus() != null ? task.getStatus() : "");
            row.createCell(9).setCellValue(task.getMaxRetryCount() != null ? task.getMaxRetryCount() : 0);
            row.createCell(10).setCellValue(task.getLastExecTime() != null ? task.getLastExecTime() : "");
            row.createCell(11).setCellValue(task.getCreateTime() != null ? task.getCreateTime().format(dtf) : "");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("迁移任务列表.xlsx", "UTF-8"));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        OutputStream out = response.getOutputStream();
        workbook.write(out);
        workbook.close();
        out.flush();
    }

    @PostMapping
    public ApiResponse create(@RequestBody TaskDTO dto) {
        try {
            migrationTaskService.add(dto);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping
    public ApiResponse update(@RequestBody TaskDTO dto) {
        migrationTaskService.update(dto);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/alert")
    public ApiResponse updateAlert(@PathVariable Long id, @RequestBody AlertToggleRequest req) {
        boolean ok = migrationTaskService.updateAlertEnabled(id, req.alertEnabled);
        return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
    }

    @PostMapping("/{id}/online")
    public ApiResponse online(@PathVariable Long id) {
        try {
            boolean ok = migrationTaskService.online(id);
            return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/offline")
    public ApiResponse offline(@PathVariable Long id) {
        try {
            boolean ok = migrationTaskService.offline(id);
            return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/execute")
    public ApiResponse execute(@PathVariable Long id) {
        try {
            boolean ok = migrationTaskService.execute(id);
            return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/force-kill")
    public ApiResponse forceKill(@PathVariable Long id) {
        boolean ok = migrationTaskService.forceKill(id);
        return ok ? ApiResponse.success() : ApiResponse.error(404, "任务不存在");
    }

    @GetMapping("/dispatch")
    public ApiResponse dispatchTasks(@RequestParam String agentId) {
        List<TaskInstanceEntity> instances = instanceService.listDispatched(agentId);
        List<TaskDTO> result = new ArrayList<>();
        for (TaskInstanceEntity inst : instances) {
            boolean updated = instanceService.updateStatusIfMatch(inst.getId(), "running", "dispatching");
            if (!updated) {
                continue;
            }
            ClusterConfigEntity srcCluster = inst.getSourceClusterId() != null ?
                    clusterConfigMapper.selectById(inst.getSourceClusterId()) : null;
            ClusterConfigEntity tgtCluster = inst.getTargetClusterId() != null ?
                    clusterConfigMapper.selectById(inst.getTargetClusterId()) : null;

            TaskDTO dto = new TaskDTO();
            dto.setTaskId(inst.getId());
            dto.setTaskName(inst.getInstanceName());
            dto.setTaskType("once");
            dto.setSourceCluster(srcCluster != null ? srcCluster.getNameService() : null);
            dto.setSourcePath(buildHdfsPath(srcCluster, inst.getSourcePath()));
            dto.setTargetCluster(tgtCluster != null ? tgtCluster.getNameService() : null);
            dto.setTargetPath(buildHdfsPath(tgtCluster, inst.getTargetPath()));
            dto.setDistcpOptions(inst.getDistcpOptions());
            dto.setAgentId(inst.getAgentId());
            result.add(dto);
        }
        return ApiResponse.success(result);
    }

    private String buildHdfsPath(ClusterConfigEntity cluster, String path) {
        if (cluster == null || path == null) return path;
        if (path.startsWith("hdfs://")) return path;
        String ns = cluster.getNameService();
        if (ns != null && !ns.isEmpty()) {
            return "hdfs://" + ns + path;
        }
        String rpc = cluster.getNameNodeRpc();
        if (rpc != null && !rpc.isEmpty()) {
            return "hdfs://" + rpc + path;
        }
        return path;
    }

    @PostMapping("/{id}/status")
    public ApiResponse updateStatus(@PathVariable Long id,
                                    @RequestBody java.util.Map<String, Object> body) {
        String status = (String) body.get("status");
        long completedFiles = body.get("completedFiles") != null ?
                Long.parseLong(body.get("completedFiles").toString()) : 0;
        long completedSize = body.get("completedSize") != null ?
                Long.parseLong(body.get("completedSize").toString()) : 0;
        long totalFiles = body.get("totalFiles") != null ?
                Long.parseLong(body.get("totalFiles").toString()) : 0;
        long totalSize = body.get("totalSize") != null ?
                Long.parseLong(body.get("totalSize").toString()) : 0;
        String errorMsg = (String) body.get("errorMsg");
        migrationTaskService.updateProgress(id, completedFiles, completedSize, totalFiles, totalSize, status, errorMsg);
        if ("failed".equals(status)) {
            TaskInstanceEntity instance = instanceService.getById(id);
            if (instance != null && "killed".equals(instance.getStatus())) {
                return ApiResponse.success();
            }
            String instanceName = instance != null ? instance.getInstanceName() : String.valueOf(id);
            boolean taskAlertEnabled = false;
            if (instance != null && instance.getParentTaskId() != null) {
                MigrationTaskEntity template = migrationTaskService.getById(instance.getParentTaskId());
                taskAlertEnabled = template != null && Boolean.TRUE.equals(template.getAlertEnabled());
            }
            alertService.notifyTaskFailed(id, instanceName, errorMsg, taskAlertEnabled);
        }
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        try {
            migrationTaskService.delete(id);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}

class AlertToggleRequest {
    public Boolean alertEnabled;
}