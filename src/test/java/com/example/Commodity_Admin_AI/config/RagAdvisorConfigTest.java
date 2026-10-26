package com.example.Commodity_Admin_AI.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RagAdvisorConfigTest {

    @Mock
    private VectorStore vectorStore;

    @Test
    @DisplayName("Should successfully create QuestionAnswerAdvisor bean bound to VectorStore")
    void testQuestionAnswerAdvisorCreation() {
        RagAdvisorConfig config = new RagAdvisorConfig();
        ReflectionTestUtils.setField(config, "topK", 5);

        assertEquals(5, config.getTopK());

        QuestionAnswerAdvisor advisor = config.questionAnswerAdvisor(vectorStore);
        assertNotNull(advisor);
        assertTrue(advisor.getOrder() >= 0);
    }

    @Test
    @DisplayName("Should use fallback topK when configured value is zero or negative")
    void testQuestionAnswerAdvisorDefaultTopK() {
        RagAdvisorConfig config = new RagAdvisorConfig();
        ReflectionTestUtils.setField(config, "topK", 0);

        QuestionAnswerAdvisor advisor = config.questionAnswerAdvisor(vectorStore);
        assertNotNull(advisor);
    }
}
