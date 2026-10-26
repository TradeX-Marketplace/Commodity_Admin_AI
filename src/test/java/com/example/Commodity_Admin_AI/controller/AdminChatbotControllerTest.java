package com.example.Commodity_Admin_AI.controller;

import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.service.AdminChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminChatbotControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminChatbotService adminChatbotService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        AdminChatbotController controller = new AdminChatbotController(adminChatbotService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/admin/chat should return 200 and ChatResponse when request is valid")
    void chat_ValidRequest_ReturnsOk() throws Exception {
        ChatResponse expectedResponse = new ChatResponse(1L, "Current rate is 5000");
        when(adminChatbotService.processChat(any(ChatRequest.class))).thenReturn(expectedResponse);

        String requestJson = """
                {
                    "sessionId": 1,
                    "message": "XYZ current rate?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(1))
                .andExpect(jsonPath("$.message").value("Current rate is 5000"));

        verify(adminChatbotService).processChat(any(ChatRequest.class));
    }

    @Test
    @DisplayName("POST /api/admin/chat should return 400 Bad Request when message is blank")
    void chat_BlankMessage_ReturnsBadRequest() throws Exception {
        String requestJson = """
                {
                    "sessionId": 1,
                    "message": ""
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CORS request from http://localhost:3000 includes Access-Control-Allow-Origin header")
    void cors_RequestFromPort3000_IncludesCorsHeader() throws Exception {
        ChatResponse expectedResponse = new ChatResponse(1L, "Current rate is 5000");
        when(adminChatbotService.processChat(any(ChatRequest.class))).thenReturn(expectedResponse);

        String requestJson = """
                {
                    "sessionId": 1,
                    "message": "XYZ current rate?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    @DisplayName("CORS request from unauthorized origin does not include Access-Control-Allow-Origin header")
    void cors_RequestFromUnauthorizedOrigin_DoesNotIncludeCorsHeader() throws Exception {
        String requestJson = """
                {
                    "sessionId": 1,
                    "message": "XYZ current rate?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .header("Origin", "http://malicious-site.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());
    }
}
