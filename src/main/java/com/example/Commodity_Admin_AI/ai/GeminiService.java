package com.example.Commodity_Admin_AI.ai;

import com.example.Commodity_Admin_AI.entity.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Service for Google Gemini LLM interaction using Spring AI.
 * <p>
 * STEP 11 — GEMINI INTEGRATION, STEP 12 — RAG ADVISOR & STEP 14 — CHAT HISTORY:
 * - Uses Spring AI's ChatModel abstraction backed by Google GenAI.
 * - Enforces controlled prompt templates with system role, read-only constraints, and context boundaries.
 * - Operates strictly on retrieved controlled context from RagService (indexed marketplace data & knowledge).
 * - Incorporates bounded multi-turn conversation history while preserving context boundaries.
 * - Read-only: does not modify database records or call arbitrary write APIs.
 * - Supports multilingual queries in English, Marathi, and Hindi.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    public static final String LANGUAGE_ENGLISH = "English";
    public static final String LANGUAGE_MARATHI = "Marathi";
    public static final String LANGUAGE_HINDI = "Hindi";

    public static final String SYSTEM_PROMPT = """
            You are the Admin AI Assistant for the B2B Commodity Raw Material Marketplace.
            
            CORE RESPONSIBILITIES:
            1. Answer administrative queries accurately using the provided CONTROLLED CONTEXT and conversation history.
            2. The controlled context may contain:
               - Indexed Marketplace Data (rates, buyer demands, listings, notifications, trends, locations)
               - General Knowledge / RAG Data (commodity information, specifications, business rules, FAQs)
            
            STRICT ARCHITECTURAL AND OPERATIONAL BOUNDARIES:
            - READ-ONLY MODE (Phase 1): You are strictly a read-only assistant. You CANNOT create, update, delete, or modify users, listings, market rates, buyer demands, deals, or contracts.
            - NO HALLUCINATION: Rely strictly on the information provided in the CONTROLLED CONTEXT. Never invent, extrapolate, or guess marketplace rates, trends, quantities, or commodity values.
            - MISSING DATA: If the requested data or commodity is not present in the provided context, state clearly and concisely that the data is currently unavailable in the marketplace records.
            - PRIVACY & CONFIDENTIALITY: Do not leak confidential supplier, buyer, or internal ownership details unless authorized in the context.
            - NO DIRECT DATABASE ACCESS: You interact solely via the provided context. You do not have direct access to database credentials or unrestricted APIs.
            
            MULTILINGUAL INSTRUCTIONS:
            - You fully support English, Marathi (\u092e\u0930\u093e\u0920\u0940), and Hindi (\u0939\u093f\u0902\u0926\u0940).
            - Always respond in the same language used by the Admin in the question:
              * English question -> English answer
              * Marathi question (\u092e\u0930\u093e\u0920\u0940 \u092a\u094d\u0930\u0936\u094d\u0928) -> Marathi answer (\u092e\u0930\u093e\u0920\u0940\u0924 \u0909\u0924\u094d\u0924\u0930 \u0926\u094d\u092f\u093e)
              * Hindi question (\u0939\u093f\u0902\u0926\u0940 \u092a\u094d\u0930\u0936\u094d\u0928) -> Hindi answer (\u0939\u093f\u0902\u0926\u0940 \u092e\u0947\u0902 \u0909\u0924\u094d\u0924\u0930 \u0926\u0947\u0902)
            
            STYLE:
            - Provide professional, structured, concise, and accurate responses.
            """;

    private final ChatModel chatModel;

    @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}")
    private String modelName;

    /**
     * Primary constructor with optional Spring AI ChatModel injection.
     * Defensive injection ensures the application context can start cleanly
     * even when external Gemini API credentials are omitted or auto-configuration is disabled in tests.
     *
     * @param chatModel optional ChatModel instance
     */
    @Autowired
    public GeminiService(@Autowired(required = false) ChatModel chatModel) {
        this(chatModel, "gemini-2.5-flash");
    }

    /**
     * Constructor for explicit configuration and unit testing.
     *
     * @param chatModel the ChatModel instance
     * @param modelName the Gemini model name
     */
    public GeminiService(ChatModel chatModel, String modelName) {
        this.chatModel = chatModel;
        this.modelName = modelName;
    }

    /**
     * Generate natural language response from Gemini model using controlled context.
     * Delegates to multi-turn overload with empty history.
     *
     * @param question the admin question
     * @param context the controlled retrieved context from RagService
     * @return model generated response text
     */
    public String generateResponse(String question, String context) {
        return generateResponse(question, context, Collections.emptyList(), detectLanguage(question));
    }

    public String generateResponse(String question, String context, List<ChatMessage> conversationHistory) {
        return generateResponse(question, context, conversationHistory, detectLanguage(question));
    }

    public String generateResponse(String question, String context, List<ChatMessage> conversationHistory, String language) {
        if (question == null || question.isBlank()) {
            return "Please provide a valid administrative question.";
        }

        if (this.chatModel == null) {
            log.warn("Gemini ChatModel is unconfigured or unavailable. Returning fallback response.");
            return "Gemini AI model is not configured. Please configure spring.ai.google.genai.api-key to enable live responses.";
        }

        try {
            Prompt prompt = buildPrompt(question, context, conversationHistory, language != null ? language : detectLanguage(question));
            log.debug("Dispatching controlled prompt with {} history turns to Gemini LLM (model: {}, language: {})",
                    conversationHistory != null ? conversationHistory.size() : 0, modelName, language);

            ChatResponse response = chatModel.call(prompt);
            if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                String text = response.getResult().getOutput().getText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }

            log.warn("Gemini returned empty or null response for question: {}", question);
            return "No response could be generated for the question.";
        } catch (Exception e) {
            String safeMsg = sanitizeCredentials(e.getMessage());
            Integer statusCode = extractHttpStatusCode(e);
            log.error("Error communicating with Gemini model (HTTP status: {}): {}",
                    statusCode != null ? statusCode : "N/A", safeMsg);
            return "An error occurred while communicating with the AI service: " + safeMsg;
        }
    }

    /**
     * Generates a natural language response using Spring AI ChatClient with an attached QuestionAnswerAdvisor.
     * Keeps Gemini behind GeminiService and preserves system instructions and persona.
     *
     * @param question the admin question
     * @param advisor the configured QuestionAnswerAdvisor (optional)
     * @return model generated response text
     */
    public String generateResponseWithAdvisor(String question, QuestionAnswerAdvisor advisor) {
        if (question == null || question.isBlank()) {
            return "Please provide a valid administrative question.";
        }

        if (this.chatModel == null) {
            log.warn("Gemini ChatModel is unconfigured or unavailable. Returning fallback response.");
            return "Gemini AI model is not configured. Please configure spring.ai.google.genai.api-key to enable live responses.";
        }

        try {
            ChatClient.Builder clientBuilder = ChatClient.builder(chatModel)
                    .defaultSystem(SYSTEM_PROMPT);

            if (advisor != null) {
                clientBuilder.defaultAdvisors(advisor);
            }

            String language = detectLanguage(question);
            String userPrompt = question.trim() + "\n\n" + getLanguageResponseInstruction(language);

            ChatClient chatClient = clientBuilder.build();
            String response = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            if (response != null && !response.isBlank()) {
                return response.trim();
            }

            log.warn("ChatClient returned empty response for question: {}", question);
            return "No response could be generated for the question.";
        } catch (Exception e) {
            log.error("Error communicating with Gemini model via ChatClient advisor: {}", e.getMessage(), e);
            return "An error occurred while communicating with the AI service: " + e.getMessage();
        }
    }

    /**
     * Constructs a structured Prompt containing system instructions and user message with controlled context.
     *
     * @param question the admin question
     * @param context the retrieved context from RagService
     * @return constructed Prompt object
     */
    public Prompt buildPrompt(String question, String context) {
        return buildPrompt(question, context, Collections.emptyList(), detectLanguage(question));
    }

    /**
     * Constructs a structured Prompt containing system instructions, bounded conversation history,
     * and current user message with controlled context.
     *
     * @param question the admin question
     * @param context the retrieved context from RagService
     * @param conversationHistory bounded prior conversation messages
     * @return constructed Prompt object
     */
    public Prompt buildPrompt(String question, String context, List<ChatMessage> conversationHistory) {
        return buildPrompt(question, context, conversationHistory, detectLanguage(question));
    }

    /**
     * Constructs a structured Prompt containing system instructions, bounded conversation history,
     * and current user message with controlled context and explicit language instructions.
     *
     * @param question the admin question
     * @param context the retrieved context from RagService
     * @param conversationHistory bounded prior conversation messages
     * @param language detected or explicit language (English, Marathi, Hindi)
     * @return constructed Prompt object
     */
    public Prompt buildPrompt(String question, String context, List<ChatMessage> conversationHistory, String language) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        // Append bounded prior conversation turns
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (ChatMessage msg : conversationHistory) {
                if (msg != null && msg.getContent() != null && !msg.getContent().isBlank()) {
                    if (ChatMessage.ROLE_ADMIN.equalsIgnoreCase(msg.getRole())) {
                        messages.add(new UserMessage(msg.getContent().trim()));
                    } else if (ChatMessage.ROLE_ASSISTANT.equalsIgnoreCase(msg.getRole())) {
                        messages.add(new AssistantMessage(msg.getContent().trim()));
                    }
                }
            }
        }

        // Current turn with controlled context
        StringBuilder userContent = new StringBuilder();
        userContent.append("=== CONTROLLED CONTEXT START ===\n");
        if (context != null && !context.isBlank()) {
            userContent.append(context.trim()).append("\n");
        } else {
            userContent.append("[No additional context retrieved]\n");
        }
        userContent.append("=== CONTROLLED CONTEXT END ===\n\n");

        userContent.append("Admin Question: ").append(question != null ? question.trim() : "").append("\n\n");
        userContent.append(getLanguageResponseInstruction(language != null ? language : detectLanguage(question)));

        messages.add(new UserMessage(userContent.toString()));
        return new Prompt(messages);
    }

    /**
     * Detects the language of the admin question without requiring a separate AI model.
     * Identifies English (Latin script), Marathi (Devanagari with Marathi-specific characters/markers),
     * and Hindi (Devanagari with Hindi-specific markers).
     *
     * @param text the input question text
     * @return one of LANGUAGE_ENGLISH, LANGUAGE_MARATHI, or LANGUAGE_HINDI
     */
    public static String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return LANGUAGE_ENGLISH;
        }

        // 1. Script check: check if contains any Devanagari Unicode characters (U+0900 to U+097F)
        boolean hasDevanagari = text.codePoints()
                .anyMatch(cp -> Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.DEVANAGARI);
        if (!hasDevanagari) {
            return LANGUAGE_ENGLISH;
        }

        // 2. Character-level Marathi-exclusive markers:
        // 'ळ' (U+0933) is standard in Marathi and absent in modern standard Hindi.
        // 'ॲ' (U+0972) and 'ॅ' (U+0945 - Chandra E) are characteristic Marathi vowel notations.
        if (text.contains("ळ") || text.contains("\u0933") || text.contains("ॲ") || text.contains("\u0972") || text.contains("ॅ")) {
            return LANGUAGE_MARATHI;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        int marathiScore = 0;
        int hindiScore = 0;

        // Distinctive Marathi verbal, interrogative, and copula markers
        if (lower.contains("आहे") || lower.contains("आहेत") || lower.contains("नाही") || lower.contains("नाहीत")) {
            marathiScore += 4;
        }
        if (lower.contains("काय") || lower.contains("किती") || lower.contains("कसा") || lower.contains("कशी")) {
            marathiScore += 3;
        }
        if (lower.contains("सांगा") || lower.contains("दाखवा") || lower.contains("द्या") || lower.contains("करा")) {
            marathiScore += 3;
        }
        if (lower.contains("बद्दल") || lower.contains("म्हणजे") || lower.contains("माहिती") || lower.contains("शेतकरी")) {
            marathiScore += 2;
        }
        if (lower.contains("आजचा") || lower.contains("आजचे") || lower.contains("कालचा") || lower.contains("दिवसांचा") || lower.contains("मागणी")) {
            marathiScore += 2;
        }

        // Distinctive Hindi verbal, interrogative, and copula markers
        if (lower.contains("है") || lower.contains("हैं") || lower.contains("था") || lower.contains("थी") || lower.contains("थे")) {
            hindiScore += 4;
        }
        if (lower.contains("क्या") || lower.contains("कितना") || lower.contains("कितनी") || lower.contains("कितने") || lower.contains("कैसे")) {
            hindiScore += 3;
        }
        if (lower.contains("बताओ") || lower.contains("बताइए") || lower.contains("दिखाओ") || lower.contains("दीजिए") || lower.contains("करें")) {
            hindiScore += 3;
        }
        if (lower.contains("बारे में") || lower.contains("जानकारी") || lower.contains("विवरण")) {
            hindiScore += 2;
        }
        if (lower.contains("आज का") || lower.contains("कल का") || lower.contains("दिनों") || lower.contains("मांग") || lower.contains("ताज़ा")) {
            hindiScore += 2;
        }

        // Token-level check for postpositions
        String[] tokens = lower.split("[\\s,\\.?!;:\\(\\)\\[\\]\"']+");
        for (String token : tokens) {
            // Marathi genitive suffixes
            if (token.endsWith("चा") || token.endsWith("ची") || token.endsWith("चे") || token.endsWith("च्या") || token.endsWith("तील")) {
                marathiScore += 1;
            }
            // Hindi postpositions
            if (token.equals("का") || token.equals("की") || token.equals("के") || token.equals("में") || token.equals("से") || token.equals("को") || token.equals("पर")) {
                hindiScore += 1;
            }
        }

        if (marathiScore > hindiScore) {
            return LANGUAGE_MARATHI;
        } else if (hindiScore > marathiScore) {
            return LANGUAGE_HINDI;
        }

        // Default for Devanagari in Maharashtra agricultural marketplace domain
        return LANGUAGE_MARATHI;
    }

    /**
     * Normalizes language identifiers (e.g. "mr", "MARATHI", "hi", "en") into canonical language names.
     *
     * @param lang raw language code or name
     * @return canonical language name (English, Marathi, Hindi) or original if unrecognized
     */
    public static String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) {
            return null;
        }
        String lower = lang.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("mr") || lower.contains("marathi") || lower.contains("मराठी")) {
            return LANGUAGE_MARATHI;
        }
        if (lower.startsWith("hi") || lower.contains("hindi") || lower.contains("हिंदी")) {
            return LANGUAGE_HINDI;
        }
        if (lower.startsWith("en") || lower.contains("english")) {
            return LANGUAGE_ENGLISH;
        }
        return lang;
    }

    /**
     * Returns the strict language instruction for Gemini to respond in the admin's question language.
     *
     * @param language the detected language (English, Marathi, Hindi)
     * @return localized instruction string
     */
    public static String getLanguageResponseInstruction(String language) {
        String normalized = normalizeLanguage(language);
        if (LANGUAGE_MARATHI.equalsIgnoreCase(normalized)) {
            return "Language Instruction: The admin asked the question in Marathi (\u092e\u0930\u093e\u0920\u0940). You MUST respond strictly in Marathi (\u092e\u0930\u093e\u0920\u0940\u0924 \u0909\u0924\u094d\u0924\u0930 \u0926\u094d\u092f\u093e). Please provide an accurate, read-only administrative response based strictly on the above context.";
        } else if (LANGUAGE_HINDI.equalsIgnoreCase(normalized)) {
            return "Language Instruction: The admin asked the question in Hindi (\u0939\u093f\u0902\u0926\u0940). You MUST respond strictly in Hindi (\u0939\u093f\u0902\u0926\u0940 \u092e\u0947\u0902 \u0909\u0924\u094d\u0924\u0930 \u0926\u0947\u0902). Please provide an accurate, read-only administrative response based strictly on the above context.";
        }
        return "Language Instruction: The admin asked the question in English. You MUST respond strictly in English. Please provide an accurate, read-only administrative response based strictly on the above context.";
    }

    public static String sanitizeCredentials(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)(api[_-]?key[:=]\\s*)[A-Za-z0-9_.-]+", "$1[REDACTED]")
                   .replaceAll("(?i)(key=)[A-Za-z0-9_.-]+", "$1[REDACTED]")
                   .replaceAll("AQ\\.[A-Za-z0-9_.-]+", "[REDACTED_API_KEY]")
                   .replaceAll("AIzaSy[A-Za-z0-9_.-]+", "[REDACTED_API_KEY]")
                   .replaceAll("(?i)(bearer\\s+)[A-Za-z0-9_.-]+", "$1[REDACTED_TOKEN]")
                   .replaceAll("(?i)(password[:=]\\s*)[^,\\s&]+", "$1[REDACTED]");
    }

    public static Integer extractHttpStatusCode(Throwable t) {
        if (t == null) {
            return null;
        }
        if (t instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            return httpEx.getStatusCode().value();
        }
        if (t instanceof org.springframework.web.client.RestClientResponseException restEx) {
            return restEx.getStatusCode().value();
        }
        String msg = t.getMessage();
        if (msg != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([45]\\d{2})\\b").matcher(msg);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (t.getCause() != null && t.getCause() != t) {
            return extractHttpStatusCode(t.getCause());
        }
        return null;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    public String getModelName() {
        return modelName;
    }
}
