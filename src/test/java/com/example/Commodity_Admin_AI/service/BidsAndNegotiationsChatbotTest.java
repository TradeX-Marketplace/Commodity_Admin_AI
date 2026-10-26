package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.BidDto;
import com.example.Commodity_Admin_AI.dto.ChatRequest;
import com.example.Commodity_Admin_AI.dto.ChatResponse;
import com.example.Commodity_Admin_AI.dto.NegotiationDto;
import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class BidsAndNegotiationsChatbotTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RagService ragService;

    @Mock
    private GeminiService geminiService;

    @Mock
    private MarketplaceDataService marketplaceDataService;

    private IntentService intentService;
    private AdminChatbotService adminChatbotService;
    private ChatSession testSession;
    private List<BidDto> sampleBids;
    private List<NegotiationDto> sampleNegotiations;

    @BeforeEach
    void setUp() {
        testSession = new ChatSession("admin");
        testSession.setId(400L);

        intentService = new IntentService();

        adminChatbotService = new AdminChatbotService(
                chatSessionRepository,
                chatMessageRepository,
                intentService,
                ragService,
                geminiService,
                marketplaceDataService,
                10
        );

        when(chatSessionRepository.findById(400L)).thenReturn(java.util.Optional.of(testSession));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));
        when(marketplaceDataService.isConfigured()).thenReturn(true);

        // Build sample data matching TradeX live backend
        sampleBids = new ArrayList<>();
        BidDto b1 = new BidDto();
        b1.setId(1L);
        b1.setBidNumber("BID-1792400918453");
        b1.setCommodityName("Soybean");
        b1.setMaskedBidderId("BDR-2");
        b1.setBidRate(new BigDecimal("4450.0000"));
        b1.setCounterRate(null);
        b1.setQuantity(new BigDecimal("100.0000"));
        b1.setDestination("Pune");
        b1.setBidType("BUY");
        b1.setRemarks("Urgent shipment required");
        b1.setStatus("PENDING");
        b1.setCreatedAt("2026-10-19T14:38:38.463436");
        b1.setUpdatedAt("2026-10-19T14:38:38.463436");
        sampleBids.add(b1);

        sampleNegotiations = new ArrayList<>();

        when(marketplaceDataService.fetchBids()).thenReturn(sampleBids);
        when(marketplaceDataService.fetchNegotiations()).thenReturn(sampleNegotiations);
    }

    @Test
    @DisplayName("Question 1: Pending bids count with diverse phrasings (English, Marathi, Hindi)")
    void testQuestion1_PendingBidsCount_MultiplePhrasings() {
        String[] queries = {
                "How many bids are pending?",
                "Are there any pending bids?",
                "Count pending bids",
                "Pending bids count",
                "Number of bids awaiting review",
                "Any pending bids in the market?",
                "किती बिड्स पेंडिंग आहेत?",
                "लंबित बोलियां कितनी हैं?"
        };

        for (String q : queries) {
            ChatRequest req = new ChatRequest(testSession.getId(), q);
            ChatResponse res = adminChatbotService.processChat(req);

            assertNotNull(res);
            assertEquals("BIDS", res.getDisplayType(), "Display type should be BIDS for query: " + q);
            assertTrue(res.getMessage().contains("1 pending bid"), "Should state 1 pending bid for query: " + q);
            assertTrue(res.getMessage().contains("BID-1792400918453"), "Should mention BID-1792400918453 for query: " + q);
            assertTrue(res.getMessage().contains("Soybean"), "Should mention Soybean for query: " + q);
            assertFalse(res.getMessage().contains("temporary communication issue"), "No communication error for query: " + q);
        }
    }

    @Test
    @DisplayName("Question 2: All bids listing with diverse phrasings")
    void testQuestion2_AllBidsListing_MultiplePhrasings() {
        String[] queries = {
                "Show me all bids.",
                "List every bid.",
                "Show all bids in the marketplace",
                "Display all bids",
                "What bids have been placed?",
                "सर्व बिड्स दाखवा",
                "सभी बिड्स दिखाएं"
        };

        for (String q : queries) {
            ChatRequest req = new ChatRequest(testSession.getId(), q);
            ChatResponse res = adminChatbotService.processChat(req);

            assertNotNull(res);
            assertEquals("BIDS", res.getDisplayType());
            assertTrue(res.getMessage().contains("BID-1792400918453"), "Should contain bid number for query: " + q);
            assertTrue(res.getMessage().contains("4,450"), "Should contain formatted rate ₹4,450 for query: " + q);
            assertTrue(res.getMessage().contains("100 Quintal"), "Should contain formatted quantity for query: " + q);
            assertNotNull(res.getBids());
            assertEquals(1, res.getBids().size());
        }
    }

    @Test
    @DisplayName("Question 3: Highest rate bid with diverse phrasings")
    void testQuestion3_HighestRateBid_MultiplePhrasings() {
        String[] queries = {
                "Which bid has the highest rate?",
                "What is the highest bid?",
                "Show highest bid",
                "Highest rate bid in marketplace",
                "कोणत्या बिडचा दर सर्वाधिक आहे?",
                "किस बिड का रेट सबसे ज्यादा है?"
        };

        for (String q : queries) {
            ChatRequest req = new ChatRequest(testSession.getId(), q);
            ChatResponse res = adminChatbotService.processChat(req);

            assertNotNull(res);
            assertEquals("BIDS", res.getDisplayType());
            assertTrue(res.getMessage().contains("BID-1792400918453"), "Should mention highest bid number for query: " + q);
            assertTrue(res.getMessage().contains("4,450 per Quintal"), "Should mention ₹4,450 per Quintal for query: " + q);
            assertTrue(res.getMessage().contains("Soybean"), "Should mention commodity for query: " + q);
        }
    }

    @Test
    @DisplayName("Question 4: Latest counter-offer with diverse phrasings (accurately reporting none submitted)")
    void testQuestion4_LatestCounterOffer_MultiplePhrasings() {
        String[] queries = {
                "What is the latest counter-offer?",
                "Show recent counter-offers",
                "Has any counter-offer been made?",
                "Are there any counter offers?",
                "नवीनतम काउंटर ऑफर काय आहे?",
                "लेटेस्ट काउंटर ऑफर क्या है?"
        };

        for (String q : queries) {
            ChatRequest req = new ChatRequest(testSession.getId(), q);
            ChatResponse res = adminChatbotService.processChat(req);

            assertNotNull(res);
            assertTrue(res.getMessage().toLowerCase().contains("no counter-offer")
                            || res.getMessage().contains("कोणतीही") || res.getMessage().contains("कोई"),
                    "Should explain no counter-offers exist for query: " + q);
            assertTrue(res.getMessage().contains("BID-1792400918453"), "Should refer to the pending bid for context: " + q);
        }
    }

    @Test
    @DisplayName("Question 5: Active negotiations with diverse phrasings (accurately reporting none open)")
    void testQuestion5_ActiveNegotiations_MultiplePhrasings() {
        String[] queries = {
                "Are there any active negotiations?",
                "Show active negotiations.",
                "List current negotiations.",
                "How many active negotiations are there?",
                "काही सक्रिय निगोशिएशन्स चालू आहेत का?",
                "क्या कोई सक्रिय बातचीत चल रही है?"
        };

        for (String q : queries) {
            ChatRequest req = new ChatRequest(testSession.getId(), q);
            ChatResponse res = adminChatbotService.processChat(req);

            assertNotNull(res);
            assertEquals("NEGOTIATIONS", res.getDisplayType());
            assertTrue(res.getMessage().toLowerCase().contains("no active negotiations")
                            || res.getMessage().contains("कोणतीही") || res.getMessage().contains("कोई"),
                    "Should explain no active negotiations for query: " + q);
        }
    }

    @Test
    @DisplayName("Question 6: Negotiations needing a response with diverse phrasings (and pending bids reference)")
    void testQuestion6_NegotiationsNeedingResponse_MultiplePhrasings() {
        String[] queries = {
                "Which negotiations need a response?",
                "Negotiations awaiting action",
                "Negotiations waiting for reply",
                "Are there any negotiations needing a response?",
                "कोणत्या निगोशिएशन्सना प्रतिसाद देणे आवश्यक आहे?"
        };

        for (String q : queries) {
            ChatRequest req = new ChatRequest(testSession.getId(), q);
            ChatResponse res = adminChatbotService.processChat(req);

            assertNotNull(res);
            assertTrue(res.getMessage().contains("no active negotiation threads"), "Should clarify no open threads for query: " + q);
            assertTrue(res.getMessage().contains("BID-1792400918453"), "Should highlight pending bid awaiting seller response for query: " + q);
            assertTrue(res.getMessage().contains("PENDING"), "Should specify PENDING status for query: " + q);
        }
    }

    @Test
    @DisplayName("Question 7: Bids for a specified commodity (Soybean vs Maize)")
    void testQuestion7_BidsForSpecifiedCommodity_MultiplePhrasings() {
        // Soybean bids
        ChatRequest req1 = new ChatRequest(testSession.getId(), "Show bids for Soybean.");
        ChatResponse res1 = adminChatbotService.processChat(req1);
        assertNotNull(res1);
        assertEquals("BIDS", res1.getDisplayType());
        assertTrue(res1.getMessage().contains("BID-1792400918453"));
        assertTrue(res1.getMessage().contains("Soybean"));
        assertEquals(1, res1.getBids().size());

        // Soybean bids variation
        ChatRequest req2 = new ChatRequest(testSession.getId(), "List all bids for Soybean");
        ChatResponse res2 = adminChatbotService.processChat(req2);
        assertTrue(res2.getMessage().contains("BID-1792400918453"));

        // Maize bids (none exist)
        ChatRequest req3 = new ChatRequest(testSession.getId(), "Are there bids for Maize?");
        ChatResponse res3 = adminChatbotService.processChat(req3);
        assertTrue(res3.getMessage().contains("no bids") || res3.getMessage().contains("0 bids"));
        assertTrue(res3.getMessage().contains("MAIZE") || res3.getMessage().contains("Maize"));
    }

    @Test
    @DisplayName("Question 8: Bid status and details by exact number and 'this bid' follow-up")
    void testQuestion8_BidStatusAndDetailsById_MultiplePhrasings() {
        // Exact BID-1792400918453
        ChatRequest req1 = new ChatRequest(testSession.getId(), "Show details of bid BID-1792400918453");
        ChatResponse res1 = adminChatbotService.processChat(req1);
        assertNotNull(res1);
        assertTrue(res1.getMessage().contains("BID-1792400918453"));
        assertTrue(res1.getMessage().contains("Soybean"));
        assertTrue(res1.getMessage().contains("4,450"));
        assertTrue(res1.getMessage().contains("BDR-2"));
        assertTrue(res1.getMessage().contains("PENDING"));
        assertTrue(res1.getMessage().contains("Urgent shipment required"));

        // Follow-up: "Tell me the status of this bid."
        ChatRequest req2 = new ChatRequest(testSession.getId(), "Tell me the status of this bid.");
        ChatResponse res2 = adminChatbotService.processChat(req2);
        assertNotNull(res2);
        assertTrue(res2.getMessage().contains("BID-1792400918453"));
        assertTrue(res2.getMessage().contains("PENDING"));

        // By ID number: "Status of BID-1"
        ChatRequest req3 = new ChatRequest(testSession.getId(), "Status of BID-1");
        ChatResponse res3 = adminChatbotService.processChat(req3);
        assertNotNull(res3);
        assertTrue(res3.getMessage().contains("BID-1792400918453"));
    }

    @Test
    @DisplayName("Question 9: Bids for specified location (Pune vs Sangli)")
    void testQuestion9_BidsForLocation_MultiplePhrasings() {
        // Pending bids for Pune
        ChatRequest req1 = new ChatRequest(testSession.getId(), "Show pending bids for Pune.");
        ChatResponse res1 = adminChatbotService.processChat(req1);
        assertNotNull(res1);
        assertTrue(res1.getMessage().contains("Pune"));
        assertTrue(res1.getMessage().contains("BID-1792400918453"));
        assertEquals(1, res1.getBids().size());

        // Bids for Pune
        ChatRequest req2 = new ChatRequest(testSession.getId(), "Show bids for Pune");
        ChatResponse res2 = adminChatbotService.processChat(req2);
        assertTrue(res2.getMessage().contains("Pune"));
        assertTrue(res2.getMessage().contains("BID-1792400918453"));

        // Bids for Sangli (none exist)
        ChatRequest req3 = new ChatRequest(testSession.getId(), "Are there bids for Sangli?");
        ChatResponse res3 = adminChatbotService.processChat(req3);
        assertTrue(res3.getMessage().contains("0 bids") || res3.getMessage().contains("no bids"));
        assertTrue(res3.getMessage().contains("Sangli"));
    }

    @Test
    @DisplayName("Empty and Error handling: When data is empty or API fails, explains situation clearly")
    void testEmptyAndErrorHandling() {
        // Empty bids and negotiations
        when(marketplaceDataService.fetchBids()).thenReturn(Collections.emptyList());
        when(marketplaceDataService.fetchNegotiations()).thenReturn(Collections.emptyList());

        ChatRequest reqEmpty = new ChatRequest(testSession.getId(), "How many bids are pending?");
        ChatResponse resEmpty = adminChatbotService.processChat(reqEmpty);
        assertNotNull(resEmpty);
        assertTrue(resEmpty.getMessage().contains("0 pending bids") || resEmpty.getMessage().contains("no bids"));

        // Service unconfigured
        when(marketplaceDataService.isConfigured()).thenReturn(false);
        ChatRequest reqUnavail = new ChatRequest(testSession.getId(), "Show me all bids.");
        ChatResponse resUnavail = adminChatbotService.processChat(reqUnavail);
        assertNotNull(resUnavail);
        assertTrue(resUnavail.getMessage().toLowerCase().contains("unavailable"));
    }

    @Test
    @DisplayName("Requirement 1: Show bids created today uses by-date endpoint and excludes older bids")
    void testRequirement1_ShowBidsCreatedToday_ExcludesOlderBids() {
        // Today's bids list excludes older bid BID-1792400918453 from 2026-10-19
        List<BidDto> todayBids = new ArrayList<>();
        BidDto bToday1 = new BidDto();
        bToday1.setId(2L);
        bToday1.setBidNumber("BID-1792653528878");
        bToday1.setCommodityName("Soybean");
        bToday1.setMaskedBidderId("BDR-1");
        bToday1.setBidRate(new BigDecimal("4450.0000"));
        bToday1.setQuantity(new BigDecimal("100.0000"));
        bToday1.setDestination("Pune");
        bToday1.setStatus("PENDING");
        bToday1.setCreatedAt("2026-10-22T12:48:48.955828");
        todayBids.add(bToday1);

        BidDto bToday2 = new BidDto();
        bToday2.setId(9L);
        bToday2.setBidNumber("BID-1792654013980");
        bToday2.setCommodityName("Soybean");
        bToday2.setMaskedBidderId("BDR-1");
        bToday2.setBidRate(new BigDecimal("4400.0000"));
        bToday2.setQuantity(new BigDecimal("200.0000"));
        bToday2.setDestination("Mumbai");
        bToday2.setStatus("PENDING");
        bToday2.setCreatedAt("2026-10-22T12:56:53.987956");
        todayBids.add(bToday2);

        when(marketplaceDataService.fetchBidsByDate(any())).thenReturn(todayBids);

        String[] queries = {
                "Show bids created today",
                "Show bids created today.",
                "Bids created today",
                "Show today's bids",
                "List bids created today"
        };

        for (String q : queries) {
            ChatRequest req = new ChatRequest(testSession.getId(), q);
            ChatResponse res = adminChatbotService.processChat(req);

            assertNotNull(res);
            assertEquals("BIDS", res.getDisplayType());
            assertTrue(res.getMessage().contains("BID-1792653528878"), "Should contain today's bid BID-1792653528878 for query: " + q);
            assertTrue(res.getMessage().contains("BID-1792654013980"), "Should contain today's bid BID-1792654013980 for query: " + q);
            assertFalse(res.getMessage().contains("BID-1792400918453"), "Should exclude older bid BID-1792400918453 for query: " + q);
            assertNotNull(res.getBids());
            assertEquals(2, res.getBids().size());
        }
    }

    @Test
    @DisplayName("Requirement 2: Which bid has the highest offer rate returns all bids tied at maximum numeric bidRate")
    void testRequirement2_HighestOfferRate_ReturnsAllTiedBids() {
        List<BidDto> bidsWithTies = new ArrayList<>();

        // Tie 1: 4450.0000
        BidDto b1 = new BidDto();
        b1.setId(1L);
        b1.setBidNumber("BID-1792400918453");
        b1.setCommodityName("Soybean");
        b1.setMaskedBidderId("BDR-2");
        b1.setBidRate(new BigDecimal("4450.0000"));
        b1.setQuantity(new BigDecimal("100.0000"));
        b1.setDestination("Pune");
        b1.setStatus("PENDING");
        b1.setRemarks("Urgent shipment required");
        bidsWithTies.add(b1);

        // Tie 2: 4450.0000
        BidDto b2 = new BidDto();
        b2.setId(2L);
        b2.setBidNumber("BID-1792653528878");
        b2.setCommodityName("Soybean");
        b2.setMaskedBidderId("BDR-1");
        b2.setBidRate(new BigDecimal("4450.0000"));
        b2.setQuantity(new BigDecimal("100.0000"));
        b2.setDestination("Pune");
        b2.setStatus("PENDING");
        b2.setRemarks("Urgent shipment required");
        bidsWithTies.add(b2);

        // Lower bid: 4200.0000
        BidDto b3 = new BidDto();
        b3.setId(3L);
        b3.setBidNumber("BID-1792653727870");
        b3.setCommodityName("Soybean");
        b3.setMaskedBidderId("BDR-1");
        b3.setBidRate(new BigDecimal("4200.0000"));
        b3.setQuantity(new BigDecimal("50.0000"));
        b3.setDestination("Pune");
        b3.setStatus("PENDING");
        bidsWithTies.add(b3);

        when(marketplaceDataService.fetchBids()).thenReturn(bidsWithTies);

        String[] queries = {
                "Which bid has the highest offer rate?",
                "Which bid has the highest rate?",
                "What is the highest bid?"
        };

        for (String q : queries) {
            ChatRequest req = new ChatRequest(testSession.getId(), q);
            ChatResponse res = adminChatbotService.processChat(req);

            assertNotNull(res);
            assertEquals("BIDS", res.getDisplayType());
            assertTrue(res.getMessage().contains("tied for the highest offer rate") || res.getMessage().contains("2 bids tied"),
                    "Should indicate tied bids for query: " + q);
            assertTrue(res.getMessage().contains("BID-1792400918453"), "Should mention tied BID-1792400918453 for query: " + q);
            assertTrue(res.getMessage().contains("BID-1792653528878"), "Should mention tied BID-1792653528878 for query: " + q);
            assertFalse(res.getMessage().contains("BID-1792653727870"), "Should not include lower bid BID-1792653727870 as highest for query: " + q);
            assertTrue(res.getMessage().contains("4,450 per Quintal"), "Should mention maximum rate ₹4,450 per Quintal for query: " + q);

            assertNotNull(res.getBids());
            assertEquals(2, res.getBids().size(), "ChatResponse bids should contain both tied bids for query: " + q);
        }
    }
}
