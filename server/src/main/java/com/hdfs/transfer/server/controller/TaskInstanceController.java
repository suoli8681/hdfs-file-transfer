package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.server.entity.TaskInstanceEntity;
import com.hdfs.transfer.server.service.TaskInstanceService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;

@RestController
@RequestMapping("/api/task-instances")
public class TaskInstanceController {

    private final TaskInstanceService instanceService;

    public TaskInstanceController(TaskInstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @GetMapping("/page")
    public ApiResponse page(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "10") int pageSize,
                            @RequestParam(required = false) Long parentTaskId,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String agentId,
                            @RequestParam(required = false) String startTime,
                            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(instanceService.page(pageNum, pageSize, parentTaskId, keyword, status, agentId, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ApiResponse getById(@PathVariable Long id) {
        return ApiResponse.success(instanceService.getById(id));
    }

    @PostMapping("/{id}/force-kill")
    public ApiResponse forceKill(@PathVariable Long id) {
        boolean ok = instanceService.forceKill(id);
        return ok ? ApiResponse.success() : ApiResponse.error(404, "实例不存在");
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) Long parentTaskId,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String agentId,
                       @RequestParam(required = false) String startTime,
                       @RequestParam(required = false) String endTime,
                       HttpServletResponse response) throws Exception {
        List<TaskInstanceEntity> list = instanceService.listForExport(parentTaskId, keyword, status, agentId, startTime, endTime);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("任务实例");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        String[] headers = {"实例名称", "源集群", "源路径", "目标集群", "目标路径",
                "distcp参数", "执行Agent", "状态", "总文件数", "已完成文件数",
                "总数据量", "已完成数据量", "重试次数", "启动时间", "完成时间", "错误信息"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < list.size(); i++) {
            TaskInstanceEntity inst = list.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(inst.getInstanceName() != null ? inst.getInstanceName() : "");
            row.createCell(1).setCellValue(inst.getSourceClusterName() != null ? inst.getSourceClusterName() : "");
            row.createCell(2).setCellValue(inst.getSourcePath() != null ? inst.getSourcePath() : "");
            row.createCell(3).setCellValue(inst.getTargetClusterName() != null ? inst.getTargetClusterName() : "");
            row.createCell(4).setCellValue(inst.getTargetPath() != null ? inst.getTargetPath() : "");
            row.createCell(5).setCellValue(inst.getDistcpOptions() != null ? inst.getDistcpOptions() : "");
            row.createCell(6).setCellValue(inst.getAgentId() != null ? inst.getAgentId() : "");
            row.createCell(7).setCellValue(inst.getStatus() != null ? inst.getStatus() : "");
            row.createCell(8).setCellValue(inst.getTotalFiles() != null ? inst.getTotalFiles() : 0);
            row.createCell(9).setCellValue(inst.getCompletedFiles() != null ? inst.getCompletedFiles() : 0);
            row.createCell(10).setCellValue(inst.getTotalSize() != null ? inst.getTotalSize() : 0);
            row.createCell(11).setCellValue(inst.getCompletedSize() != null ? inst.getCompletedSize() : 0);
            row.createCell(12).setCellValue(inst.getRetryCount() != null ? inst.getRetryCount() : 0);
            row.createCell(13).setCellValue(inst.getLastExecTime() != null ? inst.getLastExecTime() : "");
            row.createCell(14).setCellValue(inst.getCompleteTime() != null ? inst.getCompleteTime() : "");
            row.createCell(15).setCellValue(inst.getErrorMsg() != null ? inst.getErrorMsg() : "");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("任务实例列表.xlsx", "UTF-8"));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        OutputStream out = response.getOutputStream();
        workbook.write(out);
        workbook.close();
        out.flush();
    }
}
