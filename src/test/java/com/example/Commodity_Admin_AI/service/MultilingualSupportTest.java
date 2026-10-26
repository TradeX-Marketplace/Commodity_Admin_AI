package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STEP 19 — MULTILINGUAL SUPPORT TEST SUITE
 *
 * Verifies that the Admin AI Chatbot:
 * 1. English question → English response instruction.
 * 2. Marathi question → Marathi response instruction.
 * 3. Hindi question → Hindi response instruction.
 * 4. Language detection does not break intent routing.
 * 5. RAG context works with multilingual questions.
 * 6. Multi-turn history remains supported across multilingual turns.
 * 7. Read-only restrictions remain enforced in English, Marathi, and Hindi.
 * 8. ChatResponse reflects language metadata and preserves existing flow.
 * 9. IntentService and GeminiService language detection parity.
 */
@ExtendWith(MockitoExtension.class)
class MultilingualSupportTest {

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
        testSession.setId(600L);
    }

    // =========================================================================
    // 1. ENGLISH QUESTION → ENGLISH RESPONSE INSTRUCTION
    // =========================================================================
    @Test
    @DisplayName("1. English question builds Prompt with explicit English response instruction")
    void test1_EnglishQuestion_EnglishResponseInstruction() {
        GeminiService liveService = new GeminiService(null, "gemini-2.5-flash");
        String question = "What is the current soybean rate in Sangli?";
        String context = "Marketplace rate: Soybean in Sangli is 42000 per MT.";

        Prompt prompt = liveService.buildPrompt(question, context);
        assertNotNull(prompt);
        String promptUserText = prompt.getInstructions().get(1).getText();

        assertTrue(promptUserText.contains("Language Instruction: The admin asked the question in English. You MUST respond strictly in English."));
        assertTrue(promptUserText.contains("Please provide an accurate, read-only administrative response based strictly on the above context."));
        assertTrue(promptUserText.contains("Admin Question: " + question));
    }

    // =========================================================================
    // 2. MARATHI QUESTION → MARATHI RESPONSE INSTRUCTION
    // =========================================================================
    @Test
    @DisplayName("2. Marathi question builds Prompt with explicit Marathi response instruction")
    void test2_MarathiQuestion_MarathiResponseInstruction() {
        GeminiService liveService = new GeminiService(null, "gemini-2.5-flash");
        String question = "सोयाबीनचा आजचा दर काय आहे?";
        String context = "सांगली बाजारपेठ दर: सोयाबीन ₹४२,००० प्रति टन.";

        Prompt prompt = liveService.buildPrompt(question, context);
        assertNotNull(prompt);
        String promptUserText = prompt.getInstructions().get(1).getText();

        assertTrue(promptUserText.contains("Language Instruction: The admin asked the question in Marathi (मराठी). You MUST respond strictly in Marathi (मराठीत उत्तर द्या)."));
        assertTrue(promptUserText.contains("Please provide an accurate, read-only administrative response based strictly on the above context."));
        assertTrue(promptUserText.contains("Admin Question: " + question));
    }

    // =========================================================================
    // 3. HINDI QUESTION → HINDI RESPONSE INSTRUCTION
    // =========================================================================
    @Test
    @DisplayName("3. Hindi question builds Prompt with explicit Hindi response instruction")
    void test3_HindiQuestion_HindiResponseInstruction() {
        GeminiService liveService = new GeminiService(null, "gemini-2.5-flash");
        String question = "सोयाबीन का आज का भाव क्या है?";
        String context = "सांगली मंडी भाव: सोयाबीन ₹४२,००० प्रति टन.";

        Prompt prompt = liveService.buildPrompt(question, context);
        assertNotNull(prompt);
        String promptUserText = prompt.getInstructions().get(1).getText();

        assertTrue(promptUserText.contains("Language Instruction: The admin asked the question in Hindi (हिंदी). You MUST respond strictly in Hindi (हिंदी में उत्तर दें)."));
        assertTrue(promptUserText.contains("Please provide an accurate, read-only administrative response based strictly on the above context."));
        assertTrue(promptUserText.contains("Admin Question: " + question));
    }

    // =========================================================================
    // 4. LANGUAGE DETECTION DOES NOT BREAK INTENT ROUTING
    // =========================================================================
    @ParameterizedTest
    @CsvSource(value = {
            "What is the current soybean rate in Sangli? | CURRENT_RATE | English",
            "सोयाबीनचा आजचा दर काय आहे? | CURRENT_RATE | Marathi",
            "सोयाबीन का आज का भाव क्या है? | CURRENT_RATE | Hindi",
            "Show rate trends for cotton | RATE_TREND | English",
            "XYZ चा 7 दिवसांचा trend दाखवा | RATE_TREND | Marathi",
            "सोयाबीन का 7 दिनों का ट्रेंड बताओ | RATE_TREND | Hindi",
            "Show me today listings for maize | TODAY_LISTINGS | English",
            "आज उपलब्ध माल किती आहे? | TODAY_LISTINGS | Marathi",
            "आज की listings क्या हैं? | TODAY_LISTINGS | Hindi",
            "What is the buyer demand for wheat? | TODAY_DEMAND | English",
            "आज XYZ ची मागणी किती आहे? | TODAY_DEMAND | Marathi",
            "आज सोयाबीन की मांग कितनी है? | TODAY_DEMAND | Hindi",
            "Explain standard specifications of soybean | COMMODITY_INFORMATION | English",
            "हळद म्हणजे काय? | COMMODITY_INFORMATION | Marathi",
            "मक्का के बारे में जानकारी बताओ | COMMODITY_INFORMATION | Hindi",
            "Show notification status | NOTIFICATION_STATUS | English",
            "नव्या सूचना दाखवा | NOTIFICATION_STATUS | Marathi",
            "Check whatsapp status | WHATSAPP_STATUS | English",
            "व्हॉट्सॲप स्टेटस काय आहे? | WHATSAPP_STATUS | Marathi"
    }, delimiter = '|')
    @DisplayName("4. Multilingual questions preserve accurate intent classification and language detection")
    void test4_MultilingualIntentRoutingAndLanguage(String query, String expectedIntent, String expectedLang) {
        String cleanQuery = query.trim();
        assertEquals(expectedIntent.trim(), intentService.detectIntent(cleanQuery));
        assertEquals(expectedLang.trim(), GeminiService.detectLanguage(cleanQuery));
        assertEquals(expectedLang.trim(), intentService.detectLanguage(cleanQuery));
    }

    // =========================================================================
    // 5. RAG CONTEXT WORKS WITH MULTILINGUAL QUESTIONS
    // =========================================================================
    @Test
    @DisplayName("5. RAG retrieval path resolves correctly for English, Marathi, and Hindi questions")
    void test5_RagRetrievalPaths_Multilingual() {
        // English
        assertEquals(IntentService.PATH_MARKETPLACE_DATA,
                intentService.determineRetrievalPath("Current price of soybean in Sangli?"));
        assertEquals(IntentService.PATH_KNOWLEDGE,
                intentService.determineRetrievalPath("What is corn gluten feed?"));

        // Marathi
        assertEquals(IntentService.PATH_MARKETPLACE_DATA,
                intentService.determineRetrievalPath("सोयाबीनचा आजचा दर काय आहे?"));
        assertEquals(IntentService.PATH_KNOWLEDGE,
                intentService.determineRetrievalPath("सोयाबीन बद्दल सांगा"));

        // Hindi
        assertEquals(IntentService.PATH_MARKETPLACE_DATA,
                intentService.determineRetrievalPath("सोयाबीन का आज का भाव क्या है?"));
        assertEquals(IntentService.PATH_KNOWLEDGE,
                intentService.determineRetrievalPath("मक्का के बारे में जानकारी"));

        // Combined hybrid
        assertEquals(IntentService.PATH_BOTH,
                intentService.determineRetrievalPath("What is the current soybean rate in Sangli and explain soybean?"));
        assertEquals(IntentService.PATH_BOTH,
                intentService.determineRetrievalPath("सोयाबीनचा चालू दर किती आहे आणि सोयाबीन बद्दल सांगा"));
    }

    // =========================================================================
    // 6. MULTI-TURN HISTORY REMAINS SUPPORTED
    // =========================================================================
    @Test
    @DisplayName("6. Multi-turn conversation history is preserved across multilingual questions")
    void test6_MultiTurnHistory_Multilingual() {
        when(chatSessionRepository.findById(600L)).thenReturn(Optional.of(testSession));

        // Prior turn in Marathi
        ChatMessage turn1Admin = new ChatMessage(testSession, ChatMessage.ROLE_ADMIN, "सोयाबीनचा आजचा दर काय आहे?");
        ChatMessage turn1Bot = new ChatMessage(testSession, ChatMessage.ROLE_ASSISTANT, "सांगलीमध्ये सोयाबीनचा दर ₹४२,००० आहे.");
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(600L))
                .thenReturn(List.of(turn1Admin, turn1Bot));

        when(ragService.retrieveContext(anyString(), anyString())).thenReturn("Context");
        when(geminiService.generateResponse(anyString(), anyString(), any())).thenReturn("नागपूरमध्ये दर ₹४१,५०० आहे.");

        // Follow up in Marathi
        ChatRequest request = new ChatRequest(600L, "आणि नागपूरमध्ये किती आहे?");
        ChatResponse response = adminChatbotService.processChat(request);

        assertNotNull(response);
        assertEquals(GeminiService.LANGUAGE_MARATHI, response.getLanguage());

        // Verify history was fetched and passed to Gemini
        ArgumentCaptor<List<ChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiService).generateResponse(eq("आणि नागपूरमध्ये किती आहे?"), eq("Context"), historyCaptor.capture());

        List<ChatMessage> capturedHistory = historyCaptor.getValue();
        assertEquals(2, capturedHistory.size());
        assertEquals("सोयाबीनचा आजचा दर काय आहे?", capturedHistory.get(0).getContent());
        assertEquals("सांगलीमध्ये सोयाबीनचा दर ₹४२,००० आहे.", capturedHistory.get(1).getContent());
    }

    // =========================================================================
    // 7. READ-ONLY RESTRICTIONS REMAIN ENFORCED IN ALL THREE LANGUAGES
    // =========================================================================
    @Test
    @DisplayName("7. Read-only restrictions remain enforced with localized notices in EN, MR, HI")
    void test7_ReadOnlyRestrictions_Multilingual() {
        when(chatSessionRepository.findById(600L)).thenReturn(Optional.of(testSession));

        // English write attempt
        ChatResponse enResp = adminChatbotService.processChat(new ChatRequest(600L, "Create a new rate of 55000 for Soybean"));
        assertTrue(enResp.getMessage().contains("strictly READ-ONLY in Phase 1"));
        assertEquals(GeminiService.LANGUAGE_ENGLISH, enResp.getLanguage());

        // Marathi write attempt
        ChatResponse mrResp = adminChatbotService.processChat(new ChatRequest(600L, "सोयाबीनचा नवीन दर तयार करा"));
        assertTrue(mrResp.getMessage().contains("READ-ONLY"));
        assertTrue(mrResp.getMessage().contains("फक्त वाचनासाठी") || mrResp.getMessage().contains("बदलण्याची अनुमती नाही"));
        assertEquals(GeminiService.LANGUAGE_MARATHI, mrResp.getLanguage());

        // Hindi write attempt
        ChatResponse hiResp = adminChatbotService.processChat(new ChatRequest(600L, "मार्केट रेट अपडेट करें"));
        assertTrue(hiResp.getMessage().contains("READ-ONLY"));
        assertTrue(hiResp.getMessage().contains("केवल पढ़ने के लिए") || hiResp.getMessage().contains("बदलाव करने की अनुमति नहीं"));
        assertEquals(GeminiService.LANGUAGE_HINDI, hiResp.getLanguage());

        // Gemini must never be invoked for write attempts
        verify(geminiService, never()).generateResponse(any(), any());
        verify(geminiService, never()).generateResponse(any(), any(), any());
    }

    // =========================================================================
    // 8. CHAT RESPONSE METADATA & PARITY
    // =========================================================================
    @Test
    @DisplayName("8. ChatResponse reflects language metadata for regular responses")
    void test8_ChatResponse_LanguageMetadata() {
        when(chatSessionRepository.findById(600L)).thenReturn(Optional.of(testSession));
        when(ragService.retrieveContext(anyString(), anyString())).thenReturn("Live context");
        when(geminiService.generateResponse(anyString(), anyString())).thenReturn("Soybean is ₹42,000.");

        ChatResponse response = adminChatbotService.processChat(new ChatRequest(600L, "What is the soybean rate?"));
        assertNotNull(response);
        assertEquals(600L, response.getSessionId());
        assertEquals("Soybean is ₹42,000.", response.getMessage());
        assertEquals(GeminiService.LANGUAGE_ENGLISH, response.getLanguage());
    }
}
