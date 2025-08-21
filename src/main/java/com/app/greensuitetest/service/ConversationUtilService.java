package com.app.greensuitetest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
public class ConversationUtilService {

    private final Random random = new Random();

    /**
     * Generate a unique conversation ID to prevent memory mixing between users
     * Limited to 36 characters to fit database schema
     * Prioritizes persistent IDs that survive logout/login cycles
     */
    public String generateUniqueConversationId(String conversationId, String userId, String sessionId) {
        // If conversationId is explicitly provided, use it (truncate if needed)
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            String id = conversationId.trim();
            if (id.length() > 36) {
                id = id.substring(0, 36);
                log.debug("Truncated provided conversationId to: {}", id);
            }
            log.debug("Using provided conversationId: {}", id);
            return id;
        }
        
        // If userId is available, create user-specific conversation ID (persistent across sessions)
        if (userId != null && !userId.trim().isEmpty()) {
            String baseId = userId.trim();
            // Truncate baseId if too long to leave room for suffix
            if (baseId.length() > 20) {
                baseId = baseId.substring(0, 20);
            }
            
            // Create persistent conversation ID that doesn't change on logout/login
            String generatedId = baseId + "_persistent";
            
            // Final safety check
            if (generatedId.length() > 36) {
                generatedId = generatedId.substring(0, 36);
            }
            
            log.debug("Generated persistent user-based conversationId: {} for userId: {}", generatedId, userId);
            return generatedId;
        }
        
        // If sessionId is available but no userId, use session-based ID
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            String shortSessionId = sessionId.trim();
            if (shortSessionId.length() > 28) { // Leave room for "session_" prefix
                shortSessionId = shortSessionId.substring(0, 28);
            }
            String generatedId = "session_" + shortSessionId;
            log.debug("Generated session-based conversationId: {} for sessionId: {}", generatedId, sessionId);
            return generatedId;
        }
        
        // Fallback: create timestamp-based unique ID to prevent sharing
        long timestamp = System.currentTimeMillis();
        int randomSuffix = random.nextInt(1000);
        String generatedId = "anon_" + timestamp + "_" + randomSuffix;
        
        // Truncate if necessary
        if (generatedId.length() > 36) {
            generatedId = generatedId.substring(0, 36);
        }
        
        log.debug("Generated anonymous conversationId: {} (no userId or sessionId provided)", generatedId);
        return generatedId;
    }

    /**
     * Get a persistent conversation ID for a specific user
     * This ID will remain the same across login/logout cycles
     */
    public String getPersistentConversationId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            // If no userId, generate a random persistent ID
            long timestamp = System.currentTimeMillis();
            String generatedId = "guest_" + timestamp + "_" + random.nextInt(1000);
            if (generatedId.length() > 36) {
                generatedId = generatedId.substring(0, 36);
            }
            return generatedId;
        }
        
        String baseId = userId.trim();
        if (baseId.length() > 20) {
            baseId = baseId.substring(0, 20);
        }
        
        String persistentId = baseId + "_persistent";
        if (persistentId.length() > 36) {
            persistentId = persistentId.substring(0, 36);
        }
        
        log.debug("Generated persistent conversation ID: {} for user: {}", persistentId, userId);
        return persistentId;
    }
} 