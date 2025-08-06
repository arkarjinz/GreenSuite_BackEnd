package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.service.ConversationUtilService;
import com.app.greensuitetest.service.AIChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ai/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationUtilService conversationUtilService;
    private final ChatMemory chatMemory;
    private final AIChatService aiChatService;

    /**
     * Get or create persistent conversation ID for a user
     * Frontend expects: { conversationId: string, isNew: boolean }
     */
    @GetMapping("/persistent/{userId}")
    public ResponseEntity<Map<String, Object>> getPersistentConversationId(@PathVariable String userId) {
        try {
            log.debug("Getting persistent conversation ID for user: {}", userId);
            
            // Generate persistent conversation ID
            String conversationId = conversationUtilService.getPersistentConversationId(userId);
            
            // Check if conversation has existing history
            List<Message> existingHistory = chatMemory.get(conversationId);
            boolean isNew = existingHistory.isEmpty();
            
            log.debug("Persistent conversation ID: {} for user: {} (isNew: {})", conversationId, userId, isNew);
            
            Map<String, Object> response = new HashMap<>();
            response.put("conversationId", conversationId);
            response.put("isNew", isNew);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting persistent conversation ID for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get persistent conversation ID: " + e.getMessage()));
        }
    }

    /**
     * Create persistent conversation ID for a user (if it doesn't exist)
     */
    @PostMapping("/persistent/{userId}")
    public ResponseEntity<Map<String, Object>> createPersistentConversationId(@PathVariable String userId) {
        try {
            log.debug("Creating persistent conversation ID for user: {}", userId);
            
            String conversationId = conversationUtilService.getPersistentConversationId(userId);
            
            // Always treat POST as creating a new conversation
            Map<String, Object> response = new HashMap<>();
            response.put("conversationId", conversationId);
            response.put("isNew", true);
            
            log.debug("Created persistent conversation ID: {} for user: {}", conversationId, userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error creating persistent conversation ID for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to create persistent conversation ID: " + e.getMessage()));
        }
    }

    /**
     * Load chat history for a conversation
     * Frontend expects: { messages: [{ content, isUser, timestamp, id }] }
     */
    @GetMapping("/{conversationId}/history")
    public ResponseEntity<Map<String, Object>> getChatHistory(
            @PathVariable String conversationId,
            @RequestParam(required = false) String userId) {
        try {
            log.debug("Getting chat history for conversation: {} (user: {})", conversationId, userId);
            
            // Get chat history from memory
            List<Message> history = chatMemory.get(conversationId);
            
            if (history.isEmpty()) {
                log.debug("No chat history found for conversation: {}", conversationId);
                return ResponseEntity.ok(Map.of("messages", List.of()));
            }
            
            // Convert Spring AI messages to frontend format
            List<Map<String, Object>> messages = history.stream()
                .map(this::convertMessageToFrontendFormat)
                .collect(Collectors.toList());
            
            log.debug("Returning {} messages for conversation: {}", messages.size(), conversationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("messages", messages);
            response.put("conversationId", conversationId);
            response.put("messageCount", messages.size());
            response.put("lastActivity", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting chat history for conversation: {}", conversationId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Clear chat history for a conversation
     * Note: Main clear endpoint is at DELETE /api/ai/memory/{conversationId}
     * This endpoint provides an alternative path for frontend compatibility
     */
    @DeleteMapping("/{conversationId}/history")
    public ResponseEntity<ApiResponse> clearChatHistory(
            @PathVariable String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {
        try {
            log.debug("Clearing chat history for conversation: {} via /conversation endpoint", conversationId);
            
            ApiResponse response = aiChatService.clearChatHistory(conversationId, userId, sessionId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error clearing chat history for conversation: {}", conversationId, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to clear chat history: " + e.getMessage()));
        }
    }

    /**
     * Get conversation statistics
     */
    @GetMapping("/{conversationId}/stats")
    public ResponseEntity<Map<String, Object>> getConversationStats(@PathVariable String conversationId) {
        try {
            List<Message> history = chatMemory.get(conversationId);
            
            long userMessages = history.stream()
                .filter(msg -> msg.getClass().getSimpleName().contains("User"))
                .count();
            
            long assistantMessages = history.stream()
                .filter(msg -> msg.getClass().getSimpleName().contains("Assistant"))
                .count();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("conversationId", conversationId);
            stats.put("totalMessages", history.size());
            stats.put("userMessages", userMessages);
            stats.put("assistantMessages", assistantMessages);
            stats.put("hasHistory", !history.isEmpty());
            stats.put("lastActivity", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting conversation stats for: {}", conversationId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get conversation stats: " + e.getMessage()));
        }
    }

    /**
     * Convert Spring AI Message to frontend format
     */
    private Map<String, Object> convertMessageToFrontendFormat(Message message) {
        Map<String, Object> frontendMessage = new HashMap<>();
        
        // Generate ID if not present
        String messageId = "loaded_" + System.currentTimeMillis() + "_" + Math.random();
        frontendMessage.put("id", messageId);
        
        // Get content using reflection
        String content = getMessageContent(message);
        frontendMessage.put("content", content);
        
        // Determine if it's a user message
        boolean isUser = message.getClass().getSimpleName().contains("User");
        frontendMessage.put("isUser", isUser);
        frontendMessage.put("isFromUser", isUser); // Alternative field name
        
        // Add timestamp (current time as placeholder)
        frontendMessage.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        frontendMessage.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        return frontendMessage;
    }

    /**
     * Extract content from Message using reflection (robust to different Spring AI implementations)
     * Ensures clean text content without raw message object formatting
     */
    private String getMessageContent(Message message) {
        try {
            // First try getContent() method
            Object contentObj = message.getClass().getMethod("getContent").invoke(message);
            if (contentObj != null) {
                String content = contentObj.toString();
                return cleanMessageContent(content);
            }
        } catch (Exception e) {
            log.debug("getContent() method not available, trying alternatives");
        }
        
        // Try alternative methods
        for (String methodName : Arrays.asList("getText", "getTextContent")) {
            try {
                Object contentObj = message.getClass().getMethod(methodName).invoke(message);
                if (contentObj != null) {
                    String content = contentObj.toString();
                    return cleanMessageContent(content);
                }
            } catch (Exception ignored) {}
        }
        
        // Last resort: clean the toString() output
        String rawContent = message.toString();
        return cleanMessageContent(rawContent);
    }
    
    /**
     * Clean message content to remove raw Spring AI message object formatting
     */
    private String cleanMessageContent(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return "";
        }
        
        // Remove raw message object patterns like "UserMessage{content='text', ...}"
        if (rawContent.contains("Message{") && rawContent.contains("content=")) {
            // Extract content from patterns like: content='actual message'
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("content='([^']*)'");
            java.util.regex.Matcher matcher = pattern.matcher(rawContent);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            // Try alternative pattern: textContent=actual text
            pattern = java.util.regex.Pattern.compile("textContent=([^,}]*)");
            matcher = pattern.matcher(rawContent);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        
        // Remove any remaining Spring AI metadata patterns
        String cleaned = rawContent
                .replaceAll("^[A-Za-z]*Message\\{.*?content='", "")  // Remove message wrapper start
                .replaceAll("'.*?\\}$", "")                          // Remove message wrapper end
                .replaceAll("^[A-Za-z]*Message\\{.*?textContent=", "") // Alternative pattern
                .replaceAll(",.*?\\}$", "")                          // Remove trailing metadata
                .trim();
        
        return cleaned.isEmpty() ? rawContent.trim() : cleaned;
    }
} 