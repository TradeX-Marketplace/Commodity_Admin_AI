package com.example.Commodity_Admin_AI.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceDataSyncSchedulerTest {

    @Mock
    private MarketplaceDataSyncService marketplaceDataSyncService;

    private MarketplaceDataSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MarketplaceDataSyncScheduler(marketplaceDataSyncService);
    }

    @Test
    @DisplayName("run executes initial marketplace sync and indexing on application startup")
    void testStartupRun_ExecutesSync() {
        when(marketplaceDataSyncService.syncAndIndexMarketRates()).thenReturn(5);

        scheduler.run(new DefaultApplicationArguments(new String[0]));

        verify(marketplaceDataSyncService, times(1)).syncAndIndexMarketRates();
        assertFalse(scheduler.isSyncInProgress());
    }

    @Test
    @DisplayName("scheduledSync executes periodic marketplace sync and indexing")
    void testScheduledSync_ExecutesSync() {
        when(marketplaceDataSyncService.syncAndIndexMarketRates()).thenReturn(3);

        scheduler.scheduledSync();

        verify(marketplaceDataSyncService, times(1)).syncAndIndexMarketRates();
        assertFalse(scheduler.isSyncInProgress());
    }

    @Test
    @DisplayName("syncMarketplaceData safely catches exceptions and returns 0 without crashing")
    void testSyncMarketplaceData_SafeExceptionHandling() {
        when(marketplaceDataSyncService.syncAndIndexMarketRates())
                .thenThrow(new RuntimeException("Simulated REST API network timeout"));

        int result = scheduler.syncMarketplaceData();

        assertEquals(0, result);
        assertFalse(scheduler.isSyncInProgress(), "Sync status flag must be reset even after failure");
    }
}
