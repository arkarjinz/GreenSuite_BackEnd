package com.app.greensuitetest.service;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.exception.InsufficientCreditsException;
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
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

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
    private final AICreditService aiCreditService; // Add credit service

    /**
     * Process streaming chat with Rin's personality and credit deduction
     */
    public Flux<String> processStreamingChat(String message, String conversationId, String userId, String sessionId) {
        try {
            // Check and deduct credits BEFORE processing
            if (userId != null) {
                if (!aiCreditService.hasCreditsForChat(userId)) {
                    return Flux.just(rinPersonalityService.getRinInsufficientCreditsMessage());
                }
            }

            // Update relationship dynamics
            rinPersonalityService.updateRelationshipDynamics(conversationId, userId, message);

            // Build enhanced context with personality awareness
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                conversationId, userId, sessionId, message);

            return processWithRinKazukiPersonalityStream(message, conversationId, enhancedContext, userId)
                .onErrorResume(error -> {
                    log.error("Error in Rin's streaming chat for conversation: {}", conversationId, error);
                    
                    // Refund credits if chat failed after deduction
                    if (userId != null) {
                        try {
                            aiCreditService.refundCredits(userId, 2, "Streaming chat failed");
                            log.info("Refunded 2 credits to user {} due to streaming failure", userId);
                        } catch (Exception refundError) {
                            log.error("Failed to refund credits to user {}: {}", userId, refundError.getMessage());
                        }
                    }
                    
                    // Return fallback response
                    String fallbackResponse = getFallbackResponse(error);
                    return Flux.just(fallbackResponse);
                });
        } catch (InsufficientCreditsException e) {
            log.warn("Insufficient credits for user {}: {}", userId, e.getMessage());
            return Flux.just(rinPersonalityService.getRinInsufficientCreditsMessage(e));
        } catch (Exception e) {
            log.error("Error in Rin's streaming chat for conversation: {}", conversationId, e);
            return Flux.just(getFallbackResponse(e));
        }
    }

    /**
     * Process synchronous chat with Rin's personality and credit deduction
     */
    public ApiResponse processSyncChat(String message, String conversationId, String userId, String sessionId) {
        try {
            // Check and deduct credits BEFORE processing
            int remainingCredits = 0;
            if (userId != null) {
                if (!aiCreditService.hasCreditsForChat(userId)) {
                    return createInsufficientCreditsResponse(userId);
                }
                remainingCredits = aiCreditService.deductChatCredits(userId);
            }

            rinPersonalityService.updateRelationshipDynamics(conversationId, userId, message);
            Map<String, Object> enhancedContext = contextBuilderService.buildEnhancedContextWithPersonality(
                conversationId, userId, sessionId, message);
            
            String response = processWithRinKazukiPersonality(message, conversationId, enhancedContext, userId).block();

            // Update conversation context after successful response
            conversationContextService.updateContextAfterInteraction(conversationId, message, response);

            Map<String, Object> responseData = Map.of(
                "response", response != null ? response : "I'm having a bit of trouble thinking of a proper response...",
                "conversationId", conversationId,
                "personality_state", rinPersonalityService.getRinPersonalityState(conversationId, userId),
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "contextUsed", enhancedContext.keySet()
            );

            // Add credit info if user is authenticated
            if (userId != null) {
                Map<String, Object> creditInfo = Map.of(
                    "creditsUsed", 2,
                    "remainingCredits", remainingCredits,
                    "isLowOnCredits", aiCreditService.isLowOnCredits(userId)
                );
                
                // Combine response data with credit info
                Map<String, Object> combinedData = new java.util.HashMap<>(responseData);
                combinedData.put("creditInfo", creditInfo);
                responseData = combinedData;
            }

            return ApiResponse.success("Rin's response", responseData);
            
        } catch (InsufficientCreditsException e) {
            log.warn("Insufficient credits for user {}: {}", userId, e.getMessage());
            return createInsufficientCreditsResponse(userId, e);
        } catch (Exception e) {
            log.error("Error in Rin's sync chat for conversation: {}", conversationId, e);
            
            // Refund credits if chat failed after deduction
            if (userId != null) {
                try {
                    aiCreditService.refundCredits(userId, 2, "Chat processing failed");
                    log.info("Refunded 2 credits to user {} due to chat failure", userId);
                } catch (Exception refundError) {
                    log.error("Failed to refund credits to user {}: {}", userId, refundError.getMessage());
                }
            }
            
            return ApiResponse.error("Rin encountered an error: " + getFallbackResponse(e));
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

            Map<String, Object> responseData = Map.of(
                "conversationId", conversationId,
                "messageCount", historySize,
                "context", enhancedContext,
                "rin_comment", "I remember everything... not because I care or anything!",
                "lastActivity", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );

            // Add credit info if user is authenticated
            if (userId != null) {
                Map<String, Object> creditStats = aiCreditService.getCreditStats(userId);
                Map<String, Object> combinedData = new java.util.HashMap<>(responseData);
                combinedData.put("creditInfo", creditStats);
                responseData = combinedData;
            }

            return ApiResponse.success("I remember everything... not because I care or anything!", responseData);
        } catch (Exception e) {
            log.error("Error retrieving chat history", e);
            return ApiResponse.error("I had trouble getting your conversation history... " + 
                "Try using a different conversation ID or check if you have the right permissions! " + 
                "Not that I was keeping track of everything you said or anything!");
        }
    }

    public ApiResponse clearChatHistory(String conversationId, String userId, String sessionId) {
        try {
            chatMemory.clear(conversationId);
            conversationContextService.clearContextCache(conversationId);
            rinPersonalityService.clearRelationshipData(conversationId);

            Map<String, Object> responseData = Map.of(
                "conversationId", conversationId,
                "rin_comment", "I've cleared our conversation history, but I'll always be here to help with environmental questions."
            );

            // Add credit info if user is authenticated
            if (userId != null) {
                Map<String, Object> creditStats = aiCreditService.getCreditStats(userId);
                Map<String, Object> combinedData = new java.util.HashMap<>(responseData);
                combinedData.put("creditInfo", creditStats);
                responseData = combinedData;
            }

            return ApiResponse.success("I've cleared our conversation history. Let's start fresh with your environmental questions!", responseData);
        } catch (Exception e) {
            log.error("Error clearing chat history", e);
            return ApiResponse.error("I'm having trouble clearing the history... " + 
                "Perhaps try refreshing and clearing again? I'm here to help with your environmental questions whenever you're ready.");
        }
    }

    private Flux<String> processWithRinKazukiPersonalityStream(String message, String conversationId, Map<String, Object> enhancedContext, String userId) {
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

                    // Build Rin enhanced prompt messages
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
                    AtomicReference<Boolean> creditsDeducted = new AtomicReference<>(false);

                    return streamingChatModel.stream(prompt)
                    .map(chatResponse -> {
                        // Deduct credits on first successful chunk
                        if (userId != null && !creditsDeducted.get()) {
                            try {
                                aiCreditService.deductChatCredits(userId);
                                creditsDeducted.set(true);
                                log.info("Deducted 2 AI credits from user {} during streaming", userId);
                            } catch (Exception e) {
                                log.warn("Failed to deduct credits during streaming for user {}: {}", userId, e.getMessage());
                            }
                        }

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
                        // Refund credits if streaming fails after deduction
                        if (userId != null && creditsDeducted.get()) {
                            try {
                                aiCreditService.refundCredits(userId, 2, "Streaming chat failed");
                                log.info("Refunded 2 credits to user {} due to streaming failure", userId);
                            } catch (Exception refundError) {
                                log.error("Failed to refund credits to user {}: {}", userId, refundError.getMessage());
                            }
                        }
                        
                        // Handle different types of streaming errors
                        if (isClientDisconnectionError(error)) {
                            log.info("Client disconnected during streaming for conversation: {}", conversationId);
                        } else {
                            log.error("Error in streaming chat for conversation: {}", conversationId, error);
                        }
                    })
                    .onErrorResume(error -> {
                        // Handle client disconnection gracefully
                        if (isClientDisconnectionError(error)) {
                            log.info("Client disconnected during streaming, stopping gracefully for conversation: {}", conversationId);
                            return Flux.empty(); // Stop gracefully without error
                        }
                        
                        // For other errors, return error message
                        log.error("Streaming error for conversation: {}", conversationId, error);
                        return Flux.just(rinPersonalityService.getRinErrorResponseForException(error));
                    });
        })
        .onErrorResume(error -> {
            log.error("Error in Rin's enhanced stream processing", error);
            return Flux.just(rinPersonalityService.getRinErrorResponseForException(error));
        });
    }

    private Mono<String> processWithRinKazukiPersonality(String message, String conversationId, Map<String, Object> enhancedContext, String userId) {
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
                        finalResponse = "I'm having a bit of trouble thinking of a proper response... Perhaps we could try a different approach to your environmental question?";
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
            for (String methodName : Arrays.asList("getContent", "getText", "getTextContent")) {
                try {
                    Object content = output.getClass().getMethod(methodName).invoke(output);
                    if (content != null) {
                        String contentStr = content.toString();
                        if (!contentStr.isEmpty()) {
                            return contentStr;
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            // Fallback to toString but filter out debug information
            String fallbackContent = output.toString();
            
            // Filter out debug messages like "AssistantMessage [messageType=ASSISTANT...]"
            if (fallbackContent.startsWith("AssistantMessage [") || 
                fallbackContent.contains("messageType=ASSISTANT") ||
                fallbackContent.contains("toolCalls=[]") ||
                fallbackContent.contains("metadata=")) {
                return "";
            }
            
            return fallbackContent;
        } catch (Exception e) {
            log.warn("Failed to extract content from output: {}", e.getMessage());
            return "";
        }
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

    private ApiResponse createInsufficientCreditsResponse(String userId) {
        return createInsufficientCreditsResponse(userId, null);
    }

    private ApiResponse createInsufficientCreditsResponse(String userId, InsufficientCreditsException e) {
        Map<String, Object> creditStats = aiCreditService.getCreditStats(userId);
        
        Map<String, Object> errorData = new java.util.HashMap<>(creditStats);
        if (e != null && e.getDetails() != null) {
            errorData.putAll(e.getDetails());
        }
        
        String rinMessage = rinPersonalityService.getRinInsufficientCreditsMessage(e);
        
        return ApiResponse.error(rinMessage, errorData);
    }

    private String getFallbackResponse(Throwable error) {
        if (error instanceof InsufficientCreditsException) {
            return rinPersonalityService.getRinInsufficientCreditsMessage((InsufficientCreditsException) error);
        } else if (error instanceof WebClientResponseException) {
            WebClientResponseException webError = (WebClientResponseException) error;
            if (webError.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                return rinPersonalityService.getRinServiceUnavailableMessage();
            } else if (webError.getStatusCode() == HttpStatus.BAD_GATEWAY) {
                return rinPersonalityService.getRinBadGatewayMessage();
            } else {
                return rinPersonalityService.getRinErrorResponseForException(error);
            }
        } else if (error instanceof ConnectException) {
            return rinPersonalityService.getRinConnectionErrorMessage();
        } else if (error instanceof TimeoutException) {
            return rinPersonalityService.getRinTimeoutMessage();
        } else {
            return rinPersonalityService.getRinErrorResponseForException(error);
        }
    }

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
        
        // Check for WebClient response exceptions that indicate client issues
        if (error instanceof WebClientResponseException) {
            WebClientResponseException webError = (WebClientResponseException) error;
            return webError.getStatusCode() == HttpStatus.REQUEST_TIMEOUT ||
                   webError.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT ||
                   webError.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE ||
                   webError.getStatusCode() == HttpStatus.BAD_GATEWAY;
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