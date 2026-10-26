package com.example.Commodity_Admin_AI.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration holder for official data.gov.in AGMARKNET mandi price API.
 * Keeps credentials, endpoints, and timeouts externalized and configurable.
 */
@Component
public class DataGovInProperties {

    private final String baseUrl;
    private final String resourceId;
    private final String apiKey;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public DataGovInProperties(
            @Value("${external.data-gov-in.base-url:https://api.data.gov.in}") String baseUrl,
            @Value("${external.data-gov-in.resource-id:9ef84268-d588-465a-a308-a864a43d0070}") String resourceId,
            @Value("${external.data-gov-in.api-key:}") String apiKey,
            @Value("${external.data-gov-in.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${external.data-gov-in.read-timeout-ms:10000}") int readTimeoutMs) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://api.data.gov.in";
        this.resourceId = resourceId != null ? resourceId.trim() : "9ef84268-d588-465a-a308-a864a43d0070";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 5000;
        this.readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 10000;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
