package com.example.Commodity_Admin_AI.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI RAG Advisor configuration for the unified PGVector VectorStore.
 * <p>
 * STEP 12 — RAG ADVISOR:
 * - Reuses the single existing PGVector / VectorStore system.
 * - Configures Spring AI's QuestionAnswerAdvisor with controlled search parameters and top-k limits.
 * - Ensures RAG retrieval remains strictly bounded, read-only, and factual without direct database access.
 * - Preserves the separation between KNOWLEDGE and MARKETPLACE_DATA metadata.
 */
@Configuration
public class RagAdvisorConfig {

    private static final Logger log = LoggerFactory.getLogger(RagAdvisorConfig.class);

    public static final String CONTROLLED_ADVISOR_TEMPLATE = """
            {query}

            === CONTROLLED CONTEXT START ===
            {question_answer_context}
            === CONTROLLED CONTEXT END ===

            Based strictly on the controlled context above and without inventing or hallucinating marketplace data, provide an accurate administrative response. If the requested information is not in the context, clearly inform the admin that the data is not available.
            """;

    @Value("${spring.ai.vectorstore.pgvector.top-k:4}")
    private int topK;

    /**
     * Declares the default QuestionAnswerAdvisor bean bound to the unified PGVector VectorStore.
     * Uses @ConditionalOnBean(VectorStore.class) so that test profiles or environments without
     * PGVector auto-configuration will cleanly skip bean creation without failure.
     *
     * @param vectorStore the unified VectorStore bean
     * @return configured QuestionAnswerAdvisor
     */
    @Bean
    @ConditionalOnBean(VectorStore.class)
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        log.info("Configuring Spring AI QuestionAnswerAdvisor using unified PGVector VectorStore (topK: {})", topK);

        SearchRequest searchRequest = SearchRequest.builder()
                .topK(topK > 0 ? topK : 4)
                .build();

        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .promptTemplate(new PromptTemplate(CONTROLLED_ADVISOR_TEMPLATE))
                .build();
    }

    public int getTopK() {
        return topK;
    }
}
