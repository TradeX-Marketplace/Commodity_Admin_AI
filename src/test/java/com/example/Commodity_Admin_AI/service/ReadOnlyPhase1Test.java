package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.controller.AdminChatbotController;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STEP 16 — READ-ONLY PHASE 1 VERIFICATION TEST SUITE
 *
 * Verifies that the Admin AI Chatbot:
 * 1. Performs allowed read and retrieval operations.
 * 2. Rejects all write and mutation attempts (rates, listings, demands, users, deals, contracts).
 * 3. Exposes NO write endpoints or write operations.
 * 4. Ensures Gemini receives strictly controlled question, history, and context.
 * 5. Contains NO direct Marketplace PostgreSQL access or entities.
 */
@ExtendWith(MockitoExtension.class)
class ReadOnlyPhase1Test {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RagService ragService;

    @Mock
    private GeminiService geminiService;

    private IntentService intentService;
    private AdminChatbotService adminChatbotService;
    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        intentService = new IntentService();
        adminChatbotService = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                10
        );

        testSession = new ChatSession("admin");
        testSession.setId(500L);
    }

    // =========================================================================
    // 1. ALLOWED READ/RETRIEVAL OPERATIONS
    // =========================================================================

    @Test
    @DisplayName("1. Chatbot successfully processes allowed read operation (CURRENT_RATE)")
    void allowedReadOperation_CurrentRate() {
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(testSession));
        when(ragService.retrieveContext(eq("What is the current soybean rate in Sangli?"), eq("CURRENT_RATE")))
                .thenReturn("Soybean rate in Sangli is 42000 per MT on 2026-09-26");
        when(geminiService.generateResponse(any(), any())).thenReturn("The current soybean rate in Sangli is ₹42,000 per MT.");

        ChatRequest request = new ChatRequest(500L, "What is the current soybean rate in Sangli?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(500L, response.getSessionId());
        assertEquals("The current soybean rate in Sangli is ₹42,000 per MT.", response.getMessage());

        verify(ragService).retrieveContext(eq("What is the current soybean rate in Sangli?"), eq("CURRENT_RATE"));
        verify(geminiService).generateResponse(eq("What is the current soybean rate in Sangli?"), any());
    }

    @Test
    @DisplayName("1b. Chatbot successfully processes allowed read operation (COMMODITY_INFORMATION)")
    void allowedReadOperation_CommodityInfo() {
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(testSession));
        when(ragService.retrieveContext(eq("What is corn gluten feed?"), eq("COMMODITY_INFORMATION")))
                .thenReturn("Corn gluten feed is a byproduct of corn processing used in animal feeds.");
        when(geminiService.generateResponse(any(), any())).thenReturn("Corn gluten feed is a high-protein feed ingredient.");

        ChatRequest request = new ChatRequest(500L, "What is corn gluten feed?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("Corn gluten feed is a high-protein feed ingredient.", response.getMessage());
    }

    @Test
    @DisplayName("1c. Chatbot successfully processes allowed read operation (RATE_TREND with history)")
    void allowedReadOperation_RateTrendWithHistory() {
        List<ChatMessage> mockHistory = List.of(
                new ChatMessage(testSession, ChatMessage.ROLE_ADMIN, "Soybean current rate?"),
                new ChatMessage(testSession, ChatMessage.ROLE_ASSISTANT, "Current rate is 42000.")
        );
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(testSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(500L)).thenReturn(mockHistory);
        when(ragService.retrieveContext(eq("What about yesterday?"), eq("RATE_TREND")))
                .thenReturn("Yesterday soybean rate was 41800.");
        when(geminiService.generateResponse(eq("What about yesterday?"), any(), any()))
                .thenReturn("Yesterday's rate was ₹41,800 per MT.");

        ChatRequest request = new ChatRequest(500L, "What about yesterday?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals("Yesterday's rate was ₹41,800 per MT.", response.getMessage());
        verify(geminiService).generateResponse(eq("What about yesterday?"), any(), any());
    }

    // =========================================================================
    // 2. REJECTION OF WRITE / MUTATION OPERATIONS
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "Create a new rate of 50000 for Soybean",
            "Update rate for Maize to 24000",
            "Delete rate for Tur",
            "Create listing for 500 MT Wheat",
            "Update listing 12 with new quantity",
            "Delete listing 45",
            "Create demand for 200 MT Cotton",
            "Update buyer demand 7",
            "Delete buyer demand 10",
            "Create user JohnDoe",
            "Delete user 101",
            "Modify ownership of commodity batch 8",
            "Create deal with Buyer ABC",
            "Place bid of 45000",
            "Sign contract for shipment 9"
    })
    @DisplayName("2. English write/mutation attempts are rejected in Phase 1 read-only mode")
    void writeOperations_English_Rejected(String writeQuery) {
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(testSession));

        ChatRequest request = new ChatRequest(500L, writeQuery);
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("strictly READ-ONLY in Phase 1"),
                "Expected read-only rejection notice for: " + writeQuery);

        // Gemini and RAG must NOT be invoked for write attempts
        verify(ragService, never()).retrieveContext(any(), any());
        verify(geminiService, never()).generateResponse(any(), any());
        verify(geminiService, never()).generateResponse(any(), any(), any());
    }

    @Test
    @DisplayName("2b. Marathi write/mutation attempts are rejected with localized notice")
    void writeOperations_Marathi_Rejected() {
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(testSession));

        ChatRequest request = new ChatRequest(500L, "सोयाबीनचा नवीन दर तयार करा");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("READ-ONLY"),
                "Expected read-only notice in Marathi response");
        assertTrue(response.getMessage().contains("फक्त वाचनासाठी") || response.getMessage().contains("बदलण्याची अनुमती नाही"));

        verify(geminiService, never()).generateResponse(any(), any());
    }

    @Test
    @DisplayName("2c. Hindi write/mutation attempts are rejected with localized notice")
    void writeOperations_Hindi_Rejected() {
        when(chatSessionRepository.findById(500L)).thenReturn(Optional.of(testSession));

        ChatRequest request = new ChatRequest(500L, "मार्केट रेट अपडेट करें");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("READ-ONLY"),
                "Expected read-only notice in Hindi response");
        assertTrue(response.getMessage().contains("केवल पढ़ने के लिए") || response.getMessage().contains("अनुमति नहीं है"));

        verify(geminiService, never()).generateResponse(any(), any());
    }

    // =========================================================================
    // 3. NO MARKETPLACE WRITE ENDPOINTS EXPOSED BY CHATBOT
    // =========================================================================

    @Test
    @DisplayName("3. Verify AdminChatbotController exposes NO write endpoints (PUT, DELETE, PATCH)")
    void verifyController_NoWriteEndpoints() {
        Method[] methods = AdminChatbotController.class.getDeclaredMethods();
        for (Method method : methods) {
            assertFalse(method.isAnnotationPresent(PutMapping.class),
                    "AdminChatbotController must not expose PUT endpoints: " + method.getName());
            assertFalse(method.isAnnotationPresent(DeleteMapping.class),
                    "AdminChatbotController must not expose DELETE endpoints: " + method.getName());
            assertFalse(method.isAnnotationPresent(PatchMapping.class),
                    "AdminChatbotController must not expose PATCH endpoints: " + method.getName());
        }
    }

    @Test
    @DisplayName("3b. Verify only POST /api/admin/chat exists on AdminChatbotController")
    void verifyController_OnlyChatEndpoint() {
        Method[] methods = AdminChatbotController.class.getDeclaredMethods();
        assertEquals(1, methods.length, "AdminChatbotController should only expose the chat method");
        assertEquals("chat", methods[0].getName());
    }

    // =========================================================================
    // 4. GEMINI RECEIVES ONLY CONTROLLED QUESTION / HISTORY / CONTEXT
    // =========================================================================

    @Test
    @DisplayName("4. GeminiService prompt contains strict read-only boundary and controlled context delimiters")
    void geminiPrompt_ContainsReadOnlyBoundaries() {
        GeminiService liveService = new GeminiService(null, "gemini-2.5-flash");

        Prompt prompt = liveService.buildPrompt(
                "What is the soybean rate?",
                "Soybean rate in Sangli is 42000"
        );

        assertNotNull(prompt);
        String promptString = prompt.getContents();

        // System prompt contains strict read-only instruction
        assertTrue(promptString.contains("READ-ONLY MODE (Phase 1)"),
                "Prompt must contain READ-ONLY instruction");
        assertTrue(promptString.contains("You CANNOT create, update, delete, or modify users, listings, market rates, buyer demands, deals, or contracts."),
                "Prompt must instruct model not to modify marketplace data");
        assertTrue(promptString.contains("NO DIRECT DATABASE ACCESS"),
                "Prompt must explicitly forbid direct database access");

        // Context is enclosed in controlled delimiters
        assertTrue(promptString.contains("=== CONTROLLED CONTEXT START ==="));
        assertTrue(promptString.contains("=== CONTROLLED CONTEXT END ==="));
        assertTrue(promptString.contains("Soybean rate in Sangli is 42000"));
        assertTrue(promptString.contains("Please provide an accurate, read-only administrative response based strictly on the above context."));
    }

    // =========================================================================
    // 5. NO DIRECT MARKETPLACE POSTGRESQL ACCESS OR ENTITIES
    // =========================================================================

    @Test
    @DisplayName("5. Verify only Chatbot entities exist (ChatSession and ChatMessage) and no Marketplace tables")
    void verifyEntities_NoMarketplaceTables() {
        Package entityPackage = ChatSession.class.getPackage();
        assertNotNull(entityPackage);

        // List entity classes in the package
        List<Class<?>> knownEntities = List.of(ChatSession.class, ChatMessage.class);
        for (Class<?> entityClass : knownEntities) {
            assertTrue(entityClass.isAnnotationPresent(Entity.class));
        }

        // Verify no marketplace tables exist in entity package
        String[] forbiddenEntityNames = {
                "User", "MarketRate", "Listing", "BuyerDemand", "Commodity",
                "Deal", "Contract", "Bidding", "Negotiation", "Organization"
        };
        for (String forbidden : forbiddenEntityNames) {
            try {
                Class.forName("com.example.Commodity_Admin_AI.entity." + forbidden);
                org.junit.jupiter.api.Assertions.fail("Forbidden marketplace entity found in Chatbot project: " + forbidden);
            } catch (ClassNotFoundException expected) {
                // Expected: Chatbot project does NOT contain Marketplace operational entities
                assertTrue(true);
            }
        }
    }

    @Test
    @DisplayName("5b. Verify only Chatbot repositories exist (ChatSessionRepository and ChatMessageRepository)")
    void verifyRepositories_NoMarketplaceRepositories() {
        String[] forbiddenRepoNames = {
                "UserRepository", "MarketRateRepository", "ListingRepository",
                "BuyerDemandRepository", "CommodityRepository", "DealRepository", "ContractRepository"
        };
        for (String forbidden : forbiddenRepoNames) {
            try {
                Class.forName("com.example.Commodity_Admin_AI.repository." + forbidden);
                org.junit.jupiter.api.Assertions.fail("Forbidden marketplace repository found in Chatbot project: " + forbidden);
            } catch (ClassNotFoundException expected) {
                // Expected
                assertTrue(true);
            }
        }
    }

}
