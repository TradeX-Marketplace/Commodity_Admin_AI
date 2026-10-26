package com.example.Commodity_Admin_AI.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IntentServiceTest {

    private IntentService intentService;

    @BeforeEach
    void setUp() {
        intentService = new IntentService();
    }

    @Test
    @DisplayName("detectIntent identifies CURRENT_RATE in English, Marathi, and Hindi")
    void detectIntent_CurrentRate_Multilingual() {
        assertEquals(IntentService.INTENT_CURRENT_RATE, intentService.detectIntent("XYZ current rate?"));
        assertEquals(IntentService.INTENT_CURRENT_RATE, intentService.detectIntent("What is XYZ current rate?"));
        assertEquals(IntentService.INTENT_CURRENT_RATE, intentService.detectIntent("XYZ चा सध्याचा दर किती आहे?"));
        assertEquals(IntentService.INTENT_CURRENT_RATE, intentService.detectIntent("XYZ का वर्तमान रेट क्या है?"));
        assertEquals(IntentService.INTENT_CURRENT_RATE, intentService.detectIntent("सोयाबीनचा आजचा दर काय आहे?"));
    }

    @Test
    @DisplayName("detectIntent identifies TODAY_DEMAND in English, Marathi, and Hindi")
    void detectIntent_TodayDemand_Multilingual() {
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent("What is XYZ demand today?"));
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent("How many buyers are looking for Soybean?"));
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent("आज XYZ ची demand किती आहे?"));
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent("आज XYZ ची मागणी किती आहे?"));
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent("सोयाबीनसाठी किती खरेदीदार शोधत आहेत?"));
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent("आज XYZ की demand कितनी है?"));
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent("आज सोयाबीन की मांग कितनी है?"));
        assertEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent("सोयाबीन के लिए कितने खरीदार हैं?"));
    }

    @Test
    @DisplayName("detectIntent identifies RATE_TREND in English, Marathi, and Hindi")
    void detectIntent_RateTrend_Multilingual() {
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent("XYZ चा 7 दिवसांचा trend?"));
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent("XYZ 7 days rate trend"));
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent("सोयाबीन का 7 दिनों का ट्रेंड बताओ"));
    }

    @Test
    @DisplayName("detectIntent identifies RATE_HISTORY in English, Marathi, and Hindi")
    void detectIntent_RateHistory_Multilingual() {
        assertEquals(IntentService.INTENT_RATE_HISTORY, intentService.detectIntent("Rate history of cotton"));
        assertEquals(IntentService.INTENT_RATE_HISTORY, intentService.detectIntent("XYZ चा 7 दिवसांचा इतिहास"));
        assertEquals(IntentService.INTENT_RATE_HISTORY, intentService.detectIntent("गेल्या ७ दिवसांचे सोयाबीनचे दर दाखवा."));
        assertEquals(IntentService.INTENT_RATE_HISTORY, intentService.detectIntent("मागील ७ दिवसांचे सोयाबीनचे दर"));
        assertEquals(IntentService.INTENT_RATE_HISTORY, intentService.detectIntent("पिछले 7 दिनों का सोयाबीन का रेट दिखाएं।"));
        assertEquals(IntentService.INTENT_RATE_HISTORY, intentService.detectIntent("Show me all actual Soybean records."));
        assertEquals(IntentService.INTENT_RATE_HISTORY, intentService.detectIntent("Show me Soybean rates for the last 7 days."));
    }

    @Test
    @DisplayName("detectIntent identifies RATE_CHART")
    void detectIntent_RateChart() {
        assertEquals(IntentService.INTENT_RATE_CHART, intentService.detectIntent("Show me a chart of Soybean rates for the last 7 days."));
        assertEquals(IntentService.INTENT_RATE_CHART, intentService.detectIntent("सोयाबीन दरांचा चार्ट दाखवा"));
    }

    @Test
    @DisplayName("detectIntent identifies TODAY_LISTINGS in English, Marathi, and Hindi")
    void detectIntent_TodayListings_Multilingual() {
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("Show me today listings for maize"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("आजचे listings दाखवा"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("आज उपलब्ध माल किती आहे?"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("आज की listings क्या हैं?"));
    }

    @Test
    @DisplayName("detectIntent identifies COMMODITY_INFORMATION in English, Marathi, and Hindi")
    void detectIntent_CommodityInformation_Multilingual() {
        assertEquals(IntentService.INTENT_COMMODITY_INFORMATION, intentService.detectIntent("Tell me about XYZ commodity."));
        assertEquals(IntentService.INTENT_COMMODITY_INFORMATION, intentService.detectIntent("What is corn gluten feed?"));
        assertEquals(IntentService.INTENT_COMMODITY_INFORMATION, intentService.detectIntent("सोयाबीन बद्दल सांगा"));
        assertEquals(IntentService.INTENT_COMMODITY_INFORMATION, intentService.detectIntent("हळद म्हणजे काय?"));
        assertEquals(IntentService.INTENT_COMMODITY_INFORMATION, intentService.detectIntent("मक्का के बारे में जानकारी"));
    }

    @Test
    @DisplayName("detectIntent identifies NOTIFICATION_STATUS")
    void detectIntent_NotificationStatus() {
        assertEquals(IntentService.INTENT_NOTIFICATION_STATUS, intentService.detectIntent("Show all recent notifications"));
        assertEquals(IntentService.INTENT_NOTIFICATION_STATUS, intentService.detectIntent("नव्या सूचना दाखवा"));
    }

    @Test
    @DisplayName("detectIntent identifies WHATSAPP_STATUS")
    void detectIntent_WhatsAppStatus() {
        assertEquals(IntentService.INTENT_WHATSAPP_STATUS, intentService.detectIntent("Check whatsapp logs"));
        assertEquals(IntentService.INTENT_WHATSAPP_STATUS, intentService.detectIntent("व्हॉट्सॲप स्टेटस काय आहे?"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Random unsupported phrase 999", "Can you help me?", "12345", ""})
    @DisplayName("detectIntent returns UNKNOWN for unmatched or empty input")
    void detectIntent_Unknown(String input) {
        assertEquals(IntentService.INTENT_UNKNOWN, intentService.detectIntent(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Hello", "Hi", "Hey", "How are you?", "how are you",
            "Good morning", "Good afternoon", "Good evening",
            "नमस्कार", "नमस्ते", "सुप्रभात", "शुभ सकाळ", "कसे आहात?", "कैसे हो?"
    })
    @DisplayName("detectIntent identifies GREETING in English, Marathi, and Hindi")
    void detectIntent_Greeting(String input) {
        assertEquals(IntentService.INTENT_GREETING, intentService.detectIntent(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What is the weather today?",
            "Tell me a joke.",
            "Who won yesterday's cricket match?",
            "What is the capital of France?",
            "Sing a song for me",
            "आजचे हवामान कसे आहे?",
            "आज का मौसम कैसा है?"
    })
    @DisplayName("detectIntent identifies OUT_OF_SCOPE for unrelated non-commodity questions")
    void detectIntent_OutOfScope(String input) {
        assertEquals(IntentService.INTENT_OUT_OF_SCOPE, intentService.detectIntent(input));
    }

    @Test
    @DisplayName("detectIntent preserves supported commodity marketplace questions")
    void detectIntent_SupportedCommodityQuestions() {
        assertEquals(IntentService.INTENT_COMMODITY_INFORMATION, intentService.detectIntent("What is soybean?"));
        assertEquals(IntentService.INTENT_COMMODITY_INFORMATION, intentService.detectIntent("What is the company name?"));
        assertEquals(IntentService.INTENT_CURRENT_RATE, intentService.detectIntent("What is today's soybean rate?"));
        assertEquals(IntentService.INTENT_TODAY_LISTINGS, intentService.detectIntent("Show today's listings."));
        assertEquals(IntentService.INTENT_WHATSAPP_STATUS, intentService.detectIntent("How many WhatsApp notifications were sent today?"));
    }

    @Test
    @DisplayName("determineRetrievalPath correctly maps to MARKETPLACE_DATA, KNOWLEDGE, BOTH, or UNKNOWN")
    void determineRetrievalPath_Scenarios() {
        // Pure marketplace data
        assertEquals(IntentService.PATH_MARKETPLACE_DATA,
                intentService.determineRetrievalPath("What is the current soybean rate in Sangli?"));
        assertEquals(IntentService.PATH_MARKETPLACE_DATA,
                intentService.determineRetrievalPath("आज XYZ ची demand किती आहे?"));

        // Pure knowledge retrieval
        assertEquals(IntentService.PATH_KNOWLEDGE,
                intentService.determineRetrievalPath("What is corn gluten feed?"));
        assertEquals(IntentService.PATH_KNOWLEDGE,
                intentService.determineRetrievalPath("सोयाबीन बद्दल सांगा"));

        // Combined: Live rate + Commodity knowledge
        assertEquals(IntentService.PATH_BOTH,
                intentService.determineRetrievalPath("What is the current soybean rate in Sangli and explain soybean?"));
        assertEquals(IntentService.PATH_BOTH,
                intentService.determineRetrievalPath("सोयाबीनचा चालू दर किती आहे आणि सोयाबीन बद्दल सांगा"));

        // Greetings and Out-of-scope map to PATH_UNKNOWN
        assertEquals(IntentService.PATH_UNKNOWN,
                intentService.determineRetrievalPath("Hello"));
        assertEquals(IntentService.PATH_UNKNOWN,
                intentService.determineRetrievalPath("Good morning!"));
        assertEquals(IntentService.PATH_UNKNOWN,
                intentService.determineRetrievalPath("What is the weather today?"));
        assertEquals(IntentService.PATH_UNKNOWN,
                intentService.determineRetrievalPath("Tell me a joke."));
        assertEquals(IntentService.PATH_UNKNOWN,
                intentService.determineRetrievalPath("Who won yesterday's cricket match?"));
        assertEquals(IntentService.PATH_UNKNOWN,
                intentService.determineRetrievalPath(null));
    }

    @Test
    @DisplayName("determineRetrievalPath handles Step 13 prompt specification examples")
    void determineRetrievalPath_Step13Examples() {
        assertEquals(IntentService.PATH_KNOWLEDGE,
                intentService.determineRetrievalPath("Explain the marketplace registration rules."));
        assertEquals(IntentService.PATH_MARKETPLACE_DATA,
                intentService.determineRetrievalPath("What is today's soybean rate in Sangli?"));
        assertEquals(IntentService.PATH_BOTH,
                intentService.determineRetrievalPath("Explain the rate policy and show today's soybean rate."));
        assertEquals(IntentService.PATH_MARKETPLACE_DATA,
                intentService.determineRetrievalPath("order of soybean"));
    }

    @Test
    @DisplayName("detectIntent identifies ORDERS in English, Marathi, and Hindi")
    void detectIntent_Orders_Multilingual() {
        assertEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("order of soybean"));
        assertEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("Show soybean orders"));
        assertEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("Orders for Soybean"));
        assertEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("How many soybean orders are there?"));
        assertEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("सोयाबीनच्या ऑर्डर्स दाखवा"));
        assertEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("सोयाबीन के ऑर्डर्स"));
        assertEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("सोयाबीन का आर्डर"));
    }

    @Test
    @DisplayName("detectIntent does not misidentify non-entity uses of order as INTENT_ORDERS")
    void detectIntent_Orders_NonEntityGuards() {
        // "in order to" should not match ORDERS
        assertNotEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("in order to check prices"));
        // "ascending order" should not match ORDERS
        assertNotEquals(IntentService.INTENT_ORDERS, intentService.detectIntent("show rates in ascending order"));
        // "create order" is a write operation and must be rejected
        assertEquals(IntentService.INTENT_WRITE_OPERATION_REJECTED, intentService.detectIntent("create order for soybean"));
    }

    @Test
    @DisplayName("detectIntent routes date-to-date price change and rate comparisons to RATE_TREND, never to TODAY_DEMAND or WRITE_OPERATION_REJECTED")
    void detectIntent_PriceChange_RoutesToRateTrendNotDemand() {
        String q1 = "How much did the Soybean asking price change between October 15 and October 21, 2026?";
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent(q1));
        assertNotEquals(IntentService.INTENT_TODAY_DEMAND, intentService.detectIntent(q1));
        assertNotEquals(IntentService.INTENT_WRITE_OPERATION_REJECTED, intentService.detectIntent(q1));

        String q2 = "Soybean price difference between October 15 and October 21";
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent(q2));

        String q3 = "What is the 7-day average rate for Soybean?";
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent(q3));

        String q4 = "Show me the 5-day Soybean rate trend";
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent(q4));

        String q5 = "15 आणि 21 ऑक्टोबर दरम्यान सोयाबीनच्या दरात काय बदल झाला?";
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent(q5));

        String q6 = "15 और 21 अक्टूबर के बीच सोयाबीन के भाव में कितना बदलाव आया?";
        assertEquals(IntentService.INTENT_RATE_TREND, intentService.detectIntent(q6));
    }

    @Test
    @DisplayName("detectIntent identifies PARTIES for all registered participant and party questions")
    void detectIntent_Parties_AllQuestions() {
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("How many parties are registered in the marketplace?"));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("List all registered parties."));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("How many buyers are registered?"));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("How many sellers are registered?"));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("Show the details of party PRT-0001."));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("Which parties are registered in Sangli?"));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("How many parties registered this month?"));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("Show inactive or deleted parties, if supported."));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("मार्केटप्लेसमध्ये किती पार्ट्यांची नोंदणी झाली आहे?"));
        assertEquals(IntentService.INTENT_PARTIES, intentService.detectIntent("कितने खरीदार पंजीकृत हैं?"));
    }
}
