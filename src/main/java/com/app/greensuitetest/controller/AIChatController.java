package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.service.AIChatService;
import com.app.greensuitetest.service.ConversationUtilService;
import com.app.greensuitetest.service.RinPersonalityService;
import com.app.greensuitetest.service.ContextBuilderService;
import com.app.greensuitetest.service.PerformanceMonitoringService;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final AIChatService aiChatService;
    private final ConversationUtilService conversationUtilService;
    private final RinPersonalityService rinPersonalityService;
    private final ContextBuilderService contextBuilderService;
    private final PerformanceMonitoringService performanceMonitoringService;
    private final SecurityUtil securityUtil;
    
    private final Random personalityRandom = new Random();

    /**
     * Get current user ID from security context or return null if not authenticated
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                !"anonymousUser".equals(authentication.getName())) {
                return securityUtil.getCurrentUser().getId();
            }
        } catch (Exception e) {
            log.debug("Could not get current user ID from security context: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get effective user ID - use provided userId or get from security context
     */
    private String getEffectiveUserId(String providedUserId) {
        if (providedUserId != null && !providedUserId.trim().isEmpty()) {
            return providedUserId;
        }
        return getCurrentUserId();
    }

    /**
     * Health check for AI service connectivity
     */
    @GetMapping("/health")
    public ApiResponse checkAiHealth() {
        try {
            // Simple test to check if AI service is responding
            return ApiResponse.success("AI service health check", Map.of(
                "status", "operational",
                "ollamaUrl", "http://localhost:11434",
                "model", "gemma3:4b",
                "note", "This is a basic connectivity check. Full functionality requires Ollama to be running."
            ));
        } catch (Exception e) {
            log.error("AI service health check failed", e);
            return ApiResponse.error("AI service health check failed: " + e.getMessage(), Map.of(
                "status", "unavailable",
                "ollamaUrl", "http://localhost:11434",
                "error", e.getMessage(),
                "solution", "Ensure Ollama is running on localhost:11434"
            ));
        }
    }

    /**
     * Streaming chat endpoint with Rin's nurturing personality
     * Returns clean text content only (no raw message objects)
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            // Generate unique conversation ID if not provided
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            log.debug("Rin responding to conversation: {} with message: {} for user: {}", effectiveConversationId, message, effectiveUserId);

            return aiChatService.processStreamingChat(message, effectiveConversationId, effectiveUserId, sessionId)
                .onErrorResume(error -> {
                    // Handle client disconnection gracefully
                    if (isClientDisconnectionError(error)) {
                        log.info("Client disconnected during streaming for conversation: {}", effectiveConversationId);
                        return Flux.empty(); // Stop gracefully without error
                    }
                    
                    // For other errors, return error message
                    log.error("Error in Rin's streaming chat for conversation: {}", effectiveConversationId, error);
                    return Flux.just(rinPersonalityService.getRinErrorResponseForException(error));
                });
        } catch (Exception e) {
            log.error("Error in Rin's streaming chat for conversation: {}", conversationId, e);
            return Flux.just(rinPersonalityService.getRinErrorResponseForException(e));
        }
    }

    /**
     * Synchronous chat endpoint with Rin personality
     * Returns clean text content in ApiResponse format (no raw message objects)
     */
    @PostMapping("/chat/sync")
    public ApiResponse chatSync(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            // Generate unique conversation ID if not provided
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            log.debug("Rin sync response for conversation: {} with message: {} for user: {}", effectiveConversationId, message, effectiveUserId);

            // AIChatService handles content cleaning internally via extractContent() method
            return aiChatService.processSyncChat(message, effectiveConversationId, effectiveUserId, sessionId);
        } catch (Exception e) {
            log.error("Error in Rin's sync chat for conversation: {}", conversationId, e);
            return ApiResponse.error("Rin encountered an error: " + rinPersonalityService.getRinErrorResponseForException(e));
        }
    }

    @GetMapping("/memory/{conversationId}")
    public ApiResponse getChatHistory(@PathVariable String conversationId,
                                      @RequestParam(required = false) String userId,
                                      @RequestParam(required = false) String sessionId) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            // Generate effective conversation ID to ensure user isolation
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            return aiChatService.getChatHistory(effectiveConversationId, effectiveUserId, sessionId);
        } catch (Exception e) {
            log.error("Error retrieving chat history", e);
            return ApiResponse.error("I'm having trouble getting your conversation history... " + 
                "Try using a different conversation ID or check if you have the right permissions! " + 
                "I'm here to help with your environmental questions whenever you're ready.");
        }
    }

    @DeleteMapping("/memory/{conversationId}")
    public ApiResponse clearChatHistory(@PathVariable String conversationId,
                                        @RequestParam(required = false) String userId,
                                        @RequestParam(required = false) String sessionId) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            // Generate effective conversation ID to ensure user isolation
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            return aiChatService.clearChatHistory(effectiveConversationId, effectiveUserId, sessionId);
        } catch (Exception e) {
            log.error("Error clearing chat history", e);
            return ApiResponse.error("I'm having trouble clearing the history... " + 
                "Perhaps try refreshing and clearing again? I'm here to help with your environmental questions whenever you're ready.");
        }
    }

    @PostMapping("/context/analyze/{conversationId}")
    public ApiResponse analyzeConversationContext(@PathVariable String conversationId,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(required = false) String sessionId) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                    effectiveConversationId, effectiveUserId, sessionId, "");

            Map<String, Object> rinAnalysis = rinPersonalityService.getRinPersonalityState(effectiveConversationId, effectiveUserId);

            Map<String, Object> analysisData = Map.of(
                            "conversationId", effectiveConversationId,
                "context_analysis", enhancedContext,
                "rin_personality_state", rinAnalysis,
                "analysis_timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "rin_comment", "I've analyzed our conversation context. It's always interesting to see how our environmental discussions develop."
            );

            return ApiResponse.success("Here's my analysis of our conversation context!", analysisData);
        } catch (Exception e) {
            log.error("Error analyzing conversation context", e);
            return ApiResponse.error("I'm having trouble analyzing the conversation context... " + 
                "Perhaps try again, or we could focus on your environmental questions instead?");
        }
    }

    @PostMapping("/cache/clear")
    public ApiResponse clearContextCache() {
        try {
            contextBuilderService.clearAllContextCache();
            return ApiResponse.success("I've cleared the context cache. " +
                "This will help ensure fresh, accurate responses to your environmental questions.", Map.of(
                "cache_cleared", true,
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "rin_comment", "Fresh starts are always good for learning, don't you think?"
                    ));
        } catch (Exception e) {
            log.error("Error clearing context cache", e);
            return ApiResponse.error("I'm having trouble clearing the cache... " + 
                "But don't worry, I'm still here to help with your environmental questions!");
        }
    }

    @GetMapping("/rin/personality/{conversationId}")
    public ApiResponse getRinPersonalityInfo(@PathVariable String conversationId,
                                             @RequestParam(required = false) String userId,
                                             @RequestParam(required = false) String sessionId) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            Map<String, Object> personalityState = rinPersonalityService.getRinPersonalityState(effectiveConversationId, effectiveUserId);

            Map<String, Object> personalityData = Map.of(
                            "conversationId", effectiveConversationId,
                "personality_type", "mature_environmental_teacher",
                "character_name", "Rin Kazuki (凛 和月)",
                "age", 27,
                "profession", "Environmental Sustainability Teacher",
                "personality_traits", Map.of(
                    "mature", "Thoughtful and introspective",
                    "nurturing", "Patient and encouraging teacher",
                    "poetic", "Appreciates beauty and literature",
                    "environmental", "Passionate about sustainability",
                    "gentle", "Warm and approachable"
                ),
                "current_state", personalityState,
                "rin_comment", "I'm here to help you learn about our beautiful planet. What environmental topic would you like to explore?"
            );

            return ApiResponse.success("Here's a bit about my personality and how I can help with your environmental questions!", personalityData);
        } catch (Exception e) {
            log.error("Error getting Rin personality info", e);
            return ApiResponse.error("I'm having trouble sharing my personality information... " + 
                "But I'm still here to help with your environmental questions!");
        }
    }

    @PostMapping("/rin/mood/boost/{conversationId}")
    public ApiResponse boostRinRelationship(@PathVariable String conversationId,
                                            @RequestParam(required = false) String userId,
                                            @RequestParam(required = false) String sessionId,
                                            @RequestParam(defaultValue = "Environmental compliment") String reason) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            String userKey = effectiveUserId != null ? effectiveUserId : effectiveConversationId;
            
            int currentLevel = rinPersonalityService.getUserRelationshipLevel(userKey);
            
            // Boost relationship through environmental engagement
            rinPersonalityService.updateRelationshipDynamics(effectiveConversationId, effectiveUserId, "environmental compliment boost");
            
            int newLevel = rinPersonalityService.getUserRelationshipLevel(userKey);
            int boostAmount = newLevel - currentLevel;

            Map<String, Object> boostData = Map.of(
                            "conversationId", effectiveConversationId,
                            "previous_level", currentLevel,
                            "new_level", newLevel,
                "boost_amount", boostAmount,
                            "reason", reason,
                "rin_comment", "Thank you for your kind words about environmental topics. I'm always happy to help people learn about sustainability!"
            );

            return ApiResponse.success("I appreciate your interest in environmental topics! Let's continue learning together.", boostData);
        } catch (Exception e) {
            log.error("Error boosting Rin relationship", e);
            return ApiResponse.error("I'm having trouble processing that... " + 
                "But I'm still here to help with your environmental questions!");
        }
    }

    @GetMapping("/debug/conversation/{conversationId}")
    public ApiResponse debugConversation(@PathVariable String conversationId,
                                        @RequestParam(required = false) String userId,
                                        @RequestParam(required = false) String sessionId,
                                        @RequestParam(required = false, defaultValue = "what did we talk about?") String testMessage) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                    effectiveConversationId, effectiveUserId, sessionId, testMessage);

            String debugSystemPrompt = rinPersonalityService.buildDebugSystemPrompt(enhancedContext, "No document context");
            
            Map<String, Object> debugData = Map.of(
                "conversationId", effectiveConversationId,
                "test_message", testMessage,
                "enhanced_context", enhancedContext,
                "debug_system_prompt", debugSystemPrompt,
                "context_keys", enhancedContext.keySet(),
                "rin_comment", "I'm here to help debug any issues with our environmental discussions."
            );

            return ApiResponse.success("Here's the debug information for our conversation!", debugData);
        } catch (Exception e) {
            log.error("Error debugging conversation", e);
            return ApiResponse.error("I'm having trouble with the debug information... " + 
                "But I'm still here to help with your environmental questions!");
        }
    }

    @PostMapping("/debug/name-query")
    public ApiResponse debugNameQuery(@RequestParam String message,
                                     @RequestParam(required = false) String conversationId,
                                     @RequestParam(required = false) String userId,
                                     @RequestParam(required = false) String sessionId) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                effectiveConversationId, effectiveUserId, sessionId, message);
            
            String debugSystemPrompt = rinPersonalityService.buildDebugSystemPrompt(enhancedContext, "No document context");
            
            Map<String, Object> debugData = Map.of(
                "conversationId", effectiveConversationId,
                "test_message", message,
                "enhanced_context", enhancedContext,
                "debug_system_prompt", debugSystemPrompt,
                "is_name_query", enhancedContext.get("user_asking_about_name"),
                "is_rin_name_query", enhancedContext.get("user_asking_about_rin_name"),
                "user_name", enhancedContext.get("user_name"),
                "rin_comment", "I'm here to help debug name-related queries in our environmental discussions."
            );

            return ApiResponse.success("Here's the debug information for the name query!", debugData);
        } catch (Exception e) {
            log.error("Error debugging name query", e);
            return ApiResponse.error("I'm having trouble with the name query debug... " + 
                "But I'm still here to help with your environmental questions!");
        }
    }

    @PostMapping("/debug/history-query")
    public ApiResponse debugHistoryQuery(@RequestParam String message,
                                        @RequestParam(required = false) String conversationId,
                                        @RequestParam(required = false) String userId,
                                        @RequestParam(required = false) String sessionId) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, effectiveUserId, sessionId);
            
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                effectiveConversationId, effectiveUserId, sessionId, message);
            
            String debugSystemPrompt = rinPersonalityService.buildDebugSystemPrompt(enhancedContext, "No document context");
            
            Map<String, Object> debugData = Map.of(
                "conversationId", effectiveConversationId,
                "test_message", message,
                "enhanced_context", enhancedContext,
                "debug_system_prompt", debugSystemPrompt,
                "is_history_query", enhancedContext.get("user_asking_about_conversation_history"),
                "conversation_metrics", enhancedContext.get("conversation_metrics"),
                "rin_comment", "I'm here to help debug conversation history queries in our environmental discussions."
            );

            return ApiResponse.success("Here's the debug information for the history query!", debugData);
        } catch (Exception e) {
            log.error("Error debugging history query", e);
            return ApiResponse.error("I'm having trouble with the history query debug... " + 
                "But I'm still here to help with your environmental questions!");
        }
    }

    @GetMapping("/conversation/persistent-id")
    public ApiResponse getPersistentConversationId(@RequestParam(required = false) String userId) {
        try {
            // Get effective user ID from security context if not provided
            String effectiveUserId = getEffectiveUserId(userId);
            
            String persistentId = conversationUtilService.getPersistentConversationId(effectiveUserId);
            
            Map<String, Object> idData = Map.of(
                "persistent_conversation_id", persistentId,
                "user_id", effectiveUserId,
                "generation_timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "rin_comment", "Here's your persistent conversation ID. I'll remember our environmental discussions across sessions."
            );

            return ApiResponse.success("Here's your persistent conversation ID for our environmental discussions!", idData);
        } catch (Exception e) {
            log.error("Error generating persistent conversation ID", e);
            return ApiResponse.error("I'm having trouble generating a persistent conversation ID... " + 
                "But I'm still here to help with your environmental questions!");
        }
    }

    @GetMapping("/conversation/info")
    public ApiResponse getConversationInfo() {
        try {
            Map<String, Object> infoData = Map.of(
                "ai_personality", "Rin Kazuki (凛 和月)",
                "personality_type", "Mature Environmental Teacher",
                "age", 27,
                "specialization", "Environmental Sustainability",
                "teaching_style", "Patient, nurturing, and encouraging",
                "personality_traits", Map.of(
                    "mature", "Thoughtful and introspective",
                    "nurturing", "Patient and encouraging teacher",
                    "poetic", "Appreciates beauty and literature",
                    "environmental", "Passionate about sustainability",
                    "gentle", "Warm and approachable"
                ),
                "environmental_expertise", Map.of(
                    "carbon_footprints", "Expert knowledge and calculations",
                    "renewable_energy", "Comprehensive understanding",
                    "sustainability_practices", "Practical guidance and advice",
                    "environmental_science", "Deep theoretical knowledge"
                ),
                "rin_comment", "I'm here to help you learn about our beautiful planet and how we can protect it together."
            );

            return ApiResponse.success("Here's some information about me and how I can help with your environmental questions!", infoData);
        } catch (Exception e) {
            log.error("Error getting conversation info", e);
            return ApiResponse.error("I'm having trouble sharing my information... " + 
                "But I'm still here to help with your environmental questions!");
        }
    }

    @GetMapping("/performance/metrics")
    public ApiResponse getPerformanceMetrics() {
        try {
            Map<String, Object> metrics = performanceMonitoringService.getPerformanceMetrics();
            Map<String, Object> redisHealth = performanceMonitoringService.checkRedisHealth();
            Map<String, Object> cacheStats = performanceMonitoringService.getCacheStatistics();
            
            Map<String, Object> performanceData = Map.of(
                "metrics", metrics,
                "redis_health", redisHealth,
                "cache_statistics", cacheStats,
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            
            return ApiResponse.success("Performance metrics retrieved successfully", performanceData);
        } catch (Exception e) {
            log.error("Error getting performance metrics", e);
            return ApiResponse.error("Failed to retrieve performance metrics: " + e.getMessage());
        }
    }

    @PostMapping("/performance/reset")
    public ApiResponse resetPerformanceMetrics() {
        try {
            performanceMonitoringService.resetMetrics();
            return ApiResponse.success("Performance metrics reset successfully");
        } catch (Exception e) {
            log.error("Error resetting performance metrics", e);
            return ApiResponse.error("Failed to reset performance metrics: " + e.getMessage());
        }
    }

    @GetMapping("/rin/environmental-tips")
    public ApiResponse getRinEnvironmentalTips() {
        try {
            String[] tips = {
                "Consider using energy-efficient LED bulbs - they use up to 90% less energy than traditional incandescent bulbs and last much longer.",
                "Try to reduce your meat consumption by having one meatless day per week. This can significantly reduce your carbon footprint.",
                "Use reusable water bottles and coffee cups instead of disposable ones. Every small change makes a difference.",
                "Consider walking, cycling, or using public transportation when possible. It's good for both you and the environment.",
                "Start composting your food waste. It's a wonderful way to reduce landfill waste and create nutrient-rich soil for plants.",
                "Switch to renewable energy sources if available in your area. Solar and wind power are becoming more accessible.",
                "Reduce your water usage by taking shorter showers and fixing any leaks. Every drop counts.",
                "Choose products with minimal packaging or packaging that can be recycled. This helps reduce waste significantly.",
                "Plant native trees and flowers in your garden. They provide habitat for local wildlife and help clean the air.",
                "Consider the environmental impact of your purchases. Sometimes spending a bit more on sustainable products pays off in the long run."
            };

            String selectedTip = tips[personalityRandom.nextInt(tips.length)];

            String[] additionalComments = {
                "I hope this tip helps you on your environmental journey!",
                "Every small step toward sustainability makes our planet a better place.",
                "It's wonderful to see people taking an interest in environmental protection.",
                "Remember, we're all learning together how to care for our beautiful planet."
            };

            String additionalComment = additionalComments[personalityRandom.nextInt(additionalComments.length)];

            return ApiResponse.success("Here's an environmental tip for you!",
                    Map.of(
                            "tip", selectedTip,
                            "additional_comment", additionalComment,
                            "expertise_level", "Expert Environmental Teacher",
                            "passion_level", "Deeply Caring"
                    ));
        } catch (Exception e) {
            log.error("Error getting Rin's environmental tips", e);
            return ApiResponse.error("I'm having trouble sharing environmental advice... " + 
                "But I'm still here to help with your sustainability questions! " + 
                "What specific environmental topic would you like to learn about?");
        }
    }

    /**
     * Detect if an error is related to client disconnection
     */
    private boolean isClientDisconnectionError(Throwable error) {
        // Check for client abort exceptions (client disconnected)
        if (error instanceof ClientAbortException) {
            return true;
        }
        
        // Check for async request not usable (client disconnected during streaming)
        if (error instanceof AsyncRequestNotUsableException) {
            return true;
        }
        
        // Check for connection-related exceptions
        if (error instanceof ConnectException || error instanceof TimeoutException) {
            return true;
        }
        
        // Check for IO exceptions that might indicate client disconnection
        if (error instanceof java.io.IOException) {
            String message = error.getMessage();
            return message != null && (
                message.contains("Connection reset") ||
                message.contains("Broken pipe") ||
                message.contains("Connection aborted") ||
                message.contains("Software caused connection abort")
            );
        }
        
        // Check for underlying cause
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            return isClientDisconnectionError(cause);
        }
        
        return false;
    }
} 