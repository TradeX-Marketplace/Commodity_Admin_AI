package com.example.Commodity_Admin_AI.repository;

import com.example.Commodity_Admin_AI.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for ChatSession persistence and lookup.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /**
     * Find all chat sessions for a specific admin user, ordered by creation date descending.
     *
     * @param adminUserId identifier of the admin user
     * @return list of chat sessions
     */
    List<ChatSession> findByAdminUserIdOrderByCreatedAtDesc(String adminUserId);
}
