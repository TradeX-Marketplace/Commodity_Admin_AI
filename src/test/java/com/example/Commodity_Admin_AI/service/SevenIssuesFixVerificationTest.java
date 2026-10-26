package com.example.Commodity_Admin_AI.service;

import com.example.Commodity_Admin_AI.ai.GeminiService;
import com.example.Commodity_Admin_AI.ai.RagService;
import com.example.Commodity_Admin_AI.dto.*;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SevenIssuesFixVerificationTest {

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

    private List<BuyerDemandDto> baselineDemands;
    private List<SupplyDto> baselineSupplies;
    private List<BidDto> baselineBids;
    private PartiesResultDto baselineParties;

    @BeforeEach
    void setUp() {
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

        testSession = new ChatSession();
        testSession.setId(100L);

        // Ground-truth TradeX demands (4 active demands)
        baselineDemands = new ArrayList<>();
        BuyerDemandDto d1 = new BuyerDemandDto(1L, "K.K.CANVANSSING AGENT Verified Buyer [West]", 1L, "Wheat", 100.0, "Quintal", "Pune", "2026-11-15", "ACTIVE", "2026-10-10");
        d1.setDemandNumber("DEM-1792400811111");
        d1.setTargetRate(27000.0);
        d1.setDestination("Pune");
        d1.setValidTill("2026-11-15");
        baselineDemands.add(d1);

        BuyerDemandDto d2 = new BuyerDemandDto(2L, "K.K.CANVANSSING AGENT Verified Buyer [West]", 2L, "MAIZE", 150.0, "Quintal", "Kolhapur", "2026-11-20", "ACTIVE", "2026-10-10");
        d2.setDemandNumber("DEM-1792400822222");
        d2.setTargetRate(21000.0);
        d2.setDestination("Kolhapur");
        d2.setValidTill("2026-11-20");
        baselineDemands.add(d2);

        BuyerDemandDto d3 = new BuyerDemandDto(3L, "K.K.CANVANSSING AGENT Verified Buyer [West]", 3L, "MAIZEW", 120.0, "Quintal", "Sangli", "2026-11-25", "ACTIVE", "2026-10-10");
        d3.setDemandNumber("DEM-1792400874398");
        d3.setTargetRate(22000.0);
        d3.setDestination("Sangli");
        d3.setValidTill("2026-11-25");
        baselineDemands.add(d3);

        BuyerDemandDto d4 = new BuyerDemandDto(4L, "K.K.CANVANSSING AGENT Verified Buyer [West]", 4L, "Soybean", 200.0, "Quintal", "Pune", "2026-11-30", "ACTIVE", "2026-10-10");
        d4.setDemandNumber("DEM-1792400902263");
        d4.setTargetRate(4400.0);
        d4.setDestination("Pune");
        d4.setValidTill("2026-11-30");
        baselineDemands.add(d4);

        // Ground-truth TradeX supplies (11 active supplies, total 6,100 Qtl)
        baselineSupplies = new ArrayList<>();
        // 2 Unexpired Soybean supplies
        SupplyDto s1 = new SupplyDto();
        s1.setId(1L); s1.setSupplyNumber("SUP-1792400860244"); s1.setCommodityName("Soybean"); s1.setAvailableQuantity(500.0);
        s1.setUnit("Quintal"); s1.setAskingRate(4500.0); s1.setLocation("Sangli"); s1.setPartyName("Sangli Agro Traders"); s1.setValidTill("2026-11-30"); s1.setStatus("ACTIVE");
        baselineSupplies.add(s1);

        SupplyDto s2 = new SupplyDto();
        s2.setId(2L); s2.setSupplyNumber("SUP-1792572585508"); s2.setCommodityName("Soybean"); s2.setAvailableQuantity(500.0);
        s2.setUnit("Quintal"); s2.setAskingRate(4500.0); s2.setLocation("Sangli"); s2.setPartyName("Sangli Agro Traders"); s2.setValidTill("2026-10-31"); s2.setStatus("ACTIVE");
        baselineSupplies.add(s2);

        // 6 Expired Soybean supplies (validTill before 2026-10-26)
        String[] expiredDates = {"2026-10-16", "2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20", "2026-10-21"};
        String[] expiredNums = {"SUP-1792572661871", "SUP-1792572691806", "SUP-1792572709817", "SUP-1792572721202", "SUP-1792572733646", "SUP-1792572748561"};
        double[] expiredRates = {4350.0, 4400.0, 4350.0, 4300.0, 4400.0, 4400.0};
        for (int i = 0; i < expiredNums.length; i++) {
            SupplyDto se = new SupplyDto();
            se.setId(10L + i); se.setSupplyNumber(expiredNums[i]); se.setCommodityName("Soybean"); se.setAvailableQuantity(500.0);
            se.setUnit("Quintal"); se.setAskingRate(expiredRates[i]); se.setLocation("Sangli"); se.setPartyName("Sangli Agro Traders");
            se.setValidTill(expiredDates[i]); se.setStatus("ACTIVE");
            baselineSupplies.add(se);
        }

        // 3 Other commodities supplies
        SupplyDto sm = new SupplyDto();
        sm.setId(3L); sm.setSupplyNumber("SUP-1792400898516"); sm.setCommodityName("MAIZE"); sm.setAvailableQuantity(600.0);
        sm.setUnit("Quintal"); sm.setAskingRate(22000.0); sm.setLocation("Sangli"); sm.setPartyName("Sangli Agro Traders"); sm.setValidTill("2026-11-30"); sm.setStatus("ACTIVE");
        baselineSupplies.add(sm);

        SupplyDto smw = new SupplyDto();
        smw.setId(4L); smw.setSupplyNumber("SUP-1792400898517"); smw.setCommodityName("MAIZEW"); smw.setAvailableQuantity(700.0);
        smw.setUnit("Quintal"); smw.setAskingRate(22500.0); smw.setLocation("Kolhapur"); smw.setPartyName("Sangli Agro Traders"); smw.setValidTill("2026-11-30"); smw.setStatus("ACTIVE");
        baselineSupplies.add(smw);

        SupplyDto sw = new SupplyDto();
        sw.setId(5L); sw.setSupplyNumber("SUP-1792400898518"); sw.setCommodityName("Wheat"); sw.setAvailableQuantity(800.0);
        sw.setUnit("Quintal"); sw.setAskingRate(28000.0); sw.setLocation("Sangli"); sw.setPartyName("Sangli Agro Traders"); sw.setValidTill("2026-11-30"); sw.setStatus("ACTIVE");
        baselineSupplies.add(sw);

        // Ground-truth TradeX bids (9 pending bids)
        baselineBids = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            BidDto b = new BidDto();
            b.setId((long) i);
            b.setBidNumber("BID-1792" + (400918450L + i));
            b.setCommodityName("Soybean");
            b.setBidRate(BigDecimal.valueOf(4200 + i * 25));
            b.setQuantity(BigDecimal.valueOf(100));
            b.setStatus("PENDING");
            b.setMaskedBidderId("K.K.CANVANSSING AGENT Verified Buyer [West]");
            baselineBids.add(b);
        }

        // Ground-truth TradeX parties (12 parties)
        List<PartyDto> partyList = new ArrayList<>();
        PartyDto p1 = new PartyDto();
        p1.setId(1L); p1.setPartyCode("PRT-0001"); p1.setCompanyName("Sangli Agro Traders"); p1.setCity("Sangli"); p1.setActive(true);
        p1.setRoles(List.of("TRADER"));
        partyList.add(p1);
        for (int i = 2; i <= 12; i++) {
            PartyDto p = new PartyDto();
            p.setId((long) i); p.setPartyCode(String.format("PRT-%04d", i)); p.setCompanyName("Merchant " + i); p.setCity("Pune"); p.setActive(true);
            partyList.add(p);
        }
        baselineParties = new PartiesResultDto(partyList, 12L, 1, 0, 12);
    }

    // =========================================================================
    // Q13: Buyer-demand location filtering
    // =========================================================================
    @Test
    @DisplayName("Q13: Filter buyer demands by destination/location - Sangli returns 1, Nagpur returns 0")
    void testQ13_BuyerDemandsLocationFiltering() {
        // Query Sangli: Should return 1 demand (MAIZEW 120 Qtl destination Sangli)
        String respSangli = adminChatbotService.generateActiveDemandsCountResponse(baselineDemands, "Sangli", "en");
        assertNotNull(respSangli);
        assertTrue(respSangli.contains("1 active buyer demand record"));
        assertTrue(respSangli.contains("Sangli"));
        assertTrue(respSangli.contains("MAIZEW"));

        // Query location with 0 demands: e.g. Nagpur
        String respNagpur = adminChatbotService.generateActiveDemandsCountResponse(baselineDemands, "Nagpur", "en");
        assertNotNull(respNagpur);
        assertTrue(respNagpur.contains("0 active buyer demand records registered in Nagpur"));

        // Verify filterDemandsForResponse filters by location
        List<BuyerDemandDto> filteredSangli = adminChatbotService.filterDemandsForResponse(
                baselineDemands, "How many buyer demands are registered in Sangli?", null, "Sangli");
        assertEquals(1, filteredSangli.size());
        assertEquals("MAIZEW", filteredSangli.get(0).getCommodityName());

        List<BuyerDemandDto> filteredNagpur = adminChatbotService.filterDemandsForResponse(
                baselineDemands, "How many buyer demands are registered in Nagpur?", null, "Nagpur");
        assertEquals(0, filteredNagpur.size());
    }

    // =========================================================================
    // Q15: Include actual target prices and units in demand response
    // =========================================================================
    @Test
    @DisplayName("Q15: Include actual target prices and units in demand response and DTO")
    void testQ15_IncludeActualTargetPricesAndUnits() {
        // Check DTO getTargetPrice() and serialization
        BuyerDemandDto d = baselineDemands.get(0);
        assertEquals(27000.0, d.getTargetPrice());
        assertEquals(27000.0, d.getTargetRate());

        // Check summary table contains Target Price column and values
        String summaryEn = adminChatbotService.generateBuyerDemandSummary(baselineDemands, "en", null, null);
        assertNotNull(summaryEn);
        assertTrue(summaryEn.contains("| Target Price |"));
        assertTrue(summaryEn.contains("₹27,000 / Quintal"));
        assertTrue(summaryEn.contains("₹21,000 / Quintal"));
        assertTrue(summaryEn.contains("₹22,000 / Quintal"));
        assertTrue(summaryEn.contains("₹4,400 / Quintal"));

        // Check Marathi and Hindi translations
        String summaryMr = adminChatbotService.generateBuyerDemandSummary(baselineDemands, "mr", null, null);
        assertTrue(summaryMr.contains("टार्गेट रेट"));

        String summaryHi = adminChatbotService.generateBuyerDemandSummary(baselineDemands, "hi", null, null);
        assertTrue(summaryHi.contains("टारगेट रेट"));
    }

    // =========================================================================
    // Q18: Match active Soybean demands against real supplies
    // =========================================================================
    @Test
    @DisplayName("Q18: Match active Soybean demands against actual supplies across 5 criteria")
    void testQ18_MatchActiveSoybeanDemandsAgainstSupplies() {
        String resp = adminChatbotService.generateMatchingDemandsToSuppliesResponse(
                baselineDemands, baselineSupplies, "Find supplies matching active Soybean demands.", "en");

        assertNotNull(resp);
        // 1. Demand requirements
        assertTrue(resp.contains("200 Quintal"));
        assertTrue(resp.contains("₹4,400 / Quintal"));
        assertTrue(resp.contains("Pune"));

        // 2. 5 Supported matching criteria
        assertTrue(resp.contains("Commodity Match"));
        assertTrue(resp.contains("Quantity Sufficiency"));
        assertTrue(resp.contains("Date Validity"));
        assertTrue(resp.contains("Price / Rate Compatibility"));
        assertTrue(resp.contains("Location Compatibility"));

        // 3. 0 Exact matches & candidate unexpired supplies
        assertTrue(resp.contains("0 exact matches"));
        assertTrue(resp.contains("SUP-1792400860244"));
        assertTrue(resp.contains("SUP-1792572585508"));
        assertTrue(resp.contains("Partial Candidate"));
        assertTrue(resp.contains("+₹100 / Qtl"));

        // 4. Mentions expired listings
        assertTrue(resp.contains("expired"));
    }

    // =========================================================================
    // Q24: Filter supplies by actual validity dates and current date
    // =========================================================================
    @Test
    @DisplayName("Q24: Filter supplies whose validTill date is before current date")
    void testQ24_FilterSuppliesByActualValidityDates() {
        String question = "Which supplies have expired validity dates?";
        assertTrue(adminChatbotService.isExpiredSuppliesQuestion(question));

        String resp = adminChatbotService.generateExpiredSuppliesResponse(baselineSupplies, "en");
        assertNotNull(resp);
        assertTrue(resp.contains("6 supply listings have expired validity dates"));
        assertTrue(resp.contains("SUP-1792572661871"));
        assertTrue(resp.contains("SUP-1792572691806"));
        assertTrue(resp.contains("SUP-1792572709817"));
        assertTrue(resp.contains("SUP-1792572721202"));
        assertTrue(resp.contains("SUP-1792572733646"));
        assertTrue(resp.contains("SUP-1792572748561"));

        // Unexpired listings must NOT be in expired table
        assertFalse(resp.contains("SUP-1792400860244 |"));
        assertFalse(resp.contains("SUP-1792572585508 |"));

        // Verify filterSuppliesForResponse returns the 6 expired supplies
        List<SupplyDto> expiredFiltered = adminChatbotService.filterSuppliesForResponse(baselineSupplies, question);
        assertEquals(6, expiredFiltered.size());
    }

    // =========================================================================
    // Q26: Dynamic highlights matching table counts without contradictions
    // =========================================================================
    @Test
    @DisplayName("Q26: Consistent available quantities and dynamic highlights without contradictory statements")
    void testQ26_ConsistentAvailableQuantitiesAndHighlights() {
        String resp = adminChatbotService.generateCompareAvailableQuantitiesResponse(baselineSupplies, "en");
        assertNotNull(resp);

        // Must contain table entries
        assertTrue(resp.contains("Soybean"));
        assertTrue(resp.contains("MAIZE"));
        assertTrue(resp.contains("MAIZEW"));
        assertTrue(resp.contains("Wheat"));

        // Dynamic highlights must match computed numbers
        assertTrue(resp.contains("4,000 Quintal available across 8 active listings"));
        assertTrue(resp.contains("600 Quintal available across 1 active listing"));
        assertTrue(resp.contains("700 Quintal available across 1 active listing"));
        assertTrue(resp.contains("800 Quintal available across 1 active listing"));
        assertTrue(resp.contains("6,100 Quintal available across 11 active supply listings"));

        // MUST NOT contain old contradictory hardcoded claims
        assertFalse(resp.contains("Currently 0 active seller listings in the marketplace"));
        assertFalse(resp.contains("500.0 Quintals available across 11 active listings"));
        assertFalse(resp.contains("Soybean currently accounts for 100%"));
    }

    // =========================================================================
    // Q31: Genuine negotiation rounds; never substitute initial bids
    // =========================================================================
    @Test
    @DisplayName("Q31: Return genuine negotiation rounds newest first; never substitute initial bids")
    void testQ31_NegotiationRoundsDoesNotSubstituteInitialBids() {
        String question = "Show me the latest negotiation rounds.";
        assertTrue(adminChatbotService.isNegotiationRoundsQuestion(question.toLowerCase()));

        // Case A: 0 negotiations in TradeX (current ground truth)
        String respZero = adminChatbotService.generateNegotiationRoundsResponse(null, baselineBids, "en");
        assertNotNull(respZero);
        assertTrue(respZero.contains("0 multi-round negotiation rounds"));
        assertTrue(respZero.contains("PENDING"));
        assertTrue(respZero.contains("Initial bids are recorded as standalone pending bids and are not substituted or counted as negotiation rounds"));

        // Bids filter must return empty list for negotiation rounds question when 0 rounds exist
        List<BidDto> responseBids = adminChatbotService.filterBidsForResponse(baselineBids, question);
        assertEquals(0, responseBids.size());

        // Case B: Genuine negotiation rounds exist -> newest first
        NegotiationDto n = new NegotiationDto();
        n.setId(1L);
        n.setNegotiationCode("NEG-1001");
        n.setCommodityName("Soybean");
        n.setQuantity(BigDecimal.valueOf(200));
        n.setCurrentOffer(BigDecimal.valueOf(4450));
        n.setBuyerOffer(BigDecimal.valueOf(4400));
        n.setSellerOffer(BigDecimal.valueOf(4500));
        n.setStatus("ACTIVE");

        NegotiationRoundDto r1 = new NegotiationRoundDto();
        r1.setRoundNumber(2);
        r1.setRate(BigDecimal.valueOf(4450));
        n.setRounds(List.of(r1));

        String respWithNeg = adminChatbotService.generateNegotiationRoundsResponse(List.of(n), baselineBids, "en");
        assertTrue(respWithNeg.contains("1 active negotiation round(s)"));
        assertTrue(respWithNeg.contains("NEG-1001"));
        assertTrue(respWithNeg.contains("₹4450"));
    }

    // =========================================================================
    // Q46: Report both buyer and supplier counts, distinguishing entities from listings
    // =========================================================================
    @Test
    @DisplayName("Q46: Report distinct active buyer and supplier entities separately from listings")
    void testQ46_ActiveBuyersAndSuppliersCountAndDistinction() {
        String question = "How many buyers and suppliers are active?";
        assertTrue(adminChatbotService.isHowManyBuyersAndSuppliersActiveQuestion(question.toLowerCase()));

        when(marketplaceDataService.fetchBuyerDemands()).thenReturn(baselineDemands);
        when(marketplaceDataService.fetchSupplies()).thenReturn(baselineSupplies);
        when(marketplaceDataService.fetchBids()).thenReturn(baselineBids);

        String resp = adminChatbotService.generateActiveBuyersAndSuppliersResponse(baselineParties.getParties(), "en");
        assertNotNull(resp);

        // Must report both Buyers and Suppliers
        assertTrue(resp.contains("1 active buyer entity"));
        assertTrue(resp.contains("1 active supplier entity"));
        assertTrue(resp.contains("K.K.CANVANSSING AGENT"));
        assertTrue(resp.contains("Sangli Agro Traders"));

        // Must distinguish entities from listings
        assertTrue(resp.contains("4 Active Demands"));
        assertTrue(resp.contains("9 Active Bids"));
        assertTrue(resp.contains("11 Active Supply Listings"));
        assertTrue(resp.contains("Distinction Between Registered Entities and Active Listings"));
        assertTrue(resp.contains("12 total registered party accounts"));
    }
}
