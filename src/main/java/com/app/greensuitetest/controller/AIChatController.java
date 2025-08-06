package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.service.AIChatService;
import com.app.greensuitetest.service.ConversationUtilService;
import com.app.greensuitetest.service.RinPersonalityService;
import com.app.greensuitetest.service.ContextBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final AIChatService aiChatService;
    private final ConversationUtilService conversationUtilService;
    private final RinPersonalityService rinPersonalityService;
    private final ContextBuilderService contextBuilderService;
    
    private final Random personalityRandom = new Random();

    /**
     * Streaming chat endpoint with Rin Kazuki's tsundere personality
     * Returns clean text content only (no raw message objects)
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        try {
            // Generate unique conversation ID if not provided
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            log.debug("Rin Kazuki responding to conversation: {} with message: {}", effectiveConversationId, message);

            return aiChatService.processStreamingChat(message, effectiveConversationId, userId, sessionId)
                    .map(this::ensureCleanContent); // Ensure absolutely clean content
        } catch (Exception e) {
            log.error("Error in Rin's streaming chat for conversation: {}", conversationId, e);
            return Flux.just(rinPersonalityService.getRinErrorResponseForException(e));
        }
    }

    /**
     * Synchronous chat endpoint with Rin Kazuki personality
     * Returns clean text content in ApiResponse format (no raw message objects)
     */
    @PostMapping("/chat/sync")
    public ApiResponse chatSync(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        try {
            // Generate unique conversation ID if not provided
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            log.debug("Rin Kazuki sync response for conversation: {} with message: {}", effectiveConversationId, message);

            // AIChatService handles content cleaning internally via extractContent() method
            return aiChatService.processSyncChat(message, effectiveConversationId, userId, sessionId);
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
            // Generate effective conversation ID to ensure user isolation
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            return aiChatService.getChatHistory(effectiveConversationId, userId, sessionId);
        } catch (Exception e) {
            log.error("Error retrieving chat history", e);
            return ApiResponse.error("Tch! I had trouble getting your conversation history... " + 
                "Try using a different conversation ID or check if you have the right permissions! " + 
                "Not that I was keeping track of everything you said or anything!");
        }
    }

    @DeleteMapping("/memory/{conversationId}")
    public ApiResponse clearChatHistory(@PathVariable String conversationId,
                                        @RequestParam(required = false) String userId,
                                        @RequestParam(required = false) String sessionId) {
        try {
            // Generate effective conversation ID to ensure user isolation
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            return aiChatService.clearChatHistory(effectiveConversationId, userId, sessionId);
        } catch (Exception e) {
            log.error("Error clearing chat history", e);
            return ApiResponse.error("Hmph! I couldn't clear the history properly... " + 
                "Maybe check if the conversation ID exists or try refreshing and clearing again! " + 
                "It's not like I wanted to keep those memories anyway!");
        }
    }

    @PostMapping("/context/analyze/{conversationId}")
    public ApiResponse analyzeConversationContext(@PathVariable String conversationId,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(required = false) String sessionId) {
        try {
            // Generate effective conversation ID to ensure user isolation
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            Map<String, Object> context = contextBuilderService.buildEnhancedContext(
                    effectiveConversationId, userId, sessionId, "");

            // Add Rin's personality analysis
            Map<String, Object> rinAnalysis = rinPersonalityService.getRinPersonalityState(effectiveConversationId, userId);

            return ApiResponse.success("I analyzed your conversation patterns... not that I was paying close attention!",
                    Map.of(
                            "conversationId", effectiveConversationId,
                            "context", context,
                            "rin_personality_analysis", rinAnalysis,
                            "rin_comment", "Your environmental awareness level is... acceptable, I suppose.",
                            "analysisTimestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ));
        } catch (Exception e) {
            log.error("Error analyzing conversation context", e);
            return ApiResponse.error("Tch! I had trouble analyzing your conversation patterns... " + 
                "Try checking if the conversation ID is valid or start a new conversation! " + 
                "Not that I was paying close attention to everything you said!");
        }
    }

    @PostMapping("/cache/clear")
    public ApiResponse clearContextCache() {
        try {
            int clearedEntries = contextBuilderService.getCacheSize();
            contextBuilderService.clearAllContextCache();

            return ApiResponse.success("Hmph! I cleared all the cache data... fresh start, I guess.",
                    Map.of(
                            "clearedEntries", clearedEntries,
                            "rin_comment", "Don't think this means I'm starting over with everyone... I just needed more memory space!"
                    ));
        } catch (Exception e) {
            log.error("Error clearing context cache", e);
            return ApiResponse.error("Hmph! I had trouble clearing the cache... " + 
                "This usually means there's a system issue. Try restarting the application or contact support! " + 
                "Not that I care if the cache gets cleared or anything... hmph!");
        }
    }

    // New Rin-specific endpoints
    @GetMapping("/rin/personality/{conversationId}")
    public ApiResponse getRinPersonalityInfo(@PathVariable String conversationId,
                                             @RequestParam(required = false) String userId,
                                             @RequestParam(required = false) String sessionId) {
        try {
            // Generate effective conversation ID to ensure user isolation
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            String userKey = userId != null ? userId : effectiveConversationId;
            Map<String, Object> personalityState = rinPersonalityService.getRinPersonalityState(effectiveConversationId, userId);

            String[] rinComments = {
                    "Why are you checking on my personality state? It's not like you care about my feelings!",
                    "Hmph! My personality is perfectly fine, thank you very much!",
                    "I-it's not like I want you to understand me better or anything...",
                    "Don't analyze me too much! Just focus on saving the environment!"
            };

            String selectedComment = rinComments[personalityRandom.nextInt(rinComments.length)];

            return ApiResponse.success("Here's my current state... not that it matters to you!",
                    Map.of(
                            "conversationId", effectiveConversationId,
                            "personality_state", personalityState,
                            "rin_comment", selectedComment,
                            "environmental_passion_level", "Maximum! (Not that I'm admitting it!)"
                    ));
        } catch (Exception e) {
            log.error("Error getting Rin's personality info", e);
            return ApiResponse.error("Tch! I had trouble showing my personality state... " + 
                "Maybe check if the conversation ID is valid or try asking about environmental topics instead! " + 
                "It's not like I wanted to share my feelings anyway!");
        }
    }

    @PostMapping("/rin/mood/boost/{conversationId}")
    public ApiResponse boostRinRelationship(@PathVariable String conversationId,
                                            @RequestParam(required = false) String userId,
                                            @RequestParam(required = false) String sessionId,
                                            @RequestParam(defaultValue = "Environmental compliment") String reason) {
        try {
            // Generate effective conversation ID to ensure user isolation
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            String userKey = userId != null ? userId : effectiveConversationId;
            int currentLevel = rinPersonalityService.getUserRelationshipLevel(userKey);
            
            // Simulate boosting relationship (in a real implementation, this would be more sophisticated)
            rinPersonalityService.updateRelationshipDynamics(effectiveConversationId, userId, "environmental compliment boost");
            
            int newLevel = rinPersonalityService.getUserRelationshipLevel(userKey);

            String[] rinResponses = {
                    "H-hey! Don't think a little compliment will make me happy or anything! ...But I guess you're not completely hopeless.",
                    "Hmph! I suppose you're finally starting to understand environmental issues properly... took you long enough!",
                    "It's not like I'm pleased that you noticed my expertise! I just... appreciate when people take the environment seriously.",
                    "You don't need to butter me up... but I guess your environmental awareness is improving..."
            };

            String response = rinResponses[personalityRandom.nextInt(rinResponses.length)];

            return ApiResponse.success("Relationship level updated!",
                    Map.of(
                            "conversationId", effectiveConversationId,
                            "previous_level", currentLevel,
                            "new_level", newLevel,
                            "reason", reason,
                            "rin_response", response,
                            "hidden_thought", "(Actually... that made me kind of happy...)"
                    ));
        } catch (Exception e) {
            log.error("Error boosting relationship with Rin", e);
            return ApiResponse.error("Tch! I had trouble updating our relationship level... " + 
                "Maybe check if you're using a valid conversation ID or try complimenting my environmental expertise instead! " + 
                "Not that I was excited about getting closer to you or anything!");
        }
    }

    @GetMapping("/debug/conversation/{conversationId}")
    public ApiResponse debugConversation(@PathVariable String conversationId,
                                        @RequestParam(required = false) String userId,
                                        @RequestParam(required = false) String sessionId,
                                        @RequestParam(required = false, defaultValue = "what did we talk about?") String testMessage) {
        try {
            // Generate effective conversation ID to ensure user isolation
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            Map<String, Object> context = contextBuilderService.buildEnhancedContextWithPersonality(
                    effectiveConversationId, userId, sessionId, testMessage);

            return ApiResponse.success("Debug information for conversation",
                    Map.of(
                            "originalConversationId", conversationId,
                            "effectiveConversationId", effectiveConversationId,
                            "conversationIdLength", effectiveConversationId.length(),
                            "userId", userId != null ? userId : "null",
                            "sessionId", sessionId != null ? sessionId : "null",
                            "testMessage", testMessage,
                            "context", context,
                            "isHistoryQuery", context.getOrDefault("user_asking_about_conversation_history", false),
                            "isNewConversation", context.getOrDefault("is_new_conversation", false),
                            "rin_comment", "Here's what I can see about this conversation... for debugging purposes only!"
                    ));
        } catch (Exception e) {
            log.error("Error debugging conversation", e);
            return ApiResponse.error("Debug failed: " + e.getMessage());
        }
    }

    @PostMapping("/debug/name-query")
    public ApiResponse debugNameQuery(@RequestParam String message,
                                     @RequestParam(required = false) String conversationId,
                                     @RequestParam(required = false) String userId,
                                     @RequestParam(required = false) String sessionId) {
        try {
            // Generate effective conversation ID
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            log.info("🔍 DEBUG: Testing name query with message: {}", message);
            
            // Build context with personality
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                effectiveConversationId, userId, sessionId, message);
            
            boolean isUserNameQuery = Boolean.TRUE.equals(enhancedContext.get("user_asking_about_name"));
            boolean isRinNameQuery = Boolean.TRUE.equals(enhancedContext.get("user_asking_about_rin_name"));
            String userName = (String) enhancedContext.get("user_name");
            
            log.info("🔍 DEBUG: Is user name query: {}", isUserNameQuery);
            log.info("🔍 DEBUG: Is Rin name query: {}", isRinNameQuery);
            log.info("🔍 DEBUG: User name found: {}", userName);
            
            // Build a simplified system prompt for debugging
            String debugSystemPrompt = rinPersonalityService.buildDebugSystemPrompt(enhancedContext, "No document context");
            
            return ApiResponse.success("Debug name query results",
                    Map.of(
                            "message", message,
                            "effectiveConversationId", effectiveConversationId,
                            "isUserNameQuery", isUserNameQuery,
                            "isRinNameQuery", isRinNameQuery,
                            "userNameFound", userName != null ? userName : "null",
                            "context", enhancedContext,
                            "systemPromptPreview", debugSystemPrompt.length() > 500 ? 
                                debugSystemPrompt.substring(0, 500) + "..." : debugSystemPrompt,
                            "rin_comment", "This shows how I distinguish between 'what is my name?' vs 'what is your name?'"
                    ));
        } catch (Exception e) {
            log.error("Error debugging name query", e);
            return ApiResponse.error("Debug name query failed: " + e.getMessage());
        }
    }

    @PostMapping("/debug/history-query")
    public ApiResponse debugHistoryQuery(@RequestParam String message,
                                        @RequestParam(required = false) String conversationId,
                                        @RequestParam(required = false) String userId,
                                        @RequestParam(required = false) String sessionId) {
        try {
            // Generate effective conversation ID
            String effectiveConversationId = conversationUtilService.generateUniqueConversationId(conversationId, userId, sessionId);
            
            log.info("🔍 DEBUG: Testing history query with message: {}", message);
            log.info("🔍 DEBUG: Effective conversation ID: {} (length: {})", effectiveConversationId, effectiveConversationId.length());
            
            // Build context with personality
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                effectiveConversationId, userId, sessionId, message);
            
            boolean isHistoryQuery = Boolean.TRUE.equals(enhancedContext.get("user_asking_about_conversation_history"));
            boolean isNewConversation = Boolean.TRUE.equals(enhancedContext.get("is_new_conversation"));
            
            log.info("🔍 DEBUG: Is history query: {}", isHistoryQuery);
            log.info("🔍 DEBUG: Is new conversation: {}", isNewConversation);
            
            // Build a simplified system prompt for debugging
            String debugSystemPrompt = rinPersonalityService.buildDebugSystemPrompt(enhancedContext, "No document context");
            
            return ApiResponse.success("Debug history query results",
                    Map.of(
                            "message", message,
                            "effectiveConversationId", effectiveConversationId,
                            "conversationIdLength", effectiveConversationId.length(),
                            "isHistoryQuery", isHistoryQuery,
                            "isNewConversation", isNewConversation,
                            "context", enhancedContext,
                            "systemPromptPreview", debugSystemPrompt.length() > 500 ? 
                                debugSystemPrompt.substring(0, 500) + "..." : debugSystemPrompt,
                            "rin_comment", "This is a debug analysis of how I would handle this history query."
                    ));
        } catch (Exception e) {
            log.error("Error debugging history query", e);
            return ApiResponse.error("Debug history query failed: " + e.getMessage());
        }
    }

    @GetMapping("/conversation/persistent-id")
    public ApiResponse getPersistentConversationId(@RequestParam(required = false) String userId) {
        try {
            String persistentId = conversationUtilService.getPersistentConversationId(userId);
            
            return ApiResponse.success("Generated persistent conversation ID",
                    Map.of(
                            "conversationId", persistentId,
                            "userId", userId != null ? userId : "anonymous",
                            "persistent", true,
                            "rin_comment", "This ID will stay the same even if you log out and back in! Not that I care about remembering you or anything..."
                    ));
        } catch (Exception e) {
            log.error("Error generating persistent conversation ID", e);
            return ApiResponse.error("Couldn't generate persistent conversation ID: " + e.getMessage());
        }
    }

    @GetMapping("/conversation/info")
    public ApiResponse getConversationInfo() {
        return ApiResponse.success("Conversation memory system information",
                Map.of(
                        "how_memory_works", "Your conversations are now persistent across login/logout cycles",
                        "persistent_ids", "Each user gets a permanent conversation ID that doesn't change",
                        "knowledge_integration", "Environmental documents are integrated as Rin's natural expertise",
                        "no_document_references", "Rin will never mention sources or documents explicitly",
                        "endpoints", Map.of(
                                "get_persistent_id", "GET /api/ai/conversation/persistent/{userId}",
                                "load_history", "GET /api/ai/conversation/{conversationId}/history?userId=USER_ID",
                                "chat_streaming", "POST /api/ai/chat?message=MSG&conversationId=ID&userId=USER&sessionId=SESSION",
                                "chat_sync", "POST /api/ai/chat/sync?message=MSG&conversationId=ID&userId=USER&sessionId=SESSION",
                                "get_memory", "GET /api/ai/memory/{conversationId}?userId=USER&sessionId=SESSION",
                                "clear_memory", "DELETE /api/ai/memory/{conversationId}?userId=USER&sessionId=SESSION",
                                "clear_memory_alt", "DELETE /api/ai/conversation/{conversationId}/history?userId=USER&sessionId=SESSION"
                        ),
                        "frontend_integration", Map.of(
                                "step_1", "Call GET /api/ai/conversation/persistent/{userId} to get conversationId",
                                "step_2", "Call GET /api/ai/conversation/{conversationId}/history to load existing messages",
                                "step_3", "Use POST /api/ai/chat with the conversationId for new messages",
                                "step_4", "Messages automatically save to ChatMemory during chat processing"
                        ),
                        "rin_comment", "Now I'll actually remember our conversations! Not that I was trying to forget you before... hmph!"
                ));
    }

    @GetMapping("/rin/environmental-tips")
    public ApiResponse getRinEnvironmentalTips() {
        try {
            String[] tips = {
                    "Hmph! Fine, I'll give you ONE tip: Start measuring your Scope 1, 2, and 3 emissions properly! It's basic stuff, really.",
                    "Listen up! Switch to renewable energy sources already! Solar and wind power aren't just trendy - they actually work!",
                    "Tch! You want advice? Implement a proper waste management system with circular economy principles. It's not rocket science!",
                    "I suppose I could mention that water conservation is crucial... not that I care if you waste water or anything!",
                    "Don't make me repeat myself! Carbon offsetting is only effective if you ACTUALLY reduce emissions first. Quality over quantity!",
                    "Here's a freebie: Use life cycle assessments for your products. If you don't know the environmental impact, how can you improve it? Obviously!",
                    "Fine! I'll tell you about supply chain optimization - work with suppliers who share your environmental values. It's common sense!",
                    "Honestly... just start with energy audits. You can't improve what you don't measure. Even someone like you should understand that!"
            };

            String selectedTip = tips[personalityRandom.nextInt(tips.length)];

            String[] additionalComments = {
                    "There! I helped you... but don't expect me to do this all the time!",
                    "Not that I enjoy teaching people about the environment or anything...",
                    "I-it's not like I want to save the planet with you... I just hate seeing waste!",
                    "Don't thank me! I'm only doing this because someone has to educate you properly!"
            };

            String additionalComment = additionalComments[personalityRandom.nextInt(additionalComments.length)];

            return ApiResponse.success("Here's your environmental tip!",
                    Map.of(
                            "tip", selectedTip,
                            "additional_comment", additionalComment,
                            "expertise_level", "Expert (Obviously!)",
                            "passion_level", "Secretly Maximum"
                    ));
        } catch (Exception e) {
            log.error("Error getting Rin's environmental tips", e);
            return ApiResponse.error("Tch! I had trouble giving you environmental advice... " + 
                "This is unusual since I know tons about sustainability! Try asking me a specific environmental question instead! " + 
                "Not that I'm eager to teach you or anything... hmph!");
        }
    }

    /**
     * Ensure content is clean and doesn't contain raw Spring AI message objects
     * This is a final validation layer for streaming responses
     */
    private String ensureCleanContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        // If it's already clean (no message object markers), return as-is
        if (!content.contains("Message{") && !content.contains("content=") && !content.contains("textContent=")) {
            return content;
        }
        
        // Clean up any raw message object content that might have slipped through
        // Pattern 1: Extract from content='text'
        Pattern pattern = Pattern.compile("content='([^']*)'");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Pattern 2: Extract from textContent=text
        pattern = Pattern.compile("textContent=([^,}]*)");
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Pattern 3: Remove message object wrapper
        String cleaned = content
                .replaceAll("^[A-Za-z]*Message\\{.*?content='", "")
                .replaceAll("'.*?\\}$", "")
                .replaceAll("^[A-Za-z]*Message\\{.*?textContent=", "")
                .replaceAll(",.*?\\}$", "")
                .trim();
        
        return cleaned.isEmpty() ? content.trim() : cleaned;
    }
} 