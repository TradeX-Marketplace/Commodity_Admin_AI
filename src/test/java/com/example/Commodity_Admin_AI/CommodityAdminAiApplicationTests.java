package com.example.Commodity_Admin_AI;

import com.example.Commodity_Admin_AI.repository.ChatMessageRepository;
import com.example.Commodity_Admin_AI.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(CommodityAdminAiApplicationTests.MockRepositoryTestConfig.class)
class CommodityAdminAiApplicationTests {

    @TestConfiguration
    static class MockRepositoryTestConfig {
        @Bean
        public ChatSessionRepository chatSessionRepository() {
            return Mockito.mock(ChatSessionRepository.class);
        }

        @Bean
        public ChatMessageRepository chatMessageRepository() {
            return Mockito.mock(ChatMessageRepository.class);
        }
    }

    @Test
    void contextLoads() {
    }

}
