package com.app.greensuitetest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextBuilderService {

    private final ConversationContextService conversationContextService;
    private final RinPersonalityService rinPersonalityService;
    
    // Cache for conversation contexts to improve performance
    private final Map<String, Map<String, Object>> conversationCache = new ConcurrentHashMap<>();

    // Environmental sustainability keywords
    private final Set<String> sustainabilityKeywords = Set.of(
        "environment", "sustainability", "green", "eco", "carbon", "emission", "footprint",
        "renewable", "energy", "solar", "wind", "recycling", "waste", "conservation",
        "climate", "global warming", "pollution", "biodiversity", "organic", "sustainable"
    );

    public Map<String, Object> buildEnhancedContextWithPersonality(String conversationId, String userId, String sessionId, String message) {
        Map<String, Object> context = new HashMap<>();
        String userKey = userId != null ? userId : conversationId;

        // Add Rin's personality context
        context.put("rin_relationship_level", rinPersonalityService.getUserRelationshipLevel(userKey));
        context.put("rin_personality_state", rinPersonalityService.getRinPersonalityState(conversationId, userId));

        // Add conversation context
        Map<String, Object> conversationContext = conversationContextService.buildComprehensiveContext(conversationId, userId, sessionId, message);
        context.putAll(conversationContext);

        // Add message analysis
        Map<String, Object> messageAnalysis = analyzeMessage(message);
        context.putAll(messageAnalysis);

        // Add environmental context
        Map<String, Object> environmentalContext = buildEnvironmentalContext(message, conversationContext);
        context.putAll(environmentalContext);

        // Add temporal context
        Map<String, Object> temporalContext = buildTemporalContext();
        context.putAll(temporalContext);

        // Cache the context
        conversationCache.put(conversationId, context);

        return context;
    }

    public Map<String, Object> buildEnhancedContext(String conversationId, String userId, String sessionId, String message) {
        Map<String, Object> context = new HashMap<>();

        try {
            // Get cached context if available and recent
            String cacheKey = conversationId + "_" + userId + "_" + sessionId;
            Map<String, Object> cachedContext = conversationCache.get(cacheKey);

            if (cachedContext != null && isContextFresh(cachedContext)) {
                context.putAll(cachedContext);
                log.debug("Using cached context for conversation: {}", conversationId);
            } else {
                // Build fresh context
                context = conversationContextService.buildComprehensiveContext(conversationId, userId, sessionId, message);

                // Cache the context with shorter TTL to prevent staleness
                context.put("timestamp", System.currentTimeMillis());
                conversationCache.put(cacheKey, new HashMap<>(context));

                log.debug("Built fresh enhanced context for conversation: {}", conversationId);
            }

            // Add real-time context
            context.put("current_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            context.put("message_intent", analyzeMessageIntent(message));
            context.put("message_complexity", analyzeMessageComplexity(message));

        } catch (Exception e) {
            log.warn("Failed to build enhanced context: {}", e.getMessage());
            context.put("context_error", e.getMessage());
        }

        return context;
    }

    public void clearContextCache(String conversationId) {
        conversationCache.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));
    }

    public void clearAllContextCache() {
        conversationCache.clear();
    }

    public int getCacheSize() {
        return conversationCache.size();
    }

    private Map<String, Object> getUserInteractionHistory(String userKey) {
        LocalDateTime lastTime = rinPersonalityService.getLastInteractionTime(userKey);
        int relationshipLevel = rinPersonalityService.getUserRelationshipLevel(userKey);

        return Map.of(
                "last_interaction", lastTime,
                "relationship_level", relationshipLevel,
                "interaction_count", getInteractionCount(userKey)
        );
    }

    private int getInteractionCount(String userKey) {
        // This would typically be stored in a more persistent way
        return rinPersonalityService.getUserRelationshipLevel(userKey) / 2; // Rough estimate
    }

    private boolean isContextFresh(Map<String, Object> context) {
        Long timestamp = (Long) context.get("timestamp");
        if (timestamp == null) return false;
        return (System.currentTimeMillis() - timestamp) < 120000;
    }

    private String analyzeMessageIntent(String message) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("calculate") || lowerMessage.contains("compute") ||
                lowerMessage.contains("emission") || lowerMessage.contains("formula")) {
            return "calculation";
        } else if (lowerMessage.contains("explain") || lowerMessage.contains("what is") ||
                lowerMessage.contains("how does") || lowerMessage.contains("define")) {
            return "explanation";
        } else if (lowerMessage.contains("recommend") || lowerMessage.contains("suggest") ||
                lowerMessage.contains("advice") || lowerMessage.contains("should")) {
            return "recommendation";
        } else if (lowerMessage.contains("compare") || lowerMessage.contains("difference") ||
                lowerMessage.contains("versus") || lowerMessage.contains("vs")) {
            return "comparison";
        } else if (lowerMessage.contains("help") || lowerMessage.contains("guide") ||
                lowerMessage.contains("how to") || lowerMessage.contains("steps")) {
            return "guidance";
        } else {
            return "general";
        }
    }

    private String analyzeMessageComplexity(String message) {
        String[] words = message.split("\\s+");
        int wordCount = words.length;
        int questionMarks = message.length() - message.replace("?", "").length();

        boolean hasMultipleTopics = message.contains(" and ") || message.contains(" or ") || message.contains(",");
        boolean hasComplexStructure = message.contains("however") || message.contains("therefore") ||
                message.contains("furthermore") || message.contains("moreover");

        if (wordCount > 50 || questionMarks > 2 || hasComplexStructure) {
            return "complex";
        } else if (wordCount > 20 || questionMarks > 1 || hasMultipleTopics) {
            return "moderate";
        } else {
            return "simple";
        }
    }

    private Map<String, Object> analyzeMessage(String message) {
        Map<String, Object> messageAnalysis = new HashMap<>();
        messageAnalysis.put("message_intent", analyzeMessageIntent(message));
        messageAnalysis.put("message_complexity", analyzeMessageComplexity(message));
        return messageAnalysis;
    }

    private Map<String, Object> buildEnvironmentalContext(String message, Map<String, Object> conversationContext) {
        Map<String, Object> envContext = new HashMap<>();
        
        // Analyze environmental engagement
        String lowerMessage = message.toLowerCase();
        int environmentalScore = 0;
        
        for (String keyword : sustainabilityKeywords) {
            if (lowerMessage.contains(keyword)) {
                environmentalScore += 2;
            }
        }
        
        // Check for specific environmental topics
        if (lowerMessage.contains("carbon") || lowerMessage.contains("emission")) {
            envContext.put("environmental_topic", "carbon_footprint");
            environmentalScore += 3;
        } else if (lowerMessage.contains("energy") || lowerMessage.contains("renewable")) {
            envContext.put("environmental_topic", "energy");
            environmentalScore += 3;
        } else if (lowerMessage.contains("waste") || lowerMessage.contains("recycling")) {
            envContext.put("environmental_topic", "waste_management");
            environmentalScore += 3;
        } else if (lowerMessage.contains("water") || lowerMessage.contains("conservation")) {
            envContext.put("environmental_topic", "water_conservation");
            environmentalScore += 3;
        }
        
        envContext.put("environmental_engagement_score", environmentalScore);
        
        // Add Rin's environmental expertise level - use conversationId as fallback for userKey
        String userKey = (String) conversationContext.get("user_id");
        if (userKey == null) {
            userKey = (String) conversationContext.get("conversation_id");
        }
        
        if (userKey != null) {
            LocalDateTime lastTime = rinPersonalityService.getLastInteractionTime(userKey);
            int relationshipLevel = rinPersonalityService.getUserRelationshipLevel(userKey);
            
            if (lastTime != null) {
                long hoursSinceLastInteraction = java.time.Duration.between(lastTime, LocalDateTime.now()).toHours();
                envContext.put("hours_since_last_interaction", hoursSinceLastInteraction);
            }
            
            // Estimate user's environmental knowledge level
            envContext.put("estimated_user_expertise", relationshipLevel / 2); // Rough estimate
        }
        
        return envContext;
    }

    private Map<String, Object> buildTemporalContext() {
        Map<String, Object> temporalContext = new HashMap<>();
        temporalContext.put("current_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return temporalContext;
    }
} 