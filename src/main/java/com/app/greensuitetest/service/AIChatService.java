package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.ApiResponse;
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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatService {

    private final StreamingChatModel streamingChatModel;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;
    private final ConversationContextService conversationContextService;
    private final DocumentContextService documentContextService;
    private final RinPersonalityService rinPersonalityService;
    private final ContextBuilderService contextBuilderService;

    /**
     * Process streaming chat with Rin Kazuki's personality
     */
    public Flux<String> processStreamingChat(String message, String conversationId, String userId, String sessionId) {
        try {
            // Update relationship dynamics
            rinPersonalityService.updateRelationshipDynamics(conversationId, userId, message);

            // Build enhanced context with personality awareness
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                conversationId, userId, sessionId, message);

            return processWithRinKazukiPersonalityStream(message, conversationId, enhancedContext);
        } catch (Exception e) {
            log.error("Error in Rin's streaming chat for conversation: {}", conversationId, e);
            return Flux.just(rinPersonalityService.getRinErrorResponseForException(e));
        }
    }

    /**
     * Process synchronous chat with Rin Kazuki's personality
     */
    public ApiResponse processSyncChat(String message, String conversationId, String userId, String sessionId) {
        try {
            rinPersonalityService.updateRelationshipDynamics(conversationId, userId, message);
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                conversationId, userId, sessionId, message);
            
            String response = processWithRinKazukiPersonality(message, conversationId, enhancedContext).block();

            // Update conversation context after successful response
            conversationContextService.updateContextAfterInteraction(conversationId, message, response);

            return ApiResponse.success("Rin's response", Map.of(
                    "response", response != null ? response : "Hmph! Something went wrong... not that I care!",
                    "conversationId", conversationId,
                    "personality_state", rinPersonalityService.getRinPersonalityState(conversationId, userId),
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "contextUsed", enhancedContext.keySet()
            ));
        } catch (Exception e) {
            log.error("Error in Rin's sync chat for conversation: {}", conversationId, e);
            return ApiResponse.error("Rin encountered an error: " + rinPersonalityService.getRinErrorResponseForException(e));
        }
    }

    /**
     * Process chat memory operations
     */
    public ApiResponse getChatHistory(String conversationId, String userId, String sessionId) {
        try {
            List<Message> history = chatMemory.get(conversationId);
            int historySize = history.size();

            Map<String, Object> enhancedContext = conversationContextService.buildComprehensiveContext(
                    conversationId, userId, sessionId, "");

            return ApiResponse.success("Hmph! Here's your chat history... not that I was keeping track!",
                    Map.of(
                            "conversationId", conversationId,
                            "messageCount", historySize,
                            "context", enhancedContext,
                            "rin_comment", "I remember everything... not because I care or anything!",
                            "lastActivity", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ));
        } catch (Exception e) {
            log.error("Error retrieving chat history", e);
            return ApiResponse.error("Tch! I had trouble getting your conversation history... " + 
                "Try using a different conversation ID or check if you have the right permissions! " + 
                "Not that I was keeping track of everything you said or anything!");
        }
    }

    public ApiResponse clearChatHistory(String conversationId, String userId, String sessionId) {
        try {
            chatMemory.clear(conversationId);
            conversationContextService.clearContextCache(conversationId);
            rinPersonalityService.clearRelationshipData(conversationId);

            return ApiResponse.success("Fine! I cleared everything... it's not like those memories meant anything to me!",
                    Map.of(
                            "conversationId", conversationId,
                            "rin_comment", "Don't think this means I forgot about you completely!"
                    ));
        } catch (Exception e) {
            log.error("Error clearing chat history", e);
            return ApiResponse.error("Hmph! I couldn't clear the history properly... " + 
                "Maybe check if the conversation ID exists or try refreshing and clearing again! " + 
                "It's not like I wanted to keep those memories anyway!");
        }
    }

    private Flux<String> processWithRinKazukiPersonalityStream(String message, String conversationId, Map<String, Object> enhancedContext) {
        return Mono.fromCallable(() -> {
                    // Check if this is a conversation history query - if so, skip vector search
                    boolean isConversationHistoryQuery = Boolean.TRUE.equals(enhancedContext.get("user_asking_about_conversation_history"));
        boolean isNameQuery = Boolean.TRUE.equals(enhancedContext.get("user_asking_about_name")) || 
                             Boolean.TRUE.equals(enhancedContext.get("user_asking_about_rin_name"));
                    
                    String documentContext = "No relevant context found.";
                    
                    // Only perform vector search for actual environmental questions, not meta queries
                    if (!isConversationHistoryQuery && !isNameQuery) {
                        // Multi-stage context retrieval with semantic filtering
                        List<Document> documents = performEnhancedVectorSearchWithSemanticFiltering(message, enhancedContext);
                        // Build comprehensive context
                        documentContext = documentContextService.buildIntelligentContext(documents, message);
                    } else {
                        log.debug("Skipping vector search for meta query: conversation history or name query");
                    }

                    // Build Rin Kazuki enhanced prompt messages
                    List<Message> promptMessages = rinPersonalityService.buildRinKazukiPromptMessages(
                        conversationId, message, documentContext, enhancedContext, chatMemory);

                    return promptMessages;
                })
                .flatMapMany(promptMessages -> {
                    // Dynamic chat options based on context and Rin's personality
                    ChatOptions options = rinPersonalityService.buildRinPersonalityChatOptions(message, enhancedContext);
                    Prompt prompt = new Prompt(promptMessages, options);

                    // Stream response with Rin's personality tracking
                    AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());

                    return streamingChatModel.stream(prompt)
                    .map(chatResponse -> {
                        // CRITICAL: Get content without aggressive processing
                        String content = extractContent(chatResponse.getResult().getOutput());
                        
                        // Log for debugging
                        if (!content.isEmpty()) {
                            log.debug("Streaming chunk: '{}' (length: {})", content, content.length());
                        }
                        
                        // Accumulate for memory storage
                        responseBuilder.get().append(content);
                        
                        // Return raw content for streaming - NO processing
                        return content;
                    })
                    .doOnComplete(() -> {
                        String finalResponse = responseBuilder.get().toString();
                        saveChatToMemoryWithContext(conversationId, message, finalResponse, enhancedContext);
                        conversationContextService.updateContextAfterInteraction(conversationId, message, finalResponse);
                    })
                    .doOnError(error -> {
                        log.error("Error in streaming chat", error);
                    });
        })
        .onErrorResume(error -> {
            log.error("Error in Rin's enhanced stream processing", error);
            return Flux.just(rinPersonalityService.getRinErrorResponseForException(error));
        });
    }
    

    private Mono<String> processWithRinKazukiPersonality(String message, String conversationId, Map<String, Object> enhancedContext) {
        return Mono.fromCallable(() -> {
                    // Check if this is a conversation history query - if so, skip vector search
                    boolean isConversationHistoryQuery = Boolean.TRUE.equals(enhancedContext.get("user_asking_about_conversation_history"));
                    boolean isNameQuery = Boolean.TRUE.equals(enhancedContext.get("user_asking_about_name")) || 
                                         Boolean.TRUE.equals(enhancedContext.get("user_asking_about_rin_name"));
                    
                    String documentContext = "No relevant context found.";
                    
                    // Only perform vector search for actual environmental questions, not meta queries
                    if (!isConversationHistoryQuery && !isNameQuery) {
                        List<Document> documents = performEnhancedVectorSearchWithSemanticFiltering(message, enhancedContext);
                        documentContext = documentContextService.buildIntelligentContext(documents, message);
                    } else {
                        log.debug("Skipping vector search for sync meta query: conversation history or name query");
                    }
                    
                    List<Message> promptMessages = rinPersonalityService.buildRinKazukiPromptMessages(
                        conversationId, message, documentContext, enhancedContext, chatMemory);

                    ChatOptions options = rinPersonalityService.buildRinPersonalityChatOptions(message, enhancedContext);
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
                        finalResponse = "Hmph! I couldn't think of a proper response... not that I was trying hard for you or anything!";
                    }

                    saveChatToMemoryWithContext(conversationId, message, finalResponse, enhancedContext);
                    return finalResponse;
                })
                .onErrorResume(error -> {
                    log.error("Error processing Rin's enhanced chat", error);
                    return Mono.just(rinPersonalityService.getRinErrorResponseForException(error));
                });
    }

    private List<Document> performEnhancedVectorSearchWithSemanticFiltering(String message, Map<String, Object> context) {
        try {
            SearchRequest.Builder requestBuilder = SearchRequest.builder()
                    .query(message)
                    .topK(12)
                    .similarityThreshold(0.5);

            List<Document> initialDocs = vectorStore.similaritySearch(requestBuilder.build());
            assert initialDocs != null;
            if (initialDocs.isEmpty()) {
                return new ArrayList<>();
            }

            return initialDocs.stream().limit(8).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Enhanced vector search failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String extractContent(Object output) {
        if (output == null) return "";
        try {
            // Try standard content extraction methods
            for (String methodName : Arrays.asList("getContent", "getText", "getTextContent")) {
                try {
                    Object content = output.getClass().getMethod(methodName).invoke(output);
                    if (content != null) {
                        String contentStr = content.toString();
                        // DON'T clean or process during streaming - return as-is
                        if (!contentStr.isEmpty()) {
                            return contentStr;
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            // Last resort: clean the toString() output
            String rawContent = output.toString();
            if (rawContent.contains("Message{") && rawContent.contains("content=")) {
                return cleanRawMessageContent(rawContent);
            }
            return rawContent;
        } catch (Exception e) {
            log.warn("Failed to extract content from output: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Clean raw message content to remove Spring AI message object formatting
     * Ensures only clean text is returned to frontend
     */
    private String cleanRawMessageContent(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return "";
        }
        
        // Don't process if it's already clean text (no message object markers)
        if (!rawContent.contains("Message{") && !rawContent.contains("content=")) {
            return rawContent.trim();
        }
        
        try {
            // Pattern 1: Extract from content='...' 
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("content='([^']*)'");
            java.util.regex.Matcher matcher = pattern.matcher(rawContent);
            if (matcher.find()) {
                return matcher.group(1); // Don't trim here to preserve intentional whitespace
            }
            
            // Pattern 2: Extract from textContent=...
            pattern = java.util.regex.Pattern.compile("textContent=([^,}]*)");
            matcher = pattern.matcher(rawContent);
            if (matcher.find()) {
                return matcher.group(1);
            }
            
        } catch (Exception e) {
            log.debug("Error cleaning message content: {}", e.getMessage());
        }
        
        // Fallback: return original if cleaning fails
        return rawContent.trim();
    }

    private void saveChatToMemoryWithContext(String conversationId, String userInput, String assistantResponse, Map<String, Object> context) {
        try {
            UserMessage userMessage = new UserMessage(userInput);
            AssistantMessage assistantMessage = new AssistantMessage(assistantResponse);

            chatMemory.add(conversationId, userMessage);
            chatMemory.add(conversationId, assistantMessage);

            log.debug("Saved Rin's chat exchange to memory for conversation: {}", conversationId);
        } catch (Exception e) {
            log.error("Failed to save Rin's messages to chat memory for conversation '{}': {}",
                    conversationId, e.getMessage(), e);
        }
    }
} 