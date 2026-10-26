package com.example.Commodity_Admin_AI.controller;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.MarketplaceRateDto;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import com.example.Commodity_Admin_AI.service.AdminChatbotService;
import com.example.Commodity_Admin_AI.service.FutureMarketplaceArchitecture;
import com.example.Commodity_Admin_AI.service.IntentService;
import com.example.Commodity_Admin_AI.service.MarketplaceDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STEP 18 — COMPREHENSIVE BACKEND TESTING SUITE
 *
 * Verifies the end-to-end POST /api/admin/chat flow and architectural boundaries:
 * 1. Unauthenticated request without token or Authorization header
 * 4. English questions
 * 5. Marathi questions
 * 6. Hindi questions
 * 7. Current market-rate questions
 * 8. Rate-history / rate-trend questions
 * 9. Listing-related questions
 * 10. Buyer-demand questions
 * 11. Knowledge/RAG questions
 * 12. Combined marketplace + knowledge questions
 * 13. Unknown or unsupported intent
 * 14. Empty/blank message validation
 * 15. Invalid request/session handling
 * 16. Existing chat-session history
 * 17. Multi-turn conversation context
 * 18. Marketplace HTTP/JSON integration
 * 19. RAG/PGVector retrieval path
 * 20. Gemini receives only controlled question/history/context
 * 21. Read-only protection remains enforced
 * 22. No direct Marketplace PostgreSQL access
 * 23. Future Phase 2 write operations remain unavailable
 */
@SpringBootTest
@Import(AdminChatbotBackendIntegrationTest.BackendTestConfig.class)
class AdminChatbotBackendIntegrationTest {

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
    static class BackendTestConfig {
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
        testSession.setId(100L);

        // Default mock behaviors
        when(chatSessionRepository.findById(100L)).thenReturn(Optional.of(testSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(100L);
            }
            return s;
        });

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(anyLong()))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(ragService.retrieveContext(anyString(), anyString()))
                .thenReturn("Marketplace rates context: Soybean in Sangli is 42000 per MT.");
        when(geminiService.generateResponse(anyString(), anyString()))
                .thenReturn("The current soybean rate in Sangli is ₹42,000 per MT.");
        when(geminiService.generateResponse(anyString(), anyString(), anyList()))
                .thenReturn("Multi-turn response based on prior turns.");
    }

    // =========================================================================
    // 1. UNAUTHENTICATED CHAT REQUEST
    // =========================================================================
    @Test
    @DisplayName("1. Unauthenticated request without token or Authorization header is accepted with HTTP 200 OK")
    void test01_UnauthenticatedRequest_AllowedWith200() throws Exception {
        String requestJson = """
                {
                    "sessionId": 100,
                    "message": "What is the soybean rate in Sangli?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(100))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // =========================================================================
    // 4. ENGLISH QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("4. English question processes correctly and calls RAG and Gemini")
    void test04_EnglishQuestion_ProcessedSuccessfully() throws Exception {
        String requestJson = """
                {
                    "sessionId": 100,
                    "message": "What is the current soybean rate in Sangli?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(100));

        verify(ragService).retrieveContext(eq("What is the current soybean rate in Sangli?"), eq("CURRENT_RATE"));
        verify(geminiService).generateResponse(eq("What is the current soybean rate in Sangli?"), any());
    }

    // =========================================================================
    // 5. MARATHI QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("5. Marathi question processes correctly and detects intent")
    void test05_MarathiQuestion_ProcessedSuccessfully() throws Exception {
        when(geminiService.generateResponse(anyString(), anyString()))
                .thenReturn("सांगलीत सोयाबीनचा चालू दर ₹४२,००० प्रति टन आहे.");

        String requestJson = """
                {
                    "sessionId": 100,
                    "message": "सोयाबीनचा आजचा दर काय आहे?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(100))
                .andExpect(jsonPath("$.message").value("सांगलीत सोयाबीनचा चालू दर ₹४२,००० प्रति टन आहे."));

        verify(ragService).retrieveContext(eq("सोयाबीनचा आजचा दर काय आहे?"), eq("CURRENT_RATE"));
    }

    // =========================================================================
    // 6. HINDI QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("6. Hindi question processes correctly and detects intent")
    void test06_HindiQuestion_ProcessedSuccessfully() throws Exception {
        when(geminiService.generateResponse(anyString(), anyString()))
                .thenReturn("सांगली में सोयाबीन का वर्तमान भाव ₹42,000 प्रति मीट्रिक टन है।");

        String requestJson = """
                {
                    "sessionId": 100,
                    "message": "सोयाबीन का वर्तमान रेट क्या है?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(100))
                .andExpect(jsonPath("$.message").value("सांगली में सोयाबीन का वर्तमान भाव ₹42,000 प्रति मीट्रिक टन है।"));

        verify(ragService).retrieveContext(eq("सोयाबीन का वर्तमान रेट क्या है?"), eq("CURRENT_RATE"));
    }

    // =========================================================================
    // 7. CURRENT MARKET-RATE QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("7. Current market-rate question maps to CURRENT_RATE and MARKETPLACE_DATA path")
    void test07_CurrentMarketRate_IntentAndPath() {
        String question = "Current cotton rate in Akola?";
        String intent = intentService.detectIntent(question);
        String path = intentService.determineRetrievalPath(question, intent);

        assertEquals(IntentService.INTENT_CURRENT_RATE, intent);
        assertEquals(IntentService.PATH_MARKETPLACE_DATA, path);
    }

    // =========================================================================
    // 8. RATE-HISTORY / RATE-TREND QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("8. Rate history question maps to RATE_TREND and MARKETPLACE_DATA path")
    void test08_RateTrend_IntentAndPath() {
        String question = "What is the 7-day rate trend for maize?";
        String intent = intentService.detectIntent(question);
        String path = intentService.determineRetrievalPath(question, intent);

        assertEquals(IntentService.INTENT_RATE_TREND, intent);
        assertEquals(IntentService.PATH_MARKETPLACE_DATA, path);
    }

    // =========================================================================
    // 9. LISTING-RELATED QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("9. Listing query maps to TODAY_LISTINGS and MARKETPLACE_DATA path")
    void test09_Listings_IntentAndPath() {
        String question = "Show me available listings for wheat";
        String intent = intentService.detectIntent(question);
        String path = intentService.determineRetrievalPath(question, intent);

        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intent);
        assertEquals(IntentService.PATH_MARKETPLACE_DATA, path);
    }

    // =========================================================================
    // 10. BUYER-DEMAND QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("10. Buyer-demand query maps to TODAY_DEMAND and MARKETPLACE_DATA path")
    void test10_BuyerDemand_IntentAndPath() {
        String question = "What is the buyer demand for cotton today?";
        String intent = intentService.detectIntent(question);
        String path = intentService.determineRetrievalPath(question, intent);

        assertEquals(IntentService.INTENT_TODAY_DEMAND, intent);
        assertEquals(IntentService.PATH_MARKETPLACE_DATA, path);
    }

    // =========================================================================
    // 11. KNOWLEDGE / RAG QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("11. Commodity knowledge query maps to COMMODITY_INFORMATION and KNOWLEDGE path")
    void test11_KnowledgeRAG_IntentAndPath() {
        String question = "What is corn gluten feed and what are its specifications?";
        String intent = intentService.detectIntent(question);
        String path = intentService.determineRetrievalPath(question, intent);

        assertEquals(IntentService.INTENT_COMMODITY_INFORMATION, intent);
        assertEquals(IntentService.PATH_KNOWLEDGE, path);
    }

    // =========================================================================
    // 12. COMBINED MARKETPLACE + KNOWLEDGE QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("12. Combined marketplace + knowledge query maps to BOTH path")
    void test12_CombinedMarketplaceAndKnowledge_IntentAndPath() {
        String question = "What is the soybean rate in Sangli and explain soybean specifications?";
        String path = intentService.determineRetrievalPath(question);

        assertEquals(IntentService.PATH_BOTH, path);
    }

    // =========================================================================
    // 13. UNKNOWN OR UNSUPPORTED INTENT
    // =========================================================================
    @Test
    @DisplayName("13. Unknown intent maps to UNKNOWN and PATH_UNKNOWN without crashing")
    void test13_UnknownIntent_HandledGracefully() {
        String question = "Random unsupported phrase 999";
        String intent = intentService.detectIntent(question);
        String path = intentService.determineRetrievalPath(question, intent);

        assertEquals(IntentService.INTENT_UNKNOWN, intent);
        assertEquals(IntentService.PATH_UNKNOWN, path);
    }

    // =========================================================================
    // 14. EMPTY / BLANK MESSAGE VALIDATION
    // =========================================================================
    @Test
    @DisplayName("14. Blank message is rejected with HTTP 400 Bad Request by Bean Validation")
    void test14_BlankMessage_RejectedWith400() throws Exception {
        String requestJson = """
                {
                    "sessionId": 100,
                    "message": "   "
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 15. INVALID REQUEST / SESSION HANDLING
    // =========================================================================
    @Test
    @DisplayName("15. Unrecognized session ID creates a new session gracefully without failure")
    void test15_NonExistentSession_CreatesNewSession() throws Exception {
        when(chatSessionRepository.findById(9999L)).thenReturn(Optional.empty());

        String requestJson = """
                {
                    "sessionId": 9999,
                    "message": "Current rate of Tur?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(100));

        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    // =========================================================================
    // 16. EXISTING CHAT-SESSION HISTORY PERSISTENCE
    // =========================================================================
    @Test
    @DisplayName("16. ChatSession and ChatMessage are persisted to database repositories")
    void test16_ChatMessage_PersistedToRepository() throws Exception {
        String requestJson = """
                {
                    "sessionId": 100,
                    "message": "Current rate of Maize?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(messageCaptor.capture());

        List<ChatMessage> savedMessages = messageCaptor.getAllValues();
        assertEquals(ChatMessage.ROLE_ADMIN, savedMessages.get(0).getRole());
        assertEquals("Current rate of Maize?", savedMessages.get(0).getContent());
        assertEquals(ChatMessage.ROLE_ASSISTANT, savedMessages.get(1).getRole());
    }

    // =========================================================================
    // 17. MULTI-TURN CONVERSATION CONTEXT
    // =========================================================================
    @Test
    @DisplayName("17. Multi-turn conversation context retrieves prior messages and passes to Gemini")
    void test17_MultiTurnContext_PassedToGemini() throws Exception {
        List<ChatMessage> mockHistory = List.of(
                new ChatMessage(testSession, ChatMessage.ROLE_ADMIN, "Soybean current rate?"),
                new ChatMessage(testSession, ChatMessage.ROLE_ASSISTANT, "Current rate is 42000.")
        );
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(100L)).thenReturn(mockHistory);

        String requestJson = """
                {
                    "sessionId": 100,
                    "message": "What about yesterday?"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(geminiService).generateResponse(eq("What about yesterday?"), any(), eq(mockHistory));
    }

    // =========================================================================
    // 18. MARKETPLACE HTTP/JSON INTEGRATION
    // =========================================================================
    @Test
    @DisplayName("18. MarketplaceDataService returns empty list without inventing fake data when unconfigured")
    void test18_MarketplaceDataService_NoFakeData() {
        MarketplaceDataService unconfiguredService = new MarketplaceDataService(null, "", "", "");
        assertFalse(unconfiguredService.isConfigured());
        List<MarketplaceRateDto> rates = unconfiguredService.fetchMarketRates();
        assertNotNull(rates);
        assertTrue(rates.isEmpty(), "Must not invent fake live rates");
    }

    // =========================================================================
    // 19. RAG / PGVECTOR RETRIEVAL PATH
    // =========================================================================
    @Test
    @DisplayName("19. RagService resolves correct filter expressions for KNOWLEDGE, MARKETPLACE_DATA, and BOTH")
    void test19_RagFilterExpressions() {
        RagService realRagService = new RagService(null, intentService, 4);
        assertEquals("type == 'KNOWLEDGE'", realRagService.resolveFilterExpression(IntentService.PATH_KNOWLEDGE));
        assertEquals("type == 'MARKETPLACE_DATA'", realRagService.resolveFilterExpression(IntentService.PATH_MARKETPLACE_DATA));
        assertNull(realRagService.resolveFilterExpression(IntentService.PATH_BOTH));
        assertNull(realRagService.resolveFilterExpression(IntentService.PATH_UNKNOWN));
    }

    // =========================================================================
    // 20. GEMINI RECEIVES ONLY CONTROLLED QUESTION / HISTORY / CONTEXT
    // =========================================================================
    @Test
    @DisplayName("20. Gemini Prompt structure contains strict read-only constraints and context delimiters")
    void test20_GeminiPromptStructure() {
        GeminiService liveService = new GeminiService(null, "gemini-2.5-flash");
        Prompt prompt = liveService.buildPrompt("XYZ rate?", "Soybean rate: 42000");

        String content = prompt.getContents();
        assertTrue(content.contains("=== CONTROLLED CONTEXT START ==="));
        assertTrue(content.contains("=== CONTROLLED CONTEXT END ==="));
        assertTrue(content.contains("READ-ONLY MODE (Phase 1)"));
        assertTrue(content.contains("NO DIRECT DATABASE ACCESS"));
        assertTrue(content.contains("Please provide an accurate, read-only administrative response"));
    }

    // =========================================================================
    // 21. READ-ONLY PROTECTION REMAINS ENFORCED
    // =========================================================================
    @Test
    @DisplayName("21. Write attempt is rejected with Phase 1 read-only notice without calling Gemini")
    void test21_WriteAttempt_RejectedWithoutCallingGemini() throws Exception {
        String requestJson = """
                {
                    "sessionId": 100,
                    "message": "Create a new rate of 55000 for Soybean"
                }
                """;

        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("strictly READ-ONLY in Phase 1")));

        verify(geminiService, never()).generateResponse(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString(), anyList());
    }

    // =========================================================================
    // 22. NO DIRECT MARKETPLACE POSTGRESQL ACCESS
    // =========================================================================
    @Test
    @DisplayName("22. Verify no Marketplace PostgreSQL entities or repositories exist in Chatbot project")
    void test22_NoMarketplacePostgresEntities() {
        String[] forbiddenClasses = {
                "com.example.Commodity_Admin_AI.entity.MarketRate",
                "com.example.Commodity_Admin_AI.entity.Listing",
                "com.example.Commodity_Admin_AI.entity.BuyerDemand",
                "com.example.Commodity_Admin_AI.repository.MarketRateRepository"
        };
        for (String fqcn : forbiddenClasses) {
            try {
                Class.forName(fqcn);
                org.junit.jupiter.api.Assertions.fail("Forbidden marketplace class found: " + fqcn);
            } catch (ClassNotFoundException expected) {
                assertTrue(true);
            }
        }
    }

    // =========================================================================
    // 23. FUTURE PHASE 2 WRITE OPERATIONS REMAIN UNAVAILABLE
    // =========================================================================
    @Test
    @DisplayName("23. Bids, negotiations, deals, and contracts are unavailable in Phase 1 and rejected")
    void test23_FuturePhase2Features_Rejected() throws Exception {
        String[] futureQueries = {
                "Place bid of 45000",
                "Negotiate deal with Supplier",
                "Sign contract #10"
        };

        for (String query : futureQueries) {
            ChatRequest request = new ChatRequest(100L, query);
            ChatResponse response = adminChatbotService.processChat(request);
            assertTrue(response.getMessage().contains("READ-ONLY"),
                    "Expected read-only notice for future query: " + query);
        }

        assertEquals("BIDS", FutureMarketplaceArchitecture.FEATURE_BIDS);
        assertEquals("DEALS", FutureMarketplaceArchitecture.FEATURE_DEALS);
        assertEquals("CONTRACTS", FutureMarketplaceArchitecture.FEATURE_CONTRACTS);
    }

    // =========================================================================
    // 24. GREETINGS HANDLED WITH CONTROLLED RESPONSE
    // =========================================================================
    @Test
    @DisplayName("24. Greetings return controlled response without calling RAG, PGVector, or Gemini")
    void test24_GreetingsHandledWithControlledResponse() throws Exception {
        String[] greetings = {"Hello", "Hi", "How are you?"};

        for (String greeting : greetings) {
            String requestJson = String.format("""
                    {
                        "sessionId": 100,
                        "message": "%s"
                    }
                    """, greeting);

            mockMvc.perform(post("/api/admin/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value(100))
                    .andExpect(jsonPath("$.message").value(IntentService.CONTROLLED_HELP_RESPONSE));
        }

        verify(ragService, never()).retrieveContext(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString(), anyList());
    }

    // =========================================================================
    // 25. OUT-OF-SCOPE HANDLED WITH CONTROLLED RESPONSE
    // =========================================================================
    @Test
    @DisplayName("25. Out-of-scope questions return controlled response without calling RAG or Gemini")
    void test25_OutOfScopeHandledWithControlledResponse() throws Exception {
        String[] outOfScopeQueries = {
                "What is the weather today?",
                "Tell me a joke.",
                "Who won yesterday's cricket match?"
        };

        for (String query : outOfScopeQueries) {
            String requestJson = String.format("""
                    {
                        "sessionId": 100,
                        "message": "%s"
                    }
                    """, query);

            mockMvc.perform(post("/api/admin/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value(100))
                    .andExpect(jsonPath("$.message").value(IntentService.CONTROLLED_HELP_RESPONSE));
        }

        verify(ragService, never()).retrieveContext(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString());
        verify(geminiService, never()).generateResponse(anyString(), anyString(), anyList());
    }

    // =========================================================================
    // 26. CORS PREFLIGHT & ALLOWED ORIGIN FOR FRONTEND (PORT 3000)
    // =========================================================================
    @Test
    @DisplayName("26. CORS preflight OPTIONS request from http://localhost:3000 returns 200 with Access-Control-Allow-Origin")
    void test26_CorsPreflightFromPort3000Permitted() throws Exception {
        mockMvc.perform(options("/api/admin/chat")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Accept"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
