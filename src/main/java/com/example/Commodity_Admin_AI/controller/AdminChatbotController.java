package com.example.Commodity_Admin_AI.controller;

import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.service.AdminChatbotService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the Admin Chatbot REST API endpoint.
 * Thin controller responsible only for receiving, validating requests,
 * delegating to AdminChatbotService, and returning the response.
 */
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
@RestController
@RequestMapping("/api/admin")
public class AdminChatbotController {

    private final AdminChatbotService adminChatbotService;

    public AdminChatbotController(AdminChatbotService adminChatbotService) {
        this.adminChatbotService = adminChatbotService;
    }

    /**
     * Process an incoming natural language chat query from an authorized admin.
     *
     * @param request the chat request payload
     * @return the chat response containing session and message
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = adminChatbotService.processChat(request);
        return ResponseEntity.ok(response);
    }
}
