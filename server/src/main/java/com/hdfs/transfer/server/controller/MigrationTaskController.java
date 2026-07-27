package com.hdfs.transfer.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.common.dto.TaskDTO;
import com.hdfs.transfer.server.entity.MigrationTaskEntity;
import com.hdfs.transfer.server.entity.TaskInstanceEntity;
import com.hdfs.transfer.server.service.MigrationTaskService;
import com.hdfs.transfer.server.service.TaskInstanceService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class MigrationTaskController {

    private final MigrationTaskService migrationTaskService;
    private final TaskInstanceService instanceService;

    public MigrationTaskController(MigrationTaskService migrationTaskService, TaskInstanceService instanceService) {
        this.migrationTaskService = migrationTaskService;
        this.instanceService = instanceService;
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
                "distcp参数", "执行Agent", "状态", "总文件数", "已完成文件数",
                "总数据量", "已完成数据量", "重试次数", "最大重试次数",
                "启动时间", "完成时间", "创建时间", "错误信息"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

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
            row.createCell(9).setCellValue(task.getTotalFiles() != null ? task.getTotalFiles() : 0);
            row.createCell(10).setCellValue(task.getCompletedFiles() != null ? task.getCompletedFiles() : 0);
            row.createCell(11).setCellValue(task.getTotalSize() != null ? task.getTotalSize() : 0);
            row.createCell(12).setCellValue(task.getCompletedSize() != null ? task.getCompletedSize() : 0);
            row.createCell(13).setCellValue(task.getRetryCount() != null ? task.getRetryCount() : 0);
            row.createCell(14).setCellValue(task.getMaxRetryCount() != null ? task.getMaxRetryCount() : 0);
            row.createCell(15).setCellValue(task.getLastExecTime() != null ? task.getLastExecTime() : "");
            row.createCell(16).setCellValue(task.getCompleteTime() != null ? task.getCompleteTime() : "");
            row.createCell(17).setCellValue(task.getCreateTime() != null ? task.getCreateTime().toString() : "");
            row.createCell(18).setCellValue(task.getErrorMsg() != null ? task.getErrorMsg() : "");
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
            TaskDTO dto = new TaskDTO();
            dto.setTaskId(inst.getId());
            dto.setTaskName(inst.getInstanceName());
            dto.setTaskType("once");
            dto.setSourceCluster(String.valueOf(inst.getSourceClusterId()));
            dto.setSourcePath(inst.getSourcePath());
            dto.setTargetCluster(String.valueOf(inst.getTargetClusterId()));
            dto.setTargetPath(inst.getTargetPath());
            dto.setDistcpOptions(inst.getDistcpOptions());
            dto.setAgentId(inst.getAgentId());
            result.add(dto);
        }
        return ApiResponse.success(result);
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