package com.example.Commodity_Admin_AI.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for identifying the intent of Admin questions and determining
 * the required retrieval path (Marketplace Indexed Data, Knowledge, Both, or Unknown).
 * Supports multilingual queries in English, Marathi, and Hindi.
 */
@Service
public class IntentService {

    // Initial canonical intents defined in commodityAi specification
    public static final String INTENT_CURRENT_RATE = "CURRENT_RATE";
    public static final String INTENT_RATE_HISTORY = "RATE_HISTORY";
    public static final String INTENT_RATE_CHART = "RATE_CHART";
    public static final String INTENT_TODAY_DEMAND = "TODAY_DEMAND";
    public static final String INTENT_RATE_TREND = "RATE_TREND";
    public static final String INTENT_TODAY_LISTINGS = "TODAY_LISTINGS";
    public static final String INTENT_SUPPLIES_INVENTORY = "TODAY_LISTINGS";
    public static final String INTENT_COMMODITY_INFORMATION = "COMMODITY_INFORMATION";
    public static final String INTENT_NOTIFICATION_STATUS = "NOTIFICATION_STATUS";
    public static final String INTENT_WHATSAPP_STATUS = "WHATSAPP_STATUS";
    public static final String INTENT_ORDERS = "ORDERS";
    public static final String INTENT_PARTIES = "PARTIES";
    public static final String INTENT_BIDS = "BIDS";
    public static final String INTENT_NEGOTIATIONS = "NEGOTIATIONS";
    public static final String INTENT_GREETING = "GREETING";
    public static final String INTENT_OUT_OF_SCOPE = "OUT_OF_SCOPE";
    public static final String INTENT_UNKNOWN = "UNKNOWN";
    public static final String INTENT_WRITE_OPERATION_REJECTED = "WRITE_OPERATION_REJECTED";
    public static final String INTENT_DEALS = "DEALS";

    public static final String CONTROLLED_HELP_RESPONSE =
            "I can help with Commodity Marketplace information, such as commodities, rates, listings, buyer demands, notifications, and related business information.";

    private static final Set<String> EXACT_GREETINGS = Set.of(
            "hello", "hi", "hey", "howdy", "hola",
            "how are you", "how are you doing", "how do you do", "how r u", "how are u",
            "good morning", "good afternoon", "good evening", "good day",
            "namaste", "namaskar", "suprabhat",
            "नमस्कार", "नमस्ते", "प्रणाम",
            "सुप्रभात", "शुभ सकाळ", "शुभ दुपार", "शुभ संध्याकाळ", "शुभ संध्या", "शुभ प्रभात",
            "कसे आहात", "कसे आहात तुम्ही", "कस काय",
            "कैसे हो", "कैसे हैं", "आप कैसे हैं"
    );

    private static final Pattern GREETING_START_PATTERN =
            Pattern.compile("^(hello|hi|hey|howdy|hola|good morning|good afternoon|good evening|good day|namaste|namaskar)\\b.*",
                    Pattern.CASE_INSENSITIVE);

    // Retrieval path types
    public static final String PATH_MARKETPLACE_DATA = "MARKETPLACE_DATA";
    public static final String PATH_KNOWLEDGE = "KNOWLEDGE";
    public static final String PATH_BOTH = "BOTH";
    public static final String PATH_UNKNOWN = "UNKNOWN";

    /**
     * Identifies the primary intent behind an Admin's question.
     *
     * @param question the natural language question (English, Marathi, or Hindi)
     * @return detected intent name
     */
    public String detectIntent(String question) {
        if (question == null || question.isBlank()) {
            return INTENT_UNKNOWN;
        }

        String lower = question.toLowerCase(Locale.ROOT);

        // Phase 1 READ-ONLY check: reject any attempt to modify or create marketplace records
        if (isWriteOperation(lower)) {
            return INTENT_WRITE_OPERATION_REJECTED;
        }

        // Out-Of-Scope check before domain checks (e.g. weather, joke, cricket match)
        if (isOutOfScope(lower)) {
            return INTENT_OUT_OF_SCOPE;
        }

        // Greeting check before domain checks
        if (isGreeting(lower)) {
            return INTENT_GREETING;
        }

        // 1. WhatsApp logs / status
        if (containsAny(lower, "whatsapp", "व्हॉट्सॲप", "व्हाट्सएप")) {
            return INTENT_WHATSAPP_STATUS;
        }

        // 2. Notifications / alerts
        if (containsAny(lower, "notification", "notifications", "alert", "alerts", "सूचना", "नोटिफिकेशन")) {
            return INTENT_NOTIFICATION_STATUS;
        }

        // 3. Parties / Registered entities
        if (isPartiesIntent(lower)) {
            return INTENT_PARTIES;
        }

        // 4. Explicit requests to draw or display a price chart
        if (isRateChartIntent(lower)) {
            return INTENT_RATE_CHART;
        }

        // 5. Rate history, historical records, all actual rate records, and date ranges
        if (isRateHistoryIntent(lower)) {
            return INTENT_RATE_HISTORY;
        }

        // 6. Rate trends, price changes, differences, and rate comparisons
        boolean hasDemand = containsAny(lower, "demand", "demands", "मागणी", "मांग", "डिमांड",
                "buyer demand", "buyer demands", "buyer requirements",
                "buyer", "buyers", "खरेदीदार", "खरीदार",
                "looking for", "want to buy", "wants to buy", "purchaser", "purchasers");
        boolean hasRateOrPriceOrTrend = containsAny(lower, "rate", "rates", "price", "prices", "दर", "भाव", "रेट", "trend", "trends", "कल", "ट्रेंड", "asking price", "asking rate", "किंमत", "दाम");

        if (isPriceChangeOrComparison(lower)
                || containsAny(lower, "trend", "trends", "कल", "ट्रेंड",
                "average rate", "7-day average", "7 day average", "सरासरी", "औसत",
                "yesterday", "काल")) {
            return INTENT_RATE_TREND;
        }

        // 7. Placed orders / order queries (e.g. "order of soybean", "Show soybean orders", "Orders for Soybean")
        if (containsAny(lower, "order", "orders", "ऑर्डर", "ऑर्डर्स", "आर्डर", "आर्डर्स")
                && !lower.contains("in order to")
                && !lower.contains("ascending order")
                && !lower.contains("descending order")) {
            return INTENT_ORDERS;
        }

        // 7b. Deals & Contracts (Phase 1 Read-Only: registered deals, contracts status)
        if (isDealsOrContractsIntent(lower)) {
            return INTENT_DEALS;
        }

        // 8. Buyer demands and buyer inquiries
        boolean hasBuyersAndSuppliers = (containsAny(lower, "buyer", "buyers", "खरेदीदार", "खरेदीदारांची", "खरीदार", "खरीदारों"))
                && (containsAny(lower, "supplier", "suppliers", "seller", "sellers", "पुरवठादार", "विक्रेते", "विक्रेता", "आपूर्तिकर्ता"));

        if (!hasBuyersAndSuppliers && containsAny(lower, "demand", "demands", "मागणी", "मांग", "डिमांड",
                "buyer demand", "buyer demands", "buyer requirements",
                "buyer", "buyers", "खरेदीदार", "खरेदीदारांची", "खरेदीदारांची संख्या",
                "खरीदार", "खरीदारों", "खरीदारों की संख्या",
                "looking for", "want to buy", "wants to buy", "purchaser", "purchasers",
                "requested quantity", "demanded quantity", "quantity requested",
                "target rate", "टार्गेट रेट", "टारगेट रेट",
                "मागणीचे प्रमाण", "मांगी गई मात्रा")) {
            return INTENT_TODAY_DEMAND;
        }

        // 9. Bids, Counter-Offers, and Negotiations (Step 5)
        if (isBidsOrNegotiationsIntent(lower)) {
            if (isNegotiationsSpecificIntent(lower)) {
                return INTENT_NEGOTIATIONS;
            }
            return INTENT_BIDS;
        }

        // 10. Listings / available stock / supplies / inventory
        if (isSupplyOrListingsIntent(lower)) {
            return INTENT_TODAY_LISTINGS;
        }

        // 11. Current rates / prices
        if (containsAny(lower, "current rate", "today rate", "rate", "rates", "price", "prices",
                "दर", "भाव", "सध्याचा दर", "काय दर", "चालू दर",
                "रेट", "वर्तमान रेट", "वर्तमान भाव", "क्या रेट", "आजचा दर", "आज का भाव")) {
            return INTENT_CURRENT_RATE;
        }

        // 12. General commodity information / explanations / rules / policies
        if (containsAny(lower, "what is", "tell me about", "explain", "information", "specification", "specifications", "details",
                "rule", "rules", "policy", "policies", "terms", "faq", "faqs", "registration",
                "बद्दल सांगा", "माहिती", "म्हणजे काय", "नियम", "धोरण",
                "के बारे में", "जानकारी", "क्या है", "विवरण", "नीति", "शर्तें")) {
            return INTENT_COMMODITY_INFORMATION;
        }

        return INTENT_UNKNOWN;
    }

    /**
     * Determines the required retrieval path for an Admin question:
     * - MARKETPLACE_DATA: For live indexed rates, demands, listings, trends, logs
     * - KNOWLEDGE: For static documents, explanations, specs, commodity knowledge
     * - BOTH: When question requests both live data and commodity explanation
     * - UNKNOWN: When intent cannot be identified
     *
     * @param question the natural language question
     * @return one of PATH_MARKETPLACE_DATA, PATH_KNOWLEDGE, PATH_BOTH, or PATH_UNKNOWN
     */
    public String determineRetrievalPath(String question) {
        if (question == null || question.isBlank()) {
            return PATH_UNKNOWN;
        }

        String intent = detectIntent(question);
        return determineRetrievalPath(question, intent);
    }

    /**
     * Determines the required retrieval path using the question text and pre-computed intent.
     *
     * @param question the natural language question
     * @param intent pre-computed intent
     * @return required retrieval path
     */
    public String determineRetrievalPath(String question, String intent) {
        if (question == null || question.isBlank()) {
            return PATH_UNKNOWN;
        }

        if (INTENT_WRITE_OPERATION_REJECTED.equals(intent)
                || INTENT_GREETING.equals(intent)
                || INTENT_OUT_OF_SCOPE.equals(intent)
                || INTENT_UNKNOWN.equals(intent)) {
            return PATH_UNKNOWN;
        }

        String lower = question.toLowerCase(Locale.ROOT);
        boolean hasMarketplace = isMarketplaceQuestion(lower);
        boolean hasKnowledge = isKnowledgeQuestion(lower, hasMarketplace);

        if (hasMarketplace && hasKnowledge) {
            return PATH_BOTH;
        }

        if (INTENT_COMMODITY_INFORMATION.equals(intent) || hasKnowledge) {
            return PATH_KNOWLEDGE;
        }

        if (isMarketplaceIntent(intent) || hasMarketplace) {
            return PATH_MARKETPLACE_DATA;
        }

        return PATH_UNKNOWN;
    }

    /**
     * Checks if an incoming query attempts a write/mutation operation on marketplace data.
     * Phase 1 is strictly READ-ONLY: modifications to rates, listings, demands, users,
     * ownership, deals, bidding, or contracts are strictly forbidden.
     *
     * @param lower the lowercase question text
     * @return true if question attempts a write/modification, false otherwise
     */
    public boolean isWriteOperation(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }

        // If asking about rules, policies, terms, or explanations, it is a read query, not a mutation
        if (containsAny(lower, "policy", "policies", "rule", "rules", "faq", "terms",
                "नियम", "धोरण", "नीति", "शर्तें")) {
            return false;
        }

        // Price changes, differences, rate comparisons, and historical trends are read queries, never write operations
        if (isPriceChangeOrComparison(lower)) {
            return false;
        }

        // Safe read operations: questions asking to show/list/view/display/check/count entities or ask status/details
        if (containsAny(lower, "show", "list", "view", "display", "how many", "which", "details of", "status of", "tell me", "what is", "are there", "is there", "any", "count", "check", "find", "दाखवा", "किती", "कोणत्या")
                && containsAny(lower, "parties", "party", "deleted parties", "inactive parties", "bid", "bids", "negotiation", "negotiations", "counter-offer", "counter offer", "counter rate", "बोली", "बोल्या", "बोलियां", "निगोशिएशन", "वाटाघाटी")) {
            return false;
        }

        // Direct write / transactional actions
        if (containsAny(lower, "place bid", "submit bid", "place a bid", "submit a bid", "lock quantity", "sign contract",
                "create deal", "make deal", "negotiate deal", "बोली लावा", "करार करा", "बोली लगाएं", "सौदा करें")) {
            return true;
        }

        // Action verbs: creation, update, modification, or deletion
        boolean hasWriteVerb = containsAny(lower,
                "create ", "create\t", "update ", "update\t", "delete ", "delete\t",
                "remove ", "remove\t", "modify ", "modify\t", "change ", "change\t",
                "insert ", "insert\t", "add ", "add\t", "drop ", "drop\t",
                "तयार करा", "अपडेट करा", "हटवा", "बदला", "जोडा", "काढा",
                "अपडेट", "बनाएं", "हटाएं", "बदलें", "जोड़ें", "हटाओ", "बदलो");

        if (!hasWriteVerb) {
            return false;
        }

        // Target marketplace entities
        return containsAny(lower,
                "rate", "price", "listing", "demand", "user", "commodity",
                "deal", "contract", "ownership", "bid", "order",
                "दर", "भाव", "रेट", "मार्केट", "लिस्टिंग", "मागणी", "वापरकर्ता", "करार", "मालकी",
                "दाम", "कीमत", "मांग", "यूजर", "सौदा");
    }

    /**
     * Determines whether a question is asking about price changes, price differences, or historical rate comparisons.
     */
    public boolean isPriceChangeOrComparison(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        boolean hasPrice = containsAny(lower, "rate", "rates", "price", "prices", "दर", "भाव", "रेट", "asking price", "asking rate", "किंमत", "दाम");
        if (!hasPrice) {
            return false;
        }

        // Analytical questions about price changes, differences, or comparisons
        return containsAny(lower,
                "price change", "rate change", "price difference", "rate difference",
                "price comparison", "rate comparison", "compare", "comparison", "compared", "versus", "vs",
                "fluctuation", "variation", "volatility",
                "how much did", "how did", "did the price", "did the rate", "percentage change", "absolute change",
                "बदल", "फरक", "तुलना", "वाढ", "घट", "बदलाव", "अंतर", "वृद्धि", "गिरावट")
                || (lower.contains("change") && (lower.contains("between") || lower.contains("from") || lower.contains("how") || lower.contains("what") || lower.contains("show")));
    }

    private boolean isMarketplaceQuestion(String lower) {
        return containsAny(lower, "rate", "rates", "price", "दर", "भाव", "रेट",
                "demand", "demands", "मागणी", "मांग", "डिमांड",
                "supply", "supplies", "पुरवठा", "आपूर्ति",
                "bid", "bids", "negotiation", "negotiations", "counter-offer", "counter offer", "बोली", "बोल्या", "बोलियां", "निगोशिएशन", "वाटाघाटी",
                "buyer", "buyers", "खरेदीदार", "खरीदार",
                "party", "parties", "trader", "traders", "registered", "पार्टी", "पार्ट्या", "पार्टियां",
                "order", "orders", "ऑर्डर", "ऑर्डर्स", "आर्डर", "आर्डर्स",
                "trend", "कल", "ट्रेंड", "history", "इतिहास", "7 days", "yesterday", "काल",
                "7 दिवस", "७ दिवस", "दिवसांचे", "दिवसांचा", "गेल्या", "मागील", "पिछले",
                "listing", "listings", "लिस्टिंग", "stock", "माल",
                "notification", "notifications", "सूचना", "whatsapp", "व्हॉट्सॲप",
                "record", "records", "नोंदी", "रिकॉर्ड");
    }

    private boolean isKnowledgeQuestion(String lower, boolean hasMarketplace) {
        // Explicit explanation / knowledge keywords always indicate knowledge
        if (containsAny(lower, "explain", "tell me about", "information", "specification", "details",
                "rule", "rules", "policy", "policies", "terms", "faq", "faqs",
                "बद्दल सांगा", "माहिती", "म्हणजे काय", "नियम", "धोरण",
                "के बारे में", "जानकारी", "विवरण", "नीति", "शर्तें")) {
            return true;
        }

        // Generic question starters like "what is" or "क्या है" indicate knowledge ONLY if not asking about live market data
        if (!hasMarketplace && containsAny(lower, "what is", "क्या है")) {
            return true;
        }

        return false;
    }

    private boolean isMarketplaceIntent(String intent) {
        return INTENT_CURRENT_RATE.equals(intent) ||
                INTENT_RATE_HISTORY.equals(intent) ||
                INTENT_RATE_CHART.equals(intent) ||
                INTENT_RATE_TREND.equals(intent) ||
                INTENT_TODAY_DEMAND.equals(intent) ||
                INTENT_TODAY_LISTINGS.equals(intent) ||
                INTENT_NOTIFICATION_STATUS.equals(intent) ||
                INTENT_WHATSAPP_STATUS.equals(intent) ||
                INTENT_ORDERS.equals(intent) ||
                INTENT_PARTIES.equals(intent) ||
                INTENT_BIDS.equals(intent) ||
                INTENT_NEGOTIATIONS.equals(intent) ||
                INTENT_DEALS.equals(intent);
    }

    /**
     * Checks if the incoming query explicitly requests a price chart, plot, or graph.
     */
    public boolean isRateChartIntent(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        return containsAny(lower, "chart", "charts", "graph", "graphs", "plot", "plots",
                "draw", "visualize", "चार्ट", "आलेख", "ग्राफ", "नकाशा");
    }

    /**
     * Checks if the incoming query asks for historical rate records, all actual rate records,
     * or rates across a date range.
     */
    public boolean isRateHistoryIntent(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        // Exclude buyer demands, orders, and bids/negotiations, and registrations ("नोंदणी")
        if (containsAny(lower, "buyer demand", "buyer demands", "how many buyers", "मागणी", "मांग")
                || (lower.contains("order") && !lower.contains("in order to"))
                || lower.contains("नोंदणी")
                || isBidsOrNegotiationsIntent(lower)) {
            return false;
        }

        // Analytical price change / comparison / average questions route to RATE_TREND instead
        if (isPriceChangeOrComparison(lower)
                || containsAny(lower, "trend", "trends", "कल", "ट्रेंड", "average", "सरासरी", "औसत")) {
            return false;
        }

        // Explicit "records", "all actual records", "history"
        if (containsAny(lower, "record", "records", "all actual", "all record", "नोंदी", "रिकॉर्ड", "रेकॉर्ड",
                "history", "historical", "इतिहास", "historical rates", "rate history", "price history")
                || (lower.contains("नोंद") && !lower.contains("नोंदणी"))) {
            return true;
        }

        // Time window / date range phrases combined with rates/prices or commodities
        boolean hasRateOrPrice = containsAny(lower, "rate", "rates", "price", "prices", "दर", "भाव", "रेट", "किंमत", "दाम");
        boolean hasCommodity = hasCommodityOrMarketplaceKeywords(lower);

        if (containsAny(lower, "last 7 days", "past 7 days", "7 days", "7 day", "7-day", "7-days",
                "5-day", "5-days", "3-day", "3-days", "last 30 days", "past 30 days", "30 days",
                "7 दिवस", "7 दिवसांचा", "7 दिवसांचे", "७ दिवस", "७ दिवसांचा", "७ दिवसांचे",
                "5 दिवस", "५ दिवस", "3 दिवस", "३ दिवस",
                "गेल्या ७", "मागील ७", "गेल्या 7", "मागील 7",
                "7 दिन", "7 दिनों", "७ दिन", "७ दिनों", "पिछले 7", "पिछले ७",
                "5 दिन", "5 दिनों", "५ दिन", "५ दिनों",
                "कल का रेट", "कालचा दर")) {
            return true;
        }

        if ((containsAny(lower, "गेल्या", "मागील", "पिछले", "last", "past", "between", "दरम्यान", "के बीच"))
                && (hasRateOrPrice || hasCommodity)) {
            return true;
        }

        return false;
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identifies the language of the question (English, Marathi, Hindi).
     *
     * @param question the natural language question
     * @return detected language name
     */
    public String detectLanguage(String question) {
        return com.example.Commodity_Admin_AI.ai.GeminiService.detectLanguage(question);
    }

    /**
     * Returns the standardized controlled response for greetings and out-of-scope messages.
     *
     * @return controlled help message
     */
    public String getControlledResponse() {
        return CONTROLLED_HELP_RESPONSE;
    }

    /**
     * Checks if the incoming query is a greeting.
     *
     * @param lower the lowercase question text
     * @return true if question is a greeting, false otherwise
     */
    public boolean isGreeting(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }

        String cleaned = lower.replaceAll("[^a-zA-Z0-9\\u0900-\\u097F\\s]", " ")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);

        if (EXACT_GREETINGS.contains(cleaned)) {
            return true;
        }

        // If the query contains commodity or marketplace keywords, it is a business query, not a pure greeting
        if (hasCommodityOrMarketplaceKeywords(lower)) {
            return false;
        }

        if (GREETING_START_PATTERN.matcher(cleaned).matches()) {
            return true;
        }

        if (cleaned.startsWith("how are you") || cleaned.startsWith("how r u") || cleaned.startsWith("how do you do")) {
            return true;
        }

        if (cleaned.startsWith("कसे आहात") || cleaned.startsWith("कैसे हो") || cleaned.startsWith("कैसे हैं")
                || cleaned.startsWith("शुभ सकाळ") || cleaned.startsWith("शुभ दुपार") || cleaned.startsWith("शुभ संध्या")
                || cleaned.startsWith("सुप्रभात") || cleaned.startsWith("नमस्कार") || cleaned.startsWith("नमस्ते")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the incoming query is clearly out-of-scope (e.g. weather, jokes, sports).
     *
     * @param lower the lowercase question text
     * @return true if question is out-of-scope, false otherwise
     */
    public boolean isOutOfScope(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }

        // If the question is asking about commodities, marketplace data, or platform rules, keep it in-scope
        if (hasCommodityOrMarketplaceKeywords(lower)) {
            return false;
        }

        // 1. Weather & climate
        if (containsAny(lower, "weather", "temperature", "forecast", "climate", "rainfall", "rain today",
                "हवामान", "मौसम", "बारिश", "तापमान")) {
            return true;
        }

        // 2. Jokes, riddles, humor, entertainment
        if (containsAny(lower, "joke", "jokes", "comedy", "riddle", "funny", "laugh", "meme",
                "विनोद", "चुटकुला", "जोक")) {
            return true;
        }

        // 3. Sports & matches
        if (containsAny(lower, "cricket", "match", "score", "football", "fifa", "hockey", "ipl",
                "world cup", "tournament", "who won", "who lost", "stadium",
                "क्रिकेट", "सामना", "मैच", "खेल")) {
            return true;
        }

        // 4. Movies, songs, cinema, pop culture
        if (containsAny(lower, "movie", "movies", "cinema", "song", "songs", "music", "actor", "actress",
                "film", "गाणी", "गाना", "चित्रपट", "फिल्म", "सिनेमा")) {
            return true;
        }

        // 5. Politics, elections, government leaders
        if (containsAny(lower, "prime minister", "president", "election", "politics", "politician",
                "राजकारण", "निवडणूक", "राजनीति", "चुनाव")) {
            return true;
        }

        // 6. Food recipes & cooking
        if (containsAny(lower, "recipe", "how to cook", "dish", "रेसिपी", "पाककृती")) {
            return true;
        }

        // 7. Off-topic personal/general AI questions
        if (containsAny(lower, "who are you", "tell me a story", "sing a song", "write a poem", "capital of")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the query contains known commodity or marketplace keywords.
     *
     * @param lower the lowercase question text
     * @return true if commodity or marketplace terms are present
     */
    public boolean hasCommodityOrMarketplaceKeywords(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        return containsAny(lower,
                // Marketplace entities & metrics
                "rate", "rates", "price", "prices", "दर", "भाव", "रेट", "दाम", "कीमत",
                "demand", "demands", "मागणी", "मांग", "डिमांड",
                "order", "orders", "ऑर्डर", "ऑर्डर्स", "आर्डर", "आर्डर्स",
                "party", "parties", "trader", "traders", "registered", "पार्टी", "पार्ट्या", "पार्टियां",
                "supply", "supplies", "पुरवठा", "आपूर्ति",
                "bid", "bids", "negotiation", "negotiations", "counter-offer", "counter offer", "बोली", "बोल्या", "बोलियां", "निगोशिएशन", "वाटाघाटी",
                "listing", "listings", "लिस्टिंग", "stock", "माल", "offer", "offers",
                "trend", "trends", "कल", "ट्रेंड", "history", "इतिहास", "7 days",
                "notification", "notifications", "alert", "alerts", "सूचना", "नोटिफिकेशन",
                "whatsapp", "व्हॉट्सॲप", "व्हाट्सएप",
                "record", "records", "नोंदी", "रिकॉर्ड",
                // Specific commodities
                "soybean", "cotton", "maize", "corn", "chana", "wheat", "tur", "sugar", "onion", "garlic",
                "सोयाबीन", "कापूस", "मका", "चना", "गहू", "तूर", "कांदा", "मक्का", "कपास", "गेहूं",
                // Knowledge & Platform
                "rule", "rules", "policy", "policies", "terms", "registration", "company", "faq", "faqs",
                "नियम", "धोरण", "नीति", "शर्तें");
    }

    /**
     * Checks if the incoming query is asking about registered marketplace parties,
     * traders, buyers, sellers, or specific party details (e.g., PRT-0001).
     *
     * @param lower the lowercase question text
     * @return true if question targets party/registered user data, false otherwise
     */
    public boolean isPartiesIntent(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }

        // Questions specifically asking about buyer demands belong to DEMAND intent, not PARTIES
        if (containsAny(lower, "demand", "demands", "मागणी", "मागण्या", "मांग", "मांगें", "buyer demand", "buyer demands")) {
            return false;
        }

        // Active buyers and suppliers questions (Category 6: Parties & Marketplace Overview)
        boolean hasBuyersAndSuppliers = (containsAny(lower, "buyer", "buyers", "खरेदीदार", "खरेदीदारांची", "खरीदार", "खरीदारों"))
                && (containsAny(lower, "supplier", "suppliers", "seller", "sellers", "पुरवठादार", "विक्रेते", "विक्रेता", "आपूर्तिकर्ता"));
        if (hasBuyersAndSuppliers) {
            return true;
        }

        // Direct party references
        if (containsAny(lower, "party", "parties", "पार्टी", "पार्ट्या", "पार्ट्यांची", "पार्टियां", "पार्टियों")) {
            return true;
        }

        // PRT party code pattern (e.g., PRT-0001, PRT-0003, etc.)
        if (Pattern.compile("\\bprt[-\\s]?\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
            return true;
        }

        // Registered participants questions:
        // "How many buyers are registered?"
        // "How many sellers are registered?"
        // "How many suppliers are registered?"
        // "List all registered users"
        boolean hasRegistered = containsAny(lower, "registered", "registration", "नोंदणीकृत", "नोंदणी", "रजिस्टर्ड", "पंजीकृत", "पंजीकरण");
        boolean hasParticipants = containsAny(lower, "buyer", "buyers", "seller", "sellers", "supplier", "suppliers", "trader", "traders",
                "manufacturer", "manufacturers", "user", "users", "participant", "participants", "member", "members",
                "खरेदीदार", "विक्रेते", "पुरवठादार", "व्यापारी", "उत्पादक", "खरीदार", "विक्रेता", "आपूर्तिकर्ता", "आपूर्तिकर्ताओं", "सदस्य", "वापरकर्ते");

        if (hasRegistered && hasParticipants) {
            return true;
        }

        if (hasRegistered && containsAny(lower, "this month", "today", "yesterday", "या महिन्यात", "इस महीने")) {
            return true;
        }

        // Marketplace overview and activity summary (matches Category 6: Parties & Marketplace Overview)
        if (containsAny(lower, "marketplace activity", "market activity", "marketplace overview", "market overview",
                "बाजार आढावा", "बाज़ार गतिविधि", "बाजारातील घडामोडी")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the incoming query is asking about available supplies, stock, listings,
     * seller lots, or exact supply numbers (e.g. SUP-XXXX).
     *
     * @param lower the lowercase question text
     * @return true if question targets supply/listing data, false otherwise
     */
    public boolean isSupplyOrListingsIntent(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }

        // Bids, counter-offers, and negotiations take precedence over generic supply words like "offer"
        if (isBidsOrNegotiationsIntent(lower)) {
            return false;
        }

        // 1. Exact supply number pattern (e.g., SUP-1792572721202)
        if (Pattern.compile("\\bsup[-\\s]?\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
            return true;
        }

        // 2. Direct supply & listing terms
        boolean hasSupplyWord = containsAny(lower,
                "supply", "supplies", "supplier", "suppliers", "पुरवठा", "पुरवठे", "आपूर्ति", "सप्लाई",
                "listing", "listings", "लिस्टिंग",
                "available stock", "stock", "उपलब्ध माल", "स्टॉक", "माल साठा",
                "offer", "offers", "seller lot", "supply lot");

        if (hasSupplyWord) {
            return true;
        }

        // 3. Available quantity questions (e.g. "Which seller has the highest available quantity?", "Total available quantity for...")
        if (containsAny(lower,
                "available quantity", "available quantities", "highest available quantity", "total available quantity", "maximum available quantity",
                "quantity available", "quantities available", "highest quantity",
                "उपलब्ध प्रमाण", "उपलब्ध साठा", "उपलब्ध मात्रा", "कुल उपलब्ध मात्रा", "सर्वोच्च उपलब्ध साठा", "सर्वाधिक उपलब्ध साठा")
                || (lower.contains("available") && (lower.contains("how much") || lower.contains("how many")))) {
            return true;
        }

        // 4. Seller / supplier stock / quantity questions (e.g. "Which seller has the highest...", "Which suppliers have the largest...")
        if (containsAny(lower, "seller", "sellers", "supplier", "suppliers", "विक्रेता", "विक्रेते") &&
                containsAny(lower, "highest", "most", "maximum", "largest", "सर्वाधिक", "सर्वात जास्त", "सबसे अधिक", "सबसे ज्यादा", "अधिकतम")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the incoming query is asking about bids, counter-offers, or negotiations.
     *
     * @param lower the lowercase question text
     * @return true if query targets bids or negotiations
     */
    public boolean isBidsOrNegotiationsIntent(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }

        // 1. Exact bid number pattern (e.g., BID-1792400918453, BID-1)
        if (Pattern.compile("\\bbid[-\\s]?\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
            return true;
        }

        // 2. Exact negotiation code pattern (e.g., NEG-XXXX)
        if (Pattern.compile("\\bneg[-\\s]?\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
            return true;
        }

        // 3. Counter-offer terms
        if (containsAny(lower,
                "counter-offer", "counter offer", "counter-offers", "counter offers",
                "counter rate", "counterrate", "counter-rate", "counter remarks",
                "काउंटर ऑफर", "काउंटर रेट")) {
            return true;
        }

        // 4. Negotiation terms
        if (containsAny(lower,
                "negotiation", "negotiations", "negotiating", "bargain", "bargaining",
                "निगोशिएशन", "निगोशिएशन्स", "वाटाघाटी", "तडजोड", "बातचीत")) {
            return true;
        }

        // 5. Bids and bidding terms
        if (containsAny(lower,
                "bid", "bids", "bidding", "bidder", "bidders",
                "बोली", "बोल्या", "बोलियां", "बिड", "बिड्स")) {
            return true;
        }

        return false;
    }

    /**
     * Determines if a query is specifically targeting negotiations or counter-offers rather than simple bids.
     */
    public boolean isNegotiationsSpecificIntent(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        return containsAny(lower,
                "negotiation", "negotiations", "negotiating", "वाटाघाटी", "निगोशिएशन", "निगोशिएशन्स", "बातचीत",
                "counter-offer", "counter offer", "counter-offers", "counter offers", "काउंटर ऑफर");
    }

    /**
     * Checks if the incoming query is asking about deals or contracts in Phase 1 read-only mode.
     */
    public boolean isDealsOrContractsIntent(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        if (isWriteOperation(lower)) {
            return false;
        }
        return containsAny(lower, "deal", "deals", "contract", "contracts", "सौदा", "सौदे", "करार", "करारनामे", "अनुबंध");
    }
}
