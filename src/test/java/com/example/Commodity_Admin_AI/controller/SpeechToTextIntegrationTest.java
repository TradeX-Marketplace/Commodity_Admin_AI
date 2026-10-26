package com.example.Commodity_Admin_AI.controller;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import com.example.Commodity_Admin_AI.service.AdminChatbotService;
import com.example.Commodity_Admin_AI.service.IntentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.WebApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STEP 20 — MICROPHONE / SPEECH-TO-TEXT INTEGRATION TEST SUITE
 *
 * Verifies that:
 * 1. Existing POST /api/admin/chat still works with speech-to-text strings without authentication.
 * 2. Existing English chatbot flow works with transcribed text.
 * 3. Existing Marathi chatbot flow works with transcribed text.
 * 4. Existing Hindi chatbot flow works with transcribed text.
 * 5. Speech-to-text output can be passed to ChatRequest.
 * 6. No raw audio is persisted in ChatMessage or ChatSession entities.
 * 7. No second chatbot endpoint (e.g., /voice-chat, /audio-chat) was created.
 * 8. Existing RAG flow is unchanged and receives exact transcript text.
 * 9. Existing marketplace-data integration remains unchanged.
 * 10. Existing read-only restrictions remain enforced for spoken write attempts.
 * 11. Testing UI page (index.html) is served statically.
 */
@SpringBootTest
class SpeechToTextIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AdminChatbotService adminChatbotService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private RagService ragService;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private IntentService intentService;

    private MockMvc mockMvc;
    private ChatSession testSession;

    @TestConfiguration
    static class SttTestConfig {
        @Bean
        @Primary
        public ChatSessionRepository chatSessionRepository() {
            return Mockito.mock(ChatSessionRepository.class);
        }

        @Bean
        @Primary
        public ChatMessageRepository chatMessageRepository() {
            return Mockito.mock(ChatMessageRepository.class);
        }

        @Bean
        @Primary
        public RagService ragService(IntentService intentService) {
            return Mockito.mock(RagService.class);
        }

        @Bean
        @Primary
        public GeminiService geminiService() {
            return Mockito.mock(GeminiService.class);
        }
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(chatSessionRepository, chatMessageRepository, ragService, geminiService);

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .build();

        testSession = new ChatSession("admin@marketplace.com");
        testSession.setId(200L);

        when(chatSessionRepository.findById(200L)).thenReturn(Optional.of(testSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(200L);
            }
            return s;
        });

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(anyLong()))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(ragService.retrieveContext(anyString(), anyString()))
                .thenReturn("Soybean rate in Sangli is 42000 per MT.");
        when(geminiService.generateResponse(anyString(), anyString()))
                .thenReturn("The current soybean rate in Sangli is ₹42,000 per MT.");
    }

    // =========================================================================
    // 1. EXISTING POST /api/admin/chat STILL WORKS WITH STT TEXT
    // =========================================================================
    @Test
    @DisplayName("1. Existing POST /api/admin/chat accepts ordinary speech-to-text transcription without authentication")
    void test01_ExistingPostChat_WorksWithSttTranscript() throws Exception {
        // Speech-to-text produces standard text in English
        String sttTranscript = "What is the current soybean rate in Sangli?";

        String requestJson = String.format("""
                {
                    "sessionId": 200,
                    "message": "%s"
                }
                """, sttTranscript);

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(200))
                .andExpect(jsonPath("$.message").value("The current soybean rate in Sangli is ₹42,000 per MT."))
                .andExpect(jsonPath("$.language").value("English"));

        verify(ragService).retrieveContext(eq(sttTranscript), eq("CURRENT_RATE"));
        verify(geminiService).generateResponse(eq(sttTranscript), anyString());
    }

    // =========================================================================
    // 4. ENGLISH SPEECH-TO-TEXT FLOW
    // =========================================================================
    @Test
    @DisplayName("4. English STT transcript flows through existing pipeline and identifies English")
    void test04_EnglishChatbotFlow_WorksWithSttText() throws Exception {
        String sttTranscript = "Show me today listings for maize";

        String requestJson = String.format("""
                {
                    "sessionId": 200,
                    "message": "%s"
                }
                """, sttTranscript);

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("English"));

        verify(ragService).retrieveContext(eq(sttTranscript), eq("TODAY_LISTINGS"));
    }

    // =========================================================================
    // 5. MARATHI SPEECH-TO-TEXT FLOW
    // =========================================================================
    @Test
    @DisplayName("5. Marathi STT transcript flows through existing pipeline and identifies Marathi")
    void test05_MarathiChatbotFlow_WorksWithSttText() throws Exception {
        String sttTranscript = "सोयाबीनचा आजचा दर काय आहे?";

        String requestJson = String.format("""
                {
                    "sessionId": 200,
                    "message": "%s"
                }
                """, sttTranscript);

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("Marathi"));

        verify(ragService).retrieveContext(eq(sttTranscript), eq("CURRENT_RATE"));
    }

    // =========================================================================
    // 6. HINDI SPEECH-TO-TEXT FLOW
    // =========================================================================
    @Test
    @DisplayName("6. Hindi STT transcript flows through existing pipeline and identifies Hindi")
    void test06_HindiChatbotFlow_WorksWithSttText() throws Exception {
        String sttTranscript = "सोयाबीन का आज का भाव क्या है?";

        String requestJson = String.format("""
                {
                    "sessionId": 200,
                    "message": "%s"
                }
                """, sttTranscript);

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("Hindi"));

        verify(ragService).retrieveContext(eq(sttTranscript), eq("CURRENT_RATE"));
    }

    // =========================================================================
    // 7. SPEECH-TO-TEXT OUTPUT CAN BE PASSED TO ChatRequest
    // =========================================================================
    @Test
    @DisplayName("7. ChatRequest handles speech-to-text text seamlessly with optional language override")
    void test07_SpeechToTextOutput_PassedToChatRequest() {
        ChatRequest req1 = new ChatRequest("Transcribed voice question");
        assertEquals("Transcribed voice question", req1.getMessage());

        ChatRequest req2 = new ChatRequest(200L, "मराठीतील आवाज प्रश्न", "Marathi");
        assertEquals(200L, req2.getSessionId());
        assertEquals("मराठीतील आवाज प्रश्न", req2.getMessage());
        assertEquals("Marathi", req2.getLanguage());
    }

    // =========================================================================
    // 8. NO RAW AUDIO IS PERSISTED IN ENTITIES
    // =========================================================================
    @Test
    @DisplayName("8. Verify ChatMessage and ChatSession entities contain NO byte arrays, Blobs, or audio fields")
    void test08_NoRawAudioPersisted_EntityVerification() {
        Class<?>[] entityClasses = {ChatMessage.class, ChatSession.class};

        for (Class<?> clazz : entityClasses) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                // Must not store raw bytes, blobs, audio streams, or media files
                assertFalse(field.getType().equals(byte[].class),
                        "Entity " + clazz.getSimpleName() + " must NOT contain byte[] audio field: " + field.getName());
                assertFalse(field.getType().getName().toLowerCase().contains("blob"),
                        "Entity " + clazz.getSimpleName() + " must NOT contain Blob field: " + field.getName());
                assertFalse(field.getName().toLowerCase().contains("audio"),
                        "Entity " + clazz.getSimpleName() + " must NOT contain audio field: " + field.getName());
                assertFalse(field.getName().toLowerCase().contains("voice"),
                        "Entity " + clazz.getSimpleName() + " must NOT contain voice field: " + field.getName());
            }
        }
    }

    // =========================================================================
    // 9. NO SECOND CHATBOT ENDPOINT WAS CREATED
    // =========================================================================
    @Test
    @DisplayName("9. Verify AdminChatbotController exposes ONLY /chat and NO separate voice-chat or audio-chat endpoints")
    void test09_NoSecondChatbotEndpointCreated() {
        Method[] methods = AdminChatbotController.class.getDeclaredMethods();
        long postEndpoints = 0;

        for (Method m : methods) {
            if (m.isAnnotationPresent(PostMapping.class)) {
                PostMapping postMapping = m.getAnnotation(PostMapping.class);
                postEndpoints++;
                String path = postMapping.value().length > 0 ? postMapping.value()[0] : "";
                assertEquals("/chat", path, "AdminChatbotController must ONLY expose /chat endpoint!");
                assertFalse(path.contains("voice"), "Must NOT create /voice-chat endpoint");
                assertFalse(path.contains("audio"), "Must NOT create /audio-chat endpoint");
            }
        }

        assertEquals(1, postEndpoints, "AdminChatbotController must have exactly ONE POST endpoint (/chat)");
    }

    // =========================================================================
    // 10. EXISTING RAG FLOW IS UNCHANGED
    // =========================================================================
    @Test
    @DisplayName("10. RAG flow receives exact spoken text unchanged from STT")
    void test10_ExistingRagFlow_UnchangedForSttInput() throws Exception {
        String sttTranscript = "Explain standard moisture specifications for soybean trading";

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"sessionId\":200,\"message\":\"%s\"}", sttTranscript)))
                .andExpect(status().isOk());

        verify(ragService).retrieveContext(eq(sttTranscript), eq("COMMODITY_INFORMATION"));
    }

    // =========================================================================
    // 11. EXISTING MARKETPLACE DATA INTEGRATION IS UNCHANGED
    // =========================================================================
    @Test
    @DisplayName("11. Marketplace data HTTP/JSON contract remains unchanged for STT input")
    void test11_ExistingMarketplaceDataIntegration_Unchanged() {
        assertTrue(IntentService.PATH_MARKETPLACE_DATA.equals(intentService.determineRetrievalPath("What is today's soybean rate in Sangli?")));
        assertTrue(IntentService.PATH_MARKETPLACE_DATA.equals(intentService.determineRetrievalPath("सोयाबीनचा आजचा दर काय आहे?")));
        assertTrue(IntentService.PATH_MARKETPLACE_DATA.equals(intentService.determineRetrievalPath("सोयाबीन का आज का भाव क्या है?")));
    }

    // =========================================================================
    // 12. EXISTING READ-ONLY RESTRICTIONS REMAIN UNCHANGED FOR SPOKEN WRITE ATTEMPTS
    // =========================================================================
    @Test
    @DisplayName("12. Spoken write attempts are rejected with Phase 1 read-only notice without calling Gemini")
    void test12_ExistingReadOnlyRestrictions_UnchangedForSttInput() throws Exception {
        // Spoken attempt to modify market rates in Marathi
        String spokenWriteQuery = "सोयाबीनचा नवीन दर तयार करा";

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"sessionId\":200,\"message\":\"%s\"}", spokenWriteQuery)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("READ-ONLY")))
                .andExpect(jsonPath("$.language").value("Marathi"));

        // Gemini and RAG must NOT be called for write operations
        verify(geminiService, never()).generateResponse(anyString(), anyString());
        verify(ragService, never()).retrieveContext(anyString(), anyString());
    }

    // =========================================================================
    // 13. STATIC TESTING PAGE IS SERVED FOR STT/MICROPHONE VERIFICATION
    // =========================================================================
    @Test
    @DisplayName("13. Static test page index.html is accessible without authentication")
    void test13_StaticTestingPage_IsAccessible() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }
}
