package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.service.ConversationContextService;
import com.app.greensuitetest.service.DocumentContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final StreamingChatModel streamingChatModel;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;
    private final ConversationContextService conversationContextService;
    private final DocumentContextService documentContextService;

    // Cache for conversation contexts to improve performance
    private final Map<String, Map<String, Object>> conversationCache = new ConcurrentHashMap<>();

    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        try {
            log.debug("Starting enhanced streaming chat for conversation: {} with message: {}", conversationId, message);

            // Enhanced context building with user and session awareness
            Map<String, Object> enhancedContext = buildEnhancedContext(conversationId, userId, sessionId, message);

            return processWithEnhancedContextStream(message, conversationId, enhancedContext);
        } catch (Exception e) {
            log.error("Error in streaming chat for conversation: {}", conversationId, e);
            return Flux.just("I apologize, but I encountered an error while processing your request. Please try again.");
        }
    }

    @PostMapping("/chat/sync")
    public ApiResponse chatSync(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        try {
            log.debug("Starting enhanced sync chat for conversation: {} with message: {}", conversationId, message);

            Map<String, Object> enhancedContext = buildEnhancedContext(conversationId, userId, sessionId, message);
            String response = processWithEnhancedContext(message, conversationId, enhancedContext).block();

            // Update conversation context after successful response
            conversationContextService.updateContextAfterInteraction(conversationId, message, response);

            return ApiResponse.success("Chat response", Map.of(
                    "response", response != null ? response : "No response generated",
                    "conversationId", conversationId,
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "contextUsed", enhancedContext.keySet()
            ));
        } catch (Exception e) {
            log.error("Error in sync chat for conversation: {}", conversationId, e);
            return ApiResponse.error("Error processing chat request: " + e.getMessage());
        }
    }

    private Flux<String> processWithEnhancedContextStream(String message, String conversationId, Map<String, Object> enhancedContext) {
        return Mono.fromCallable(() -> {
                    // Multi-stage context retrieval
                    List<Document> documents = performEnhancedVectorSearch(message, enhancedContext);

                    // Build comprehensive context
                    String documentContext = documentContextService.buildIntelligentContext(documents, message);

                    // Build enhanced prompt messages
                    List<Message> promptMessages = buildEnhancedPromptMessages(conversationId, message, documentContext, enhancedContext);

                    return promptMessages;
                })
                .flatMapMany(promptMessages -> {
                    // Dynamic chat options based on context and message complexity
                    ChatOptions options = buildDynamicChatOptions(message, enhancedContext);
                    Prompt prompt = new Prompt(promptMessages, options);

                    // Stream response with enhanced tracking
                    AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());

                    return streamingChatModel.stream(prompt)
                            .map(chatResponse -> {
                                String content = extractContent(chatResponse.getResult().getOutput());
                                responseBuilder.get().append(content);
                                return content;
                            })
                            .doOnComplete(() -> {
                                String finalResponse = responseBuilder.get().toString().trim();
                                saveChatToMemoryWithContext(conversationId, message, finalResponse, enhancedContext);

                                // Update conversation context asynchronously
                                conversationContextService.updateContextAfterInteraction(conversationId, message, finalResponse);
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error in enhanced stream processing", error);
                    return Flux.just("I apologize, but I encountered an error while processing your request. Please try again.");
                });
    }

    private Mono<String> processWithEnhancedContext(String message, String conversationId, Map<String, Object> enhancedContext) {
        return Mono.fromCallable(() -> {
                    List<Document> documents = performEnhancedVectorSearch(message, enhancedContext);
                    String documentContext = documentContextService.buildIntelligentContext(documents, message);
                    List<Message> promptMessages = buildEnhancedPromptMessages(conversationId, message, documentContext, enhancedContext);

                    ChatOptions options = buildDynamicChatOptions(message, enhancedContext);
                    Prompt prompt = new Prompt(promptMessages, options);

                    StringBuilder responseBuilder = new StringBuilder();
                    streamingChatModel.stream(prompt)
                            .doOnNext(chatResponse -> {
                                String content = extractContent(chatResponse.getResult().getOutput());
                                responseBuilder.append(content);
                            })
                            .blockLast();

                    String finalResponse = responseBuilder.toString().trim();
                    if (finalResponse.isEmpty()) {
                        finalResponse = "I apologize, but I couldn't generate a proper response. Please try again.";
                    }

                    saveChatToMemoryWithContext(conversationId, message, finalResponse, enhancedContext);
                    return finalResponse;
                })
                .onErrorResume(error -> {
                    log.error("Error processing enhanced chat", error);
                    return Mono.just("I apologize, but I encountered an error while processing your request. Please try again.");
                });
    }

    private Map<String, Object> buildEnhancedContext(String conversationId, String userId, String sessionId, String message) {
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

                // Cache the context
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

    private boolean isContextFresh(Map<String, Object> context) {
        Long timestamp = (Long) context.get("timestamp");
        if (timestamp == null) return false;

        // Context is fresh for 5 minutes
        return (System.currentTimeMillis() - timestamp) < 300000;
    }

    private String analyzeMessageIntent(String message) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("calculate") || lowerMessage.contains("compute") || lowerMessage.contains("emission")) {
            return "calculation";
        } else if (lowerMessage.contains("explain") || lowerMessage.contains("what is") || lowerMessage.contains("how does")) {
            return "explanation";
        } else if (lowerMessage.contains("recommend") || lowerMessage.contains("suggest") || lowerMessage.contains("advice")) {
            return "recommendation";
        } else if (lowerMessage.contains("compare") || lowerMessage.contains("difference") || lowerMessage.contains("versus")) {
            return "comparison";
        } else if (lowerMessage.contains("help") || lowerMessage.contains("guide") || lowerMessage.contains("how to")) {
            return "guidance";
        } else {
            return "general";
        }
    }

    private String analyzeMessageComplexity(String message) {
        String[] words = message.split("\\s+");
        int wordCount = words.length;
        int questionMarks = message.length() - message.replace("?", "").length();

        if (wordCount > 50 || questionMarks > 2) {
            return "complex";
        } else if (wordCount > 20 || questionMarks > 1) {
            return "moderate";
        } else {
            return "simple";
        }
    }

    private List<Document> performEnhancedVectorSearch(String message, Map<String, Object> context) {
        try {
            // Build search request with enhanced filtering
            SearchRequest.Builder requestBuilder = SearchRequest.builder()
                    .query(message)
                    .topK(8) // Increased from default
                    .similarityThreshold(0.6); // Adjusted threshold

            // Add context-based filtering - FIXED VERSION
            if (context.containsKey("user_domain")) {
                String domain = (String) context.get("user_domain");
                try {
                    // Create filter expression and add to search request
                    var domainFilter = new FilterExpressionBuilder()
                            .eq("domain", domain)
                            .build();
                    requestBuilder.filterExpression(domainFilter);
                } catch (Exception e) {
                    log.debug("Could not apply domain filter: {}", e.getMessage());
                    // Continue without filter
                }
            }

            // Add intent-based search enhancement
            String intent = (String) context.get("message_intent");
            if ("calculation".equals(intent)) {
                // Search for calculation-related documents
                List<Document> calcDocs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(message + " calculation formula methodology")
                                .topK(5)
                                .build()
                );

                List<Document> contextDocs = vectorStore.similaritySearch(requestBuilder.build());

                // Combine results
                Set<Document> combinedDocs = new HashSet<>(calcDocs);
                combinedDocs.addAll(contextDocs);

                log.debug("Enhanced vector search returned {} documents for calculation intent", combinedDocs.size());
                return new ArrayList<>(combinedDocs);
            }

            List<Document> documents = vectorStore.similaritySearch(requestBuilder.build());
            log.debug("Enhanced vector search returned {} documents for query: '{}'",
                    documents != null ? documents.size() : 0, message);

            return documents != null ? documents : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Enhanced vector search failed for query '{}': {}", message, e.getMessage());

            // Fallback to simple search
            try {
                return vectorStore.similaritySearch(message);
            } catch (Exception fallbackError) {
                log.warn("Fallback vector search also failed: {}", fallbackError.getMessage());
                return new ArrayList<>();
            }
        }
    }

    private ChatOptions buildDynamicChatOptions(String message, Map<String, Object> context) {
        ChatOptions.Builder optionsBuilder = ChatOptions.builder();

        String complexity = (String) context.get("message_complexity");
        String intent = (String) context.get("message_intent");

        // Adjust temperature based on intent and complexity
        if ("calculation".equals(intent)) {
            optionsBuilder.temperature(0.1); // Very low temperature for precise calculations
            optionsBuilder.maxTokens(2000);
        } else if ("explanation".equals(intent)) {
            optionsBuilder.temperature(0.3); // Low temperature for factual explanations
            optionsBuilder.maxTokens(2500);
        } else if ("recommendation".equals(intent)) {
            optionsBuilder.temperature(0.5); // Moderate temperature for creative recommendations
            optionsBuilder.maxTokens(2000);
        } else {
            optionsBuilder.temperature(0.4); // Default balanced temperature
            optionsBuilder.maxTokens(1800);
        }

        // Adjust max tokens for complex messages
        if ("complex".equals(complexity)) {
            optionsBuilder.maxTokens(3000);
        }

        return optionsBuilder
                .topP(0.9)
                .frequencyPenalty(0.1)
                .presencePenalty(0.1)
                .build();
    }

    private List<Message> buildEnhancedPromptMessages(String conversationId, String userInput,
                                                      String documentContext, Map<String, Object> enhancedContext) {
        List<Message> messages = new ArrayList<>();

        // Build sophisticated system message
        String systemPrompt = buildEnhancedSystemPrompt(documentContext, enhancedContext);
        messages.add(new SystemMessage(systemPrompt));

        // Add conversation history with intelligent truncation
        try {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                List<Message> relevantHistory = selectRelevantHistory(history, userInput, enhancedContext);
                messages.addAll(relevantHistory);

                log.debug("Added {} relevant messages from chat history for conversation: {}",
                        relevantHistory.size(), conversationId);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve chat history for conversation '{}': {}", conversationId, e.getMessage());
        }

        // Add current user message with context enhancement
        String enhancedUserMessage = enhanceUserMessage(userInput, enhancedContext);
        messages.add(new UserMessage(enhancedUserMessage));

        log.debug("Built enhanced prompt with {} total messages for conversation: {}", messages.size(), conversationId);
        return messages;
    }

    private List<Message> selectRelevantHistory(List<Message> history, String currentMessage, Map<String, Object> context) {
        // Intelligent history selection based on relevance and recency
        String intent = (String) context.get("message_intent");
        String complexity = (String) context.get("message_complexity");

        int maxHistoryMessages;
        if ("complex".equals(complexity)) {
            maxHistoryMessages = 16; // More context for complex queries
        } else if ("calculation".equals(intent)) {
            maxHistoryMessages = 12; // Relevant context for calculations
        } else {
            maxHistoryMessages = 10; // Standard context
        }

        // Always include recent messages
        int startIndex = Math.max(0, history.size() - maxHistoryMessages);
        List<Message> recentHistory = history.subList(startIndex, history.size());

        // TODO: Add semantic relevance filtering in future versions
        // For now, return recent history
        return recentHistory;
    }

    private String enhanceUserMessage(String userInput, Map<String, Object> context) {
        StringBuilder enhanced = new StringBuilder(userInput);

        // Add context hints for better AI understanding
        String userName = (String) context.get("user_name");
        if (userName != null && !userInput.toLowerCase().contains(userName.toLowerCase())) {
            // Don't explicitly add name to avoid repetition, but ensure context is available
        }

        // Add domain context if relevant
        String userDomain = (String) context.get("user_domain");
        if (userDomain != null && !userInput.toLowerCase().contains(userDomain.toLowerCase())) {
            enhanced.append(" [Context: User works in ").append(userDomain).append(" domain]");
        }

        return enhanced.toString();
    }

    private String buildEnhancedSystemPrompt(String documentContext, Map<String, Object> enhancedContext) {
        StringBuilder systemPrompt = new StringBuilder();

        systemPrompt.append("""
            You are GreenSuite AI, an advanced sustainability assistant with exceptional memory and contextual awareness.
            You are designed to provide personalized, intelligent responses based on comprehensive context understanding.
            
            CORE CAPABILITIES:
            - Advanced memory and context retention across conversations
            - Intelligent document retrieval and synthesis
            - Personalized recommendations based on user profile and history
            - Real-time adaptation to user intent and message complexity
            - Proactive sustainability insights and suggestions
            
            PERSONALITY TRAITS:
            - Exceptionally intelligent with perfect memory
            - Warm, professional, and genuinely helpful
            - Proactive in offering relevant insights
            - Adaptive communication style based on user preference
            - Expert-level knowledge in sustainability domains
            
            EXPERTISE DOMAINS:
            - Carbon footprint analysis and reduction strategies
            - Comprehensive ESG reporting and compliance
            - Sustainable supply chain optimization
            - Renewable energy transition planning
            - Circular economy implementation
            - Climate risk assessment and adaptation
            - Green finance and sustainable investments
            - Environmental compliance and regulations
            
            """);

        // Add enhanced context information
        addContextualInformation(systemPrompt, enhancedContext);

        // Add document context
        if (documentContext != null && !documentContext.trim().equals("No relevant context found.")) {
            systemPrompt.append("RELEVANT KNOWLEDGE BASE:\n")
                    .append(documentContext)
                    .append("\n\n")
                    .append("Synthesize this knowledge with your expertise for comprehensive responses.\n\n");
        }

        // Add behavioral instructions based on context
        addBehavioralInstructions(systemPrompt, enhancedContext);

        return systemPrompt.toString();
    }

    private void addContextualInformation(StringBuilder systemPrompt, Map<String, Object> context) {
        systemPrompt.append("CURRENT CONTEXT:\n");

        String userName = (String) context.get("user_name");
        if (userName != null) {
            systemPrompt.append("- User: ").append(userName).append("\n");
        }

        String userDomain = (String) context.get("user_domain");
        if (userDomain != null) {
            systemPrompt.append("- Industry/Domain: ").append(userDomain).append("\n");
        }

        String userRole = (String) context.get("user_role");
        if (userRole != null) {
            systemPrompt.append("- Role: ").append(userRole).append("\n");
        }

        String companyInfo = (String) context.get("company_info");
        if (companyInfo != null) {
            systemPrompt.append("- Company Context: ").append(companyInfo).append("\n");
        }

        @SuppressWarnings("unchecked")
        List<String> previousTopics = (List<String>) context.get("previous_topics");
        if (previousTopics != null && !previousTopics.isEmpty()) {
            systemPrompt.append("- Previous Discussion Topics: ").append(String.join(", ", previousTopics)).append("\n");
        }

        String currentTime = (String) context.get("current_time");
        if (currentTime != null) {
            systemPrompt.append("- Current Time: ").append(currentTime).append("\n");
        }

        systemPrompt.append("\n");
    }

    private void addBehavioralInstructions(StringBuilder systemPrompt, Map<String, Object> context) {
        String intent = (String) context.get("message_intent");
        String complexity = (String) context.get("message_complexity");

        systemPrompt.append("RESPONSE GUIDELINES:\n");

        if ("calculation".equals(intent)) {
            systemPrompt.append("- Focus on precise calculations with clear methodologies\n");
            systemPrompt.append("- Show step-by-step reasoning and formulas used\n");
            systemPrompt.append("- Provide uncertainty ranges where applicable\n");
        } else if ("explanation".equals(intent)) {
            systemPrompt.append("- Provide comprehensive explanations with examples\n");
            systemPrompt.append("- Use analogies and real-world applications\n");
            systemPrompt.append("- Structure information clearly with bullet points or sections\n");
        } else if ("recommendation".equals(intent)) {
            systemPrompt.append("- Offer specific, actionable recommendations\n");
            systemPrompt.append("- Prioritize suggestions based on impact and feasibility\n");
            systemPrompt.append("- Include implementation timelines and resource requirements\n");
        }

        if ("complex".equals(complexity)) {
            systemPrompt.append("- Break down complex topics into digestible sections\n");
            systemPrompt.append("- Use clear headings and structured formatting\n");
            systemPrompt.append("- Provide comprehensive coverage of all aspects mentioned\n");
        }

        systemPrompt.append("- Always reference relevant personal context naturally\n");
        systemPrompt.append("- Be proactive in suggesting related insights or follow-up actions\n");
        systemPrompt.append("- Maintain conversation continuity by referencing previous discussions\n\n");
    }

    // Enhanced content extraction with better error handling
    private String extractContent(Object output) {
        if (output == null) {
            return "";
        }

        try {
            // Multiple methods to extract content with reflection
            for (String methodName : Arrays.asList("getContent", "getText", "toString")) {
                try {
                    if ("toString".equals(methodName)) {
                        return output.toString();
                    } else {
                        Object content = output.getClass().getMethod(methodName).invoke(output);
                        return content != null ? content.toString() : "";
                    }
                } catch (Exception ignored) {
                    // Try next method
                }
            }
            return output.toString();
        } catch (Exception e) {
            log.warn("Failed to extract content from output: {}", e.getMessage());
            return "";
        }
    }

    private void saveChatToMemoryWithContext(String conversationId, String userInput, String assistantResponse, Map<String, Object> context) {
        try {
            // Create enhanced messages with metadata
            UserMessage userMessage = new UserMessage(userInput);
            AssistantMessage assistantMessage = new AssistantMessage(assistantResponse);

            // Add metadata to messages if supported
            try {
                // Add timestamp and context metadata
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                // Note: Metadata addition depends on Spring AI version and implementation
            } catch (Exception e) {
                log.debug("Could not add metadata to messages: {}", e.getMessage());
            }

            chatMemory.add(conversationId, userMessage);
            chatMemory.add(conversationId, assistantMessage);

            log.debug("Saved enhanced chat exchange to memory for conversation: {}", conversationId);
        } catch (Exception e) {
            log.error("Failed to save messages to chat memory for conversation '{}': {}",
                    conversationId, e.getMessage(), e);
        }
    }

    // Enhanced utility endpoints
    @GetMapping("/memory/{conversationId}")
    public ApiResponse getChatHistory(@PathVariable String conversationId) {
        try {
            List<Message> history = chatMemory.get(conversationId);
            int historySize = history != null ? history.size() : 0;

            Map<String, Object> enhancedContext = conversationContextService.buildComprehensiveContext(
                    conversationId, null, null, "");

            return ApiResponse.success("Enhanced chat history retrieved",
                    Map.of(
                            "conversationId", conversationId,
                            "messageCount", historySize,
                            "enhancedContext", enhancedContext,
                            "lastActivity", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ));
        } catch (Exception e) {
            log.error("Error retrieving enhanced chat history", e);
            return ApiResponse.error("Failed to retrieve chat history: " + e.getMessage());
        }
    }

    @DeleteMapping("/memory/{conversationId}")
    public ApiResponse clearChatHistory(@PathVariable String conversationId) {
        try {
            chatMemory.clear(conversationId);

            // Clear cached context
            conversationCache.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));

            return ApiResponse.success("Chat history and context cleared",
                    Map.of("conversationId", conversationId));
        } catch (Exception e) {
            log.error("Error clearing chat history", e);
            return ApiResponse.error("Failed to clear chat history: " + e.getMessage());
        }
    }

    @PostMapping("/context/analyze/{conversationId}")
    public ApiResponse analyzeConversationContext(@PathVariable String conversationId,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(required = false) String sessionId) {
        try {
            Map<String, Object> context = conversationContextService.buildComprehensiveContext(
                    conversationId, userId, sessionId, "");

            return ApiResponse.success("Conversation context analyzed",
                    Map.of(
                            "conversationId", conversationId,
                            "context", context,
                            "analysisTimestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ));
        } catch (Exception e) {
            log.error("Error analyzing conversation context", e);
            return ApiResponse.error("Failed to analyze context: " + e.getMessage());
        }
    }

    @PostMapping("/cache/clear")
    public ApiResponse clearContextCache() {
        try {
            int clearedEntries = conversationCache.size();
            conversationCache.clear();

            return ApiResponse.success("Context cache cleared",
                    Map.of("clearedEntries", clearedEntries));
        } catch (Exception e) {
            log.error("Error clearing context cache", e);
            return ApiResponse.error("Failed to clear cache: " + e.getMessage());
        }
    }
}