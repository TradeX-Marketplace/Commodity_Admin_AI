package com.example.Commodity_Admin_AI.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiService(chatModel, "gemini-2.5-flash");
    }

    @Test
    @DisplayName("Should initialize properly and provide model name")
    void testInitialization() {
        assertEquals("gemini-2.5-flash", geminiService.getModelName());
        assertEquals(chatModel, geminiService.getChatModel());
    }

    @Test
    @DisplayName("Should successfully call ChatModel and return generated response text")
    void testGenerateResponse_Success() {
        String question = "What is the current soybean rate in Sangli?";
        String context = "Soybean rate in Sangli: ₹42,000 per MT on 2026-09-26.";
        String expectedAnswer = "The current soybean rate in Sangli is ₹42,000 per MT.";

        AssistantMessage assistantMessage = new AssistantMessage(expectedAnswer);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        String result = geminiService.generateResponse(question, context);

        assertEquals(expectedAnswer, result);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());

        Prompt capturedPrompt = promptCaptor.getValue();
        List<Message> messages = capturedPrompt.getInstructions();
        assertEquals(2, messages.size());

        // Verify System Message
        assertEquals(MessageType.SYSTEM, messages.get(0).getMessageType());
        assertTrue(messages.get(0).getText().contains("Admin AI Assistant"));
        assertTrue(messages.get(0).getText().contains("READ-ONLY MODE"));
        assertTrue(messages.get(0).getText().contains("CONTROLLED CONTEXT"));

        // Verify User Message
        assertEquals(MessageType.USER, messages.get(1).getMessageType());
        assertTrue(messages.get(1).getText().contains("=== CONTROLLED CONTEXT START ==="));
        assertTrue(messages.get(1).getText().contains(context));
        assertTrue(messages.get(1).getText().contains("=== CONTROLLED CONTEXT END ==="));
        assertTrue(messages.get(1).getText().contains("Admin Question: " + question));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("Should return validation error when question is empty or blank")
    void testGenerateResponse_BlankQuestion(String question) {
        String result = geminiService.generateResponse(question, "some context");

        assertEquals("Please provide a valid administrative question.", result);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should return fallback message when ChatModel is unconfigured or null")
    void testGenerateResponse_NullChatModel() {
        GeminiService unconfiguredService = new GeminiService(null);
        assertNull(unconfiguredService.getChatModel());

        String result = unconfiguredService.generateResponse("Current maize rate?", "Maize context");

        assertTrue(result.contains("Gemini AI model is not configured"));
    }

    @Test
    @DisplayName("Should catch and handle exception when ChatModel throws error")
    void testGenerateResponse_ChatModelException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("API rate limit exceeded"));

        String result = geminiService.generateResponse("Current wheat rate?", "Wheat context");

        assertNotNull(result);
        assertTrue(result.contains("An error occurred while communicating with the AI service"));
        assertTrue(result.contains("API rate limit exceeded"));
    }

    @Test
    @DisplayName("Should handle empty or null ChatResponse gracefully")
    void testGenerateResponse_EmptyResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        String result = geminiService.generateResponse("Current cotton rate?", "Cotton context");

        assertEquals("No response could be generated for the question.", result);
    }

    @Test
    @DisplayName("Should build prompt with multilingual guidelines and proper context delimiters")
    void testBuildPrompt_MultilingualAndDelimiters() {
        String question = "सांगलीमध्ये सोयाबीनचा आजचा दर काय आहे?";
        String context = "सांगली बाजारपेठ दर: सोयाबीन ₹४२,००० प्रति टन.";

        Prompt prompt = geminiService.buildPrompt(question, context);

        assertNotNull(prompt);
        List<Message> messages = prompt.getInstructions();
        assertEquals(2, messages.size());

        String systemText = messages.get(0).getText();
        assertTrue(systemText.contains("English"));
        assertTrue(systemText.contains("Marathi"));
        assertTrue(systemText.contains("Hindi"));

        String userText = messages.get(1).getText();
        assertTrue(userText.contains(question));
        assertTrue(userText.contains(context));
    }

    @Test
    @DisplayName("Should indicate missing context when context is null or blank")
    void testBuildPrompt_NullOrEmptyContext() {
        Prompt promptNull = geminiService.buildPrompt("General question", null);
        String userTextNull = promptNull.getInstructions().get(1).getText();
        assertTrue(userTextNull.contains("[No additional context retrieved]"));

        Prompt promptBlank = geminiService.buildPrompt("General question", "   ");
        String userTextBlank = promptBlank.getInstructions().get(1).getText();
        assertTrue(userTextBlank.contains("[No additional context retrieved]"));
    }

    @Test
    @DisplayName("generateResponseWithAdvisor returns error when question is empty")
    void testGenerateResponseWithAdvisor_BlankQuestion() {
        String result = geminiService.generateResponseWithAdvisor("", null);
        assertEquals("Please provide a valid administrative question.", result);
    }

    @Test
    @DisplayName("generateResponseWithAdvisor returns fallback when chatModel is null")
    void testGenerateResponseWithAdvisor_NullChatModel() {
        GeminiService unconfigured = new GeminiService(null);
        String result = unconfigured.generateResponseWithAdvisor("Valid question", null);
        assertTrue(result.contains("Gemini AI model is not configured"));
    }

    @Test
    @DisplayName("generateResponseWithAdvisor successfully calls ChatClient with advisor")
    void testGenerateResponseWithAdvisor_Success() {
        String question = "What is the soybean rate?";
        String expectedAnswer = "Soybean rate is ₹42,000 per MT.";

        ChatOptions chatOptions = ChatOptions.builder().build();
        when(chatModel.getOptions()).thenReturn(chatOptions);

        AssistantMessage assistantMessage = new AssistantMessage(expectedAnswer);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        String result = geminiService.generateResponseWithAdvisor(question, null);
        assertEquals(expectedAnswer, result);
    }

    @Test
    @DisplayName("Should build prompt with conversation history preserving turn sequence and delimiters")
    void testBuildPrompt_WithConversationHistory() {
        ChatSession session = new ChatSession("admin");
        ChatMessage turn1Admin = new ChatMessage(session, ChatMessage.ROLE_ADMIN, "What is the soybean rate in Sangli?");
        ChatMessage turn1Assistant = new ChatMessage(session, ChatMessage.ROLE_ASSISTANT, "The soybean rate in Sangli is ₹42,000 per MT.");
        List<ChatMessage> history = List.of(turn1Admin, turn1Assistant);

        String currentQuestion = "What about in Nagpur?";
        String currentContext = "Nagpur soybean rate: ₹41,500 per MT.";

        Prompt prompt = geminiService.buildPrompt(currentQuestion, currentContext, history);

        assertNotNull(prompt);
        List<Message> messages = prompt.getInstructions();
        // 1 System + 2 History + 1 Current User
        assertEquals(4, messages.size());

        // Message 0: System Message
        assertEquals(MessageType.SYSTEM, messages.get(0).getMessageType());

        // Message 1: Turn 1 User Message
        assertEquals(MessageType.USER, messages.get(1).getMessageType());
        assertEquals("What is the soybean rate in Sangli?", messages.get(1).getText());

        // Message 2: Turn 1 Assistant Message
        assertEquals(MessageType.ASSISTANT, messages.get(2).getMessageType());
        assertEquals("The soybean rate in Sangli is ₹42,000 per MT.", messages.get(2).getText());

        // Message 3: Current Turn User Message with delimiters
        assertEquals(MessageType.USER, messages.get(3).getMessageType());
        assertTrue(messages.get(3).getText().contains("=== CONTROLLED CONTEXT START ==="));
        assertTrue(messages.get(3).getText().contains(currentContext));
        assertTrue(messages.get(3).getText().contains("=== CONTROLLED CONTEXT END ==="));
        assertTrue(messages.get(3).getText().contains("Admin Question: " + currentQuestion));
    }

    @Test
    @DisplayName("Should successfully call ChatModel with conversation history")
    void testGenerateResponse_WithConversationHistory() {
        ChatSession session = new ChatSession("admin");
        ChatMessage turn1Admin = new ChatMessage(session, ChatMessage.ROLE_ADMIN, "Soybean rate?");
        ChatMessage turn1Assistant = new ChatMessage(session, ChatMessage.ROLE_ASSISTANT, "₹42,000");
        List<ChatMessage> history = List.of(turn1Admin, turn1Assistant);

        String currentQuestion = "And wheat?";
        String currentContext = "Wheat rate: ₹24,000.";
        String expectedAnswer = "Wheat rate is ₹24,000.";

        AssistantMessage assistantMessage = new AssistantMessage(expectedAnswer);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        String result = geminiService.generateResponse(currentQuestion, currentContext, history);
        assertEquals(expectedAnswer, result);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertEquals(4, captor.getValue().getInstructions().size());
    }

    @Test
    @DisplayName("1. English question produces English response instruction in Prompt")
    void testBuildPrompt_EnglishResponseInstruction() {
        Prompt prompt = geminiService.buildPrompt("What is the current soybean rate in Sangli?", "Context data");
        String userText = prompt.getInstructions().get(1).getText();

        assertTrue(userText.contains("Language Instruction: The admin asked the question in English. You MUST respond strictly in English."));
        assertTrue(userText.contains("Please provide an accurate, read-only administrative response"));
    }

    @Test
    @DisplayName("2. Marathi question produces Marathi response instruction in Prompt")
    void testBuildPrompt_MarathiResponseInstruction() {
        Prompt prompt = geminiService.buildPrompt("सोयाबीनचा आजचा दर काय आहे?", "Context data");
        String userText = prompt.getInstructions().get(1).getText();

        assertTrue(userText.contains("Language Instruction: The admin asked the question in Marathi (मराठी). You MUST respond strictly in Marathi (मराठीत उत्तर द्या)."));
        assertTrue(userText.contains("Please provide an accurate, read-only administrative response"));
    }

    @Test
    @DisplayName("3. Hindi question produces Hindi response instruction in Prompt")
    void testBuildPrompt_HindiResponseInstruction() {
        Prompt prompt = geminiService.buildPrompt("सोयाबीन का आज का भाव क्या है?", "Context data");
        String userText = prompt.getInstructions().get(1).getText();

        assertTrue(userText.contains("Language Instruction: The admin asked the question in Hindi (हिंदी). You MUST respond strictly in Hindi (हिंदी में उत्तर दें)."));
        assertTrue(userText.contains("Please provide an accurate, read-only administrative response"));
    }

    @Test
    @DisplayName("detectLanguage correctly identifies English, Marathi, and Hindi")
    void testDetectLanguage_Multilingual() {
        assertEquals(GeminiService.LANGUAGE_ENGLISH, GeminiService.detectLanguage("What is the current soybean rate in Sangli?"));
        assertEquals(GeminiService.LANGUAGE_ENGLISH, GeminiService.detectLanguage("Show me today listings"));
        assertEquals(GeminiService.LANGUAGE_ENGLISH, GeminiService.detectLanguage(""));
        assertEquals(GeminiService.LANGUAGE_ENGLISH, GeminiService.detectLanguage(null));

        assertEquals(GeminiService.LANGUAGE_MARATHI, GeminiService.detectLanguage("सोयाबीनचा आजचा दर काय आहे?"));
        assertEquals(GeminiService.LANGUAGE_MARATHI, GeminiService.detectLanguage("हळद म्हणजे काय?"));
        assertEquals(GeminiService.LANGUAGE_MARATHI, GeminiService.detectLanguage("सोयाबीन बद्दल सांगा"));
        assertEquals(GeminiService.LANGUAGE_MARATHI, GeminiService.detectLanguage("आज XYZ ची demand किती आहे?"));

        assertEquals(GeminiService.LANGUAGE_HINDI, GeminiService.detectLanguage("सोयाबीन का आज का भाव क्या है?"));
        assertEquals(GeminiService.LANGUAGE_HINDI, GeminiService.detectLanguage("मक्का के बारे में जानकारी"));
        assertEquals(GeminiService.LANGUAGE_HINDI, GeminiService.detectLanguage("आज XYZ की demand कितनी है?"));
        assertEquals(GeminiService.LANGUAGE_HINDI, GeminiService.detectLanguage("कल का रेट क्या था?"));
    }
}
