package com.example.Commodity_Admin_AI.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for Admin chatbot interactions.
 */
public class ChatRequest {

    private Long sessionId;

    @NotBlank(message = "Message must not be blank")
    private String message;

    private String language;

    public ChatRequest() {
    }

    public ChatRequest(String message) {
        this.message = message;
    }

    public ChatRequest(Long sessionId, String message) {
        this.sessionId = sessionId;
        this.message = message;
    }

    public ChatRequest(Long sessionId, String message, String language) {
        this.sessionId = sessionId;
        this.message = message;
        this.language = language;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
