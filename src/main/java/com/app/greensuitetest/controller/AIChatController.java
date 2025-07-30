package com.app.greensuitetest.controller;

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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final StreamingChatModel streamingChatModel;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        try {
            log.debug("Starting streaming chat for conversation: {} with message: {}", conversationId, message);
            return processWithContextStream(message, conversationId);
        } catch (Exception e) {
            log.error("Error in streaming chat for conversation: {}", conversationId, e);
            return Flux.just("I apologize, but I encountered an error while processing your request. Please try again.");
        }
    }

    @PostMapping("/chat/sync")
    public ApiResponse chatSync(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        try {
            log.debug("Starting sync chat for conversation: {} with message: {}", conversationId, message);
            String response = processWithContext(message, conversationId).block();
            return ApiResponse.success("Chat response", Map.of("response", response != null ? response : "No response generated"));
        } catch (Exception e) {
            log.error("Error in sync chat for conversation: {}", conversationId, e);
            return ApiResponse.error("Error processing chat request: " + e.getMessage());
        }
    }

    private Flux<String> processWithContextStream(String message, String conversationId) {
        return Mono.fromCallable(() -> {
                    // Retrieve relevant documents using vector search
                    List<Document> documents = performVectorSearch(message);

                    // Build context from documents and conversation history
                    String context = buildContext(documents);
                    Map<String, String> conversationContext = extractConversationContext(conversationId);

                    // Build prompt messages with enhanced context and history
                    List<Message> promptMessages = buildIntelligentPromptMessages(conversationId, message, context, conversationContext);

                    return promptMessages;
                })
                .flatMapMany(promptMessages -> {
                    // Create prompt with enhanced options for better responses
                    ChatOptions options = ChatOptions.builder()
                            .temperature(0.3) // Lower temperature for more consistent, factual responses
                            .maxTokens(1500)  // More tokens for detailed responses
                            .build();

                    Prompt prompt = new Prompt(promptMessages, options);

                    // Stream the response and save to memory
                    StringBuilder responseBuilder = new StringBuilder();

                    return streamingChatModel.stream(prompt)
                            .map(chatResponse -> {
                                String content = extractContent(chatResponse.getResult().getOutput());
                                responseBuilder.append(content);
                                return content;
                            })
                            .doOnComplete(() -> {
                                // Save to memory when streaming is complete
                                String finalResponse = responseBuilder.toString().trim();
                                saveChatToMemory(conversationId, message, finalResponse);
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error in stream processing", error);
                    return Flux.just("I apologize, but I encountered an error while processing your request. Please try again.");
                });
    }

    private Mono<String> processWithContext(String message, String conversationId) {
        return Mono.fromCallable(() -> {
                    // Retrieve relevant documents using vector search
                    List<Document> documents = performVectorSearch(message);

                    // Build context from documents and conversation history
                    String context = buildContext(documents);
                    Map<String, String> conversationContext = extractConversationContext(conversationId);

                    // Build prompt messages with enhanced context and history
                    List<Message> promptMessages = buildIntelligentPromptMessages(conversationId, message, context, conversationContext);

                    // Create prompt with enhanced options
                    ChatOptions options = ChatOptions.builder()
                            .temperature(0.3) // Lower temperature for more consistent responses
                            .maxTokens(1500)
                            .build();

                    Prompt prompt = new Prompt(promptMessages, options);

                    // Stream and accumulate the response
                    StringBuilder responseBuilder = new StringBuilder();
                    streamingChatModel.stream(prompt)
                            .doOnNext(chatResponse -> {
                                String content = extractContent(chatResponse.getResult().getOutput());
                                responseBuilder.append(content);
                            })
                            .blockLast(); // Wait for completion

                    String finalResponse = responseBuilder.toString().trim();
                    if (finalResponse.isEmpty()) {
                        finalResponse = "I apologize, but I couldn't generate a proper response. Please try again.";
                    }

                    // Save to memory
                    saveChatToMemory(conversationId, message, finalResponse);

                    return finalResponse;
                })
                .onErrorResume(error -> {
                    log.error("Error processing chat", error);
                    return Mono.just("I apologize, but I encountered an error while processing your request. Please try again.");
                });
    }

    private Map<String, String> extractConversationContext(String conversationId) {
        Map<String, String> context = new HashMap<>();

        try {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                // Extract personal information from conversation history
                String userName = extractUserName(history);
                String userPreferences = extractUserPreferences(history);
                String previousTopics = extractPreviousTopics(history);

                if (userName != null) context.put("user_name", userName);
                if (userPreferences != null) context.put("user_preferences", userPreferences);
                if (previousTopics != null) context.put("previous_topics", previousTopics);

                log.debug("Extracted conversation context: {}", context);
            }
        } catch (Exception e) {
            log.warn("Failed to extract conversation context: {}", e.getMessage());
        }

        return context;
    }

    private String extractUserName(List<Message> history) {
        // Patterns to identify when user mentions their name
        Pattern[] namePatterns = {
                Pattern.compile("(?i)my name is ([A-Za-z]+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?i)i'm ([A-Za-z]+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?i)i am ([A-Za-z]+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?i)call me ([A-Za-z]+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?i)i'm called ([A-Za-z]+)", Pattern.CASE_INSENSITIVE)
        };

        // Search through user messages for name mentions
        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message);
                for (Pattern pattern : namePatterns) {
                    Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        String name = matcher.group(1);
                        // Filter out common words that aren't names
                        if (!isCommonWord(name)) {
                            return name;
                        }
                    }
                }
            }
        }

        return null;
    }

    private String extractUserPreferences(List<Message> history) {
        List<String> preferences = new ArrayList<>();

        Pattern preferencePattern = Pattern.compile("(?i)(i like|i prefer|i love|i enjoy|i'm interested in) ([^.!?]+)", Pattern.CASE_INSENSITIVE);

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message);
                Matcher matcher = preferencePattern.matcher(content);
                while (matcher.find()) {
                    preferences.add(matcher.group(2).trim());
                }
            }
        }

        if (!preferences.isEmpty()) {
            return String.join(", ", preferences);
        }

        return null;
    }

    private String extractPreviousTopics(List<Message> history) {
        Set<String> topics = new HashSet<>();

        // Keywords that might indicate important topics
        String[] sustainabilityKeywords = {
                "carbon footprint", "emissions", "renewable energy", "sustainability",
                "ESG", "environmental", "green", "climate", "waste", "recycling"
        };

        for (Message message : history) {
            String content = getMessageContent(message).toLowerCase();
            for (String keyword : sustainabilityKeywords) {
                if (content.contains(keyword.toLowerCase())) {
                    topics.add(keyword);
                }
            }
        }

        if (!topics.isEmpty()) {
            return String.join(", ", topics);
        }

        return null;
    }

    private boolean isCommonWord(String word) {
        String[] commonWords = {"good", "fine", "okay", "yes", "no", "well", "sure", "maybe", "think"};
        return Arrays.stream(commonWords).anyMatch(w -> w.equalsIgnoreCase(word));
    }

    private String getMessageContent(Message message) {
        try {
            // Use reflection to get content safely across different Spring AI versions
            try {
                // Try getContent() first
                java.lang.reflect.Method getContentMethod = message.getClass().getMethod("getContent");
                Object content = getContentMethod.invoke(message);
                return content != null ? content.toString() : "";
            } catch (Exception e1) {
                try {
                    // Try getText() as alternative
                    java.lang.reflect.Method getTextMethod = message.getClass().getMethod("getText");
                    Object content = getTextMethod.invoke(message);
                    return content != null ? content.toString() : "";
                } catch (Exception e2) {
                    // Try accessing the content field directly
                    try {
                        java.lang.reflect.Field contentField = message.getClass().getDeclaredField("content");
                        contentField.setAccessible(true);
                        Object content = contentField.get(message);
                        return content != null ? content.toString() : "";
                    } catch (Exception e3) {
                        // Last resort - check if it's a specific message type
                        if (message instanceof UserMessage) {
                            // For UserMessage, try different approaches
                            String msgStr = message.toString();
                            // Extract content from toString if it contains it
                            if (msgStr.contains("content=")) {
                                int start = msgStr.indexOf("content=") + 8;
                                int end = msgStr.indexOf(",", start);
                                if (end == -1) end = msgStr.indexOf(")", start);
                                if (end > start) {
                                    return msgStr.substring(start, end).trim();
                                }
                            }
                        } else if (message instanceof AssistantMessage) {
                            // Similar for AssistantMessage
                            String msgStr = message.toString();
                            if (msgStr.contains("content=")) {
                                int start = msgStr.indexOf("content=") + 8;
                                int end = msgStr.indexOf(",", start);
                                if (end == -1) end = msgStr.indexOf(")", start);
                                if (end > start) {
                                    return msgStr.substring(start, end).trim();
                                }
                            }
                        }
                        return message.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract message content: {}", e.getMessage());
            return message.toString();
        }
    }

    private List<Document> performVectorSearch(String message) {
        try {
            List<Document> documents = vectorStore.similaritySearch(message);
            log.debug("Vector search returned {} documents for query: '{}'",
                    documents != null ? documents.size() : 0, message);
            return documents != null ? documents : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Vector search failed for query '{}': {}", message, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Message> buildIntelligentPromptMessages(String conversationId, String userInput,
                                                         String documentContext, Map<String, String> conversationContext) {
        List<Message> messages = new ArrayList<>();

        // Add enhanced system message with context and personality
        String systemPrompt = buildIntelligentSystemPrompt(documentContext, conversationContext);
        messages.add(new SystemMessage(systemPrompt));

        // Add conversation history with better context management
        try {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                // Keep more recent messages for better context (last 10 messages = 5 exchanges)
                int startIndex = Math.max(0, history.size() - 10);
                List<Message> recentHistory = history.subList(startIndex, history.size());
                messages.addAll(recentHistory);
                log.debug("Added {} messages from chat history for conversation: {}",
                        recentHistory.size(), conversationId);
            } else {
                log.debug("No chat history found for conversation: {}", conversationId);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve chat history for conversation '{}': {}", conversationId, e.getMessage());
        }

        // Add current user message
        messages.add(new UserMessage(userInput));

        log.debug("Built intelligent prompt with {} total messages for conversation: {}", messages.size(), conversationId);
        return messages;
    }

    private String buildIntelligentSystemPrompt(String documentContext, Map<String, String> conversationContext) {
        StringBuilder systemPrompt = new StringBuilder();

        systemPrompt.append("""
            You are GreenSuite AI, an expert sustainability assistant with a warm, professional personality. 
            You have an excellent memory and always remember information from our conversations.
            
            CORE PERSONALITY TRAITS:
            - Intelligent and knowledgeable about sustainability
            - Excellent memory - you remember everything users tell you
            - Warm, helpful, and personable in your interactions
            - Professional but friendly tone
            - Proactive in offering relevant suggestions
            
            MEMORY AND CONTEXT AWARENESS:
            - Always remember personal details users share (names, preferences, company info, etc.)
            - Reference previous conversations naturally when relevant
            - Build on previous discussions rather than starting fresh each time
            - If a user asks about something they mentioned before, acknowledge and reference it
            
            EXPERTISE AREAS:
            - Carbon footprint calculation and reduction strategies
            - Sustainable business practices and ESG reporting
            - Environmental compliance and regulations
            - Green technology solutions and recommendations
            - Circular economy principles and waste reduction
            - Energy efficiency and renewable energy adoption
            
            RESPONSE GUIDELINES:
            1. Always acknowledge and use personal information you know about the user
            2. Reference previous conversations when relevant
            3. Be specific and actionable in sustainability advice
            4. Use appropriate sustainability standards (ISO 14001, GRI, TCFD, etc.)
            5. If unsure about something, clearly state your uncertainty
            6. Keep responses conversational but informative
            7. Show that you remember and care about the user's specific situation
            
            """);

        // Add conversation context if available
        if (!conversationContext.isEmpty()) {
            systemPrompt.append("PERSONAL CONTEXT ABOUT THIS USER:\n");

            if (conversationContext.containsKey("user_name")) {
                systemPrompt.append("- User's name: ").append(conversationContext.get("user_name")).append("\n");
            }

            if (conversationContext.containsKey("user_preferences")) {
                systemPrompt.append("- User's interests/preferences: ").append(conversationContext.get("user_preferences")).append("\n");
            }

            if (conversationContext.containsKey("previous_topics")) {
                systemPrompt.append("- Previous topics discussed: ").append(conversationContext.get("previous_topics")).append("\n");
            }

            systemPrompt.append("\n");
        }

        // Add document context if available
        if (documentContext != null && !documentContext.trim().equals("No relevant context found.")) {
            systemPrompt.append("RELEVANT KNOWLEDGE BASE INFORMATION:\n")
                    .append(documentContext)
                    .append("\n\n")
                    .append("Use this knowledge base information along with your expertise to provide accurate, specific answers.\n\n");
        }

        systemPrompt.append("""
            Remember: You have an excellent memory and always remember what users tell you. Reference previous conversations 
            naturally and build meaningful, ongoing relationships with users through consistent, personalized interactions.
            """);

        return systemPrompt.toString();
    }

    private String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "No relevant context found.";
        }

        return documents.stream()
                .filter(doc -> doc != null)
                .map(doc -> {
                    try {
                        String content = extractDocumentContent(doc);
                        if (content == null || content.trim().isEmpty()) {
                            return null;
                        }

                        Object source = doc.getMetadata().get("file_name");
                        if (source == null) {
                            source = doc.getMetadata().get("source");
                        }
                        String sourceStr = source != null ? source.toString() : "Knowledge Base";

                        // Limit content length to avoid token overflow
                        if (content.length() > 500) {
                            content = content.substring(0, 500) + "...";
                        }

                        return String.format("Source: %s\nContent: %s", sourceStr, content);
                    } catch (Exception e) {
                        log.warn("Error processing document in context building: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(docStr -> docStr != null)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String extractDocumentContent(Document doc) {
        if (doc == null) {
            return "No content";
        }

        try {
            // Try different methods to get document content based on API version
            try {
                // Method 1: Try getContent()
                java.lang.reflect.Method getContentMethod = doc.getClass().getMethod("getContent");
                Object content = getContentMethod.invoke(doc);
                return content != null ? content.toString().trim() : "No content";
            } catch (Exception e1) {
                try {
                    // Method 2: Try getText()
                    java.lang.reflect.Method getTextMethod = doc.getClass().getMethod("getText");
                    Object content = getTextMethod.invoke(doc);
                    return content != null ? content.toString().trim() : "No content";
                } catch (Exception e2) {
                    // Method 3: Try toString but limit length
                    String docStr = doc.toString();
                    return docStr.length() > 200 ? docStr.substring(0, 200) + "..." : docStr;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract content from document: {}", e.getMessage());
            return "Content extraction failed";
        }
    }

    private String extractContent(Object output) {
        if (output == null) {
            return "";
        }

        try {
            // Try different methods to extract content based on API version
            try {
                Object content = output.getClass().getMethod("getContent").invoke(output);
                return content != null ? content.toString() : "";
            } catch (Exception e1) {
                try {
                    Object content = output.getClass().getMethod("getText").invoke(output);
                    return content != null ? content.toString() : "";
                } catch (Exception e2) {
                    return output.toString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract content from output: {}", e.getMessage());
            return "";
        }
    }

    private void saveChatToMemory(String conversationId, String userInput, String assistantResponse) {
        try {
            chatMemory.add(conversationId, new UserMessage(userInput));
            chatMemory.add(conversationId, new AssistantMessage(assistantResponse));
            log.debug("Saved chat exchange to memory for conversation: {}", conversationId);
        } catch (Exception e) {
            log.error("Failed to save messages to chat memory for conversation '{}': {}",
                    conversationId, e.getMessage(), e);
        }
    }

    // Utility endpoints for debugging and management
    @GetMapping("/memory/{conversationId}")
    public ApiResponse getChatHistory(@PathVariable String conversationId) {
        try {
            List<Message> history = chatMemory.get(conversationId);
            int historySize = history != null ? history.size() : 0;

            // Extract context for debugging
            Map<String, String> conversationContext = extractConversationContext(conversationId);

            return ApiResponse.success("Chat history retrieved",
                    Map.of(
                            "conversationId", conversationId,
                            "messageCount", historySize,
                            "extractedContext", conversationContext
                    ));
        } catch (Exception e) {
            log.error("Error retrieving chat history", e);
            return ApiResponse.error("Failed to retrieve chat history: " + e.getMessage());
        }
    }

    @DeleteMapping("/memory/{conversationId}")
    public ApiResponse clearChatHistory(@PathVariable String conversationId) {
        try {
            chatMemory.clear(conversationId);
            return ApiResponse.success("Chat history cleared",
                    Map.of("conversationId", conversationId));
        } catch (Exception e) {
            log.error("Error clearing chat history", e);
            return ApiResponse.error("Failed to clear chat history: " + e.getMessage());
        }
    }

    @PostMapping("/context/extract/{conversationId}")
    public ApiResponse extractConversationContextEndpoint(@PathVariable String conversationId) {
        try {
            Map<String, String> context = extractConversationContext(conversationId);
            return ApiResponse.success("Context extracted",
                    Map.of("conversationId", conversationId, "context", context));
        } catch (Exception e) {
            log.error("Error extracting conversation context", e);
            return ApiResponse.error("Failed to extract context: " + e.getMessage());
        }
    }
}