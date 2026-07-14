package com.hdfs.transfer.server.api;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.server.entity.SysUserEntity;
import com.hdfs.transfer.server.security.JwtTokenProvider;
import com.hdfs.transfer.server.service.LoginLogService;
import com.hdfs.transfer.server.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService userService;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final LoginLogService loginLogService;

    public AuthController(SysUserService userService, JwtTokenProvider tokenProvider,
                          PasswordEncoder passwordEncoder, LoginLogService loginLogService) {
        this.userService = userService;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.loginLogService = loginLogService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SysUserEntity user = userService.getByUsername(request.getUsername());
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ApiResponse.error(401, "密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            return ApiResponse.error(403, "账号已禁用");
        }
        String token = tokenProvider.generateToken(user.getUsername());
        loginLogService.record(user.getUsername(), getClientIp(httpRequest));
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("role", user.getRole());
        return ApiResponse.success(data);
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterRequest request) {
        SysUserEntity user = new SysUserEntity();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        if (!userService.register(user)) {
            return ApiResponse.error(400, "用户名已存在");
        }
        return ApiResponse.success();
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info(@RequestHeader("Authorization") String auth) {
        String token = auth.replace("Bearer ", "");
        String username = tokenProvider.getUsernameFromToken(token);
        SysUserEntity user = userService.getByUsername(username);
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        return ApiResponse.success(data);
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestHeader("Authorization") String auth,
                                                           @RequestBody ProfileRequest request) {
        String token = auth.replace("Bearer ", "");
        String currentUsername = tokenProvider.getUsernameFromToken(token);
        boolean ok = userService.updateProfile(currentUsername, request.getUsername(),
                request.getRealName(), request.getEmail(), request.getPhone());
        if (!ok) {
            return ApiResponse.error(400, "用户名已存在或更新失败");
        }
        // If username changed, generate new token
        Map<String, Object> data = new HashMap<>();
        if (request.getUsername() != null && !request.getUsername().equals(currentUsername)) {
            String newToken = tokenProvider.generateToken(request.getUsername());
            data.put("token", newToken);
            data.put("username", request.getUsername());
        } else {
            data.put("username", currentUsername);
        }
        data.put("realName", request.getRealName());
        return ApiResponse.success(data);
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@RequestHeader("Authorization") String auth,
                                             @RequestBody PasswordRequest request) {
        String token = auth.replace("Bearer ", "");
        String username = tokenProvider.getUsernameFromToken(token);
        boolean ok = userService.changePassword(username, request.getOldPassword(), request.getNewPassword());
        if (!ok) {
            return ApiResponse.error(400, "原密码错误");
        }
        return ApiResponse.success();
    }

    public static class LoginRequest {
        private String username;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RegisterRequest {
        private String username;
        private String password;
        private String realName;
        private String email;
        private String phone;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class ProfileRequest {
        private String username;
        private String realName;
        private String email;
        private String phone;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class PasswordRequest {
        private String oldPassword;
        private String newPassword;
        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip;
    }
}