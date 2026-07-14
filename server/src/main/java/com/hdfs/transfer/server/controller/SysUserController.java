package com.hdfs.transfer.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.server.entity.SysUserEntity;
import com.hdfs.transfer.server.service.SysUserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class SysUserController {

    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/page")
    public ApiResponse page(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "10") int pageSize,
                            @RequestParam(required = false) String keyword) {
        Page<SysUserEntity> page = userService.page(pageNum, pageSize, keyword);
        return ApiResponse.success(page);
    }

    @GetMapping("/current")
    public ApiResponse currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUserEntity user = userService.getByUsername(username);
        if (user != null) {
            user.setPassword(null);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        return ApiResponse.success(data);
    }

    @PostMapping
    public ApiResponse create(@RequestBody SysUserEntity user) {
        try {
            if (user.getPhone() == null || user.getPhone().isEmpty()) {
                return ApiResponse.error(400, "手机号不能为空");
            }
            userService.addUser(user);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping
    public ApiResponse update(@RequestBody SysUserEntity user) {
        try {
            if (user.getPhone() == null || user.getPhone().isEmpty()) {
                return ApiResponse.error(400, "手机号不能为空");
            }
            userService.updateUser(user);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/status")
    public ApiResponse updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            int status = Integer.parseInt(body.get("status").toString());
            if (status == 0) {
                String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
                SysUserEntity target = userService.getById(id);
                if (target != null && target.getUsername().equals(currentUsername)) {
                    return ApiResponse.error(400, "不能冻结自己的账户");
                }
            }
            userService.updateUserStatus(id, status);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 8) {
                return ApiResponse.error(400, "密码长度必须为6-8位");
            }
            if (!newPassword.matches(".*[a-zA-Z]+.*") || !newPassword.matches(".*[0-9]+.*")) {
                return ApiResponse.error(400, "密码必须包含字母和数字");
            }
            userService.resetPassword(id, newPassword);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
