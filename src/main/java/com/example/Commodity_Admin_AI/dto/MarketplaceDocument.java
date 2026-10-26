package com.example.Commodity_Admin_AI.dto;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a structured searchable document produced by converting
 * approved marketplace JSON data, prepared for vector embedding and PGVector storage in Step 10.
 */
public class MarketplaceDocument {

    private final String id;
    private final String content;
    private final Map<String, Object> metadata;

    public MarketplaceDocument(String content, Map<String, Object> metadata) {
        this(null, content, metadata);
    }

    public MarketplaceDocument(String id, String content, Map<String, Object> metadata) {
        this.id = id;
        this.content = content;
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "MarketplaceDocument{" +
                "id='" + id + '\'' +
                ", content='" + content + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
