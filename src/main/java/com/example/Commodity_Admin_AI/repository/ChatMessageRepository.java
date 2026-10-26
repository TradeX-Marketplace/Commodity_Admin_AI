package com.example.Commodity_Admin_AI.repository;

import com.example.Commodity_Admin_AI.entity.ChatMessage;
import com.example.Commodity_Admin_AI.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for ChatMessage persistence and lookup.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Retrieve all messages for a specific session ordered chronologically.
     *
     * @param session the chat session
     * @return list of chat messages
     */
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);

    /**
     * Retrieve all messages for a specific session ID ordered chronologically.
     *
     * @param sessionId the ID of the chat session
     * @return list of chat messages
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
