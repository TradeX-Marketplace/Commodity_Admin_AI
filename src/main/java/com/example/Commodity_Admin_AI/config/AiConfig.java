package com.example.Commodity_Admin_AI.config;

import com.google.genai.errors.ClientException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;

/**
 * AI Configuration for Spring AI and Google GenAI.
 * <p>
 * Configures a controlled RetryTemplate for the ChatModel to prevent
 * uncontrolled 45-second retries when client quota (429) or client errors occur.
 */
@Configuration
public class AiConfig {

    @Bean
    public RetryTemplate geminiRetryTemplate() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(1)
                .timeout(Duration.ofSeconds(4))
                .excludes(ClientException.class)
                .build();
        return new RetryTemplate(retryPolicy);
    }
}
