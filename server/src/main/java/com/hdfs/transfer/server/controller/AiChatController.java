package com.hdfs.transfer.server.controller;

import com.hdfs.transfer.common.dto.ApiResponse;
import com.hdfs.transfer.server.entity.AiConfigEntity;
import com.hdfs.transfer.server.entity.AiConversationEntity;
import com.hdfs.transfer.server.entity.AiMessageEntity;
import com.hdfs.transfer.server.service.AiChatService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    private String currentUser() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping("/conversations")
    public ApiResponse createConversation(@RequestParam(required = false) Long configId) {
        Long id = chatService.createConversation(currentUser(), configId);
        return ApiResponse.success(id);
    }

    @GetMapping("/conversations")
    public ApiResponse listConversations() {
        List<AiConversationEntity> list = chatService.listConversations(currentUser());
        return ApiResponse.success(list);
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse getMessages(@PathVariable Long id) {
        List<AiMessageEntity> messages = chatService.getMessages(id, currentUser());
        return ApiResponse.success(messages);
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id, currentUser());
        return ApiResponse.success();
    }

    @GetMapping("/chat")
    public SseEmitter chat(@RequestParam Long conversationId, @RequestParam String message,
                           @RequestParam(required = false) Long configId) {
        return chatService.chat(conversationId, message, currentUser(), configId);
    }

    @GetMapping("/configs")
    public ApiResponse listConfigs() {
        return ApiResponse.success(chatService.listConfigs());
    }

    @PostMapping("/configs")
    public ApiResponse createConfig(@RequestBody AiConfigEntity config) {
        try {
            chatService.addConfig(config);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/configs")
    public ApiResponse updateConfig(@RequestBody AiConfigEntity config) {
        try {
            chatService.updateConfig(config);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/configs/{id}/default")
    public ApiResponse setDefault(@PathVariable Long id) {
        try {
            chatService.setDefaultConfig(id);
            return ApiResponse.success();
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/configs/{id}/test")
    public ApiResponse testConnection(@PathVariable Long id) {
        String result = chatService.testConnection(id);
        return ApiResponse.success(result);
    }
}
