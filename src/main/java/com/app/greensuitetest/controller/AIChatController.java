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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

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
            // Immediately emit typing indicator
            Flux<String> typingIndicator = Flux.just("typing...");

            // Process the actual response
            Mono<String> responseMonoWithContext = processWithContext(message, conversationId);

            // Concatenate typing indicator with actual response
            return Flux.concat(typingIndicator, responseMonoWithContext.flux());

        } catch (Exception e) {
            log.error("Error in streaming chat", e);
            return Flux.just("Error: " + e.getMessage());
        }
    }

    @PostMapping("/chat/sync")
    public ApiResponse chatSync(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        try {
            String response = processWithContext(message, conversationId).block();
            assert response != null;
            return ApiResponse.success("Chat response", Map.of("response", response));
        } catch (Exception e) {
            log.error("Error in sync chat", e);
            return ApiResponse.error("Error processing chat request: " + e.getMessage());
        }
    }

    private Mono<String> processWithContext(String message, String conversationId) {
        return Mono.fromCallable(() -> {
                    // Retrieve relevant documents using vector search
                    List<Document> documents = performVectorSearch(message);

                    // Build context from documents
                    String context = buildContext(documents);

                    // Build prompt messages with context and history
                    List<Message> promptMessages = buildPromptMessages(conversationId, message, context);

                    // Create prompt with options
                    ChatOptions options = ChatOptions.builder()
                            .temperature(0.7)
                            .maxTokens(1000)
                            .build();

                    Prompt prompt = new Prompt(promptMessages, options);

                    // Stream and accumulate the response
                    return streamingChatModel.stream(prompt)
                            .reduce(new StringBuilder(), (buffer, chatResponse) -> {
                                String content = extractContent(chatResponse.getResult().getOutput());
                                buffer.append(content);
                                return buffer;
                            })
                            .map(StringBuilder::toString)
                            .block();
                })
                .flatMap(rawResponse -> processFinalResponse(conversationId, message, rawResponse));
    }

    private List<Document> performVectorSearch(String message) {
        try {
            // Try to use the vector store for similarity search
            // MongoDB Atlas vector store should work with simple similaritySearch
            List<Document> documents = vectorStore.similaritySearch(message);
            assert documents != null;
            log.debug("Vector search returned {} documents for query: {}", documents.size(), message);
            return documents;
        } catch (Exception e) {
            log.warn("Vector search failed: {}, continuing without context", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Message> buildPromptMessages(String conversationId, String userInput, String context) {
        List<Message> messages = new ArrayList<>();

        // Add system message with context
        String systemPrompt = String.format("""
            You are a sustainability expert for GreenSuite. Use the following context to answer questions.
            If you don't know the answer from the context, say you don't know. Be concise and factual.
            
            Context:
            %s
            """, context);

        messages.add(new SystemMessage(systemPrompt));

        // Add conversation history
        try {
            List<Message> history = chatMemory.get(conversationId);
            if (!history.isEmpty()) {
                // Limit to last 10 messages to avoid token limits
                int startIndex = Math.max(0, history.size() - 10);
                messages.addAll(history.subList(startIndex, history.size()));
            }
        } catch (Exception e) {
            log.warn("Failed to get chat history for conversationId: {}", conversationId, e);
        }

        // Add current user message
        messages.add(new UserMessage(userInput));

        return messages;
    }

    private String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "No relevant context found.";
        }

        return documents.stream()
                .map(doc -> {
                    String content = extractDocumentContent(doc);
                    Object source = doc.getMetadata().get("source");
                    String sourceStr = source != null ? source.toString() : "Unknown";

                    return String.format("Source: %s\nContent: %s", sourceStr, content);
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String extractDocumentContent(Document doc) {
        // Try different methods to get document content based on API version
        try {
            // Method 1: Try getContent()
            java.lang.reflect.Method getContentMethod = doc.getClass().getMethod("getContent");
            Object content = getContentMethod.invoke(doc);
            return content != null ? content.toString() : "No content";
        } catch (Exception e1) {
            try {
                // Method 2: Try getText()
                java.lang.reflect.Method getTextMethod = doc.getClass().getMethod("getText");
                Object content = getTextMethod.invoke(doc);
                return content != null ? content.toString() : "No content";
            } catch (Exception e2) {
                try {
                    // Method 3: Try accessing field directly
                    java.lang.reflect.Field contentField = doc.getClass().getDeclaredField("content");
                    contentField.setAccessible(true);
                    Object content = contentField.get(doc);
                    return content != null ? content.toString() : "No content";
                } catch (Exception e3) {
                    // Method 4: Last resort - toString but limit length
                    String docStr = doc.toString();
                    return docStr.length() > 200 ? docStr.substring(0, 200) + "..." : docStr;
                }
            }
        }
    }

    private String extractContent(Object output) {
        // Try different methods to extract content based on API version
        try {
            return output.getClass().getMethod("getContent").invoke(output).toString();
        } catch (Exception e1) {
            try {
                return output.getClass().getMethod("getText").invoke(output).toString();
            } catch (Exception e2) {
                return output.toString();
            }
        }
    }

    private Mono<String> processFinalResponse(String conversationId, String userInput, String rawResponse) {
        return Mono.fromCallable(() -> {
            // Clean up the response
            String cleanedResponse = rawResponse.trim();

            if (cleanedResponse.isBlank()) {
                cleanedResponse = "I apologize, but I couldn't generate a proper response. Please try again.";
            }

            // Save messages to memory
            try {
                chatMemory.add(conversationId, new UserMessage(userInput));
                chatMemory.add(conversationId, new AssistantMessage(cleanedResponse));
            } catch (Exception e) {
                log.warn("Failed to save messages to chat memory", e);
            }

            return cleanedResponse;
        });
    }
}