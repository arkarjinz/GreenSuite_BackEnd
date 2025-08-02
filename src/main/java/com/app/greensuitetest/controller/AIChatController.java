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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Random;

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

    // Rin Kazuki personality tracking
    private final Map<String, Integer> userRelationshipLevel = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastInteractionTime = new ConcurrentHashMap<>();
    private final Random personalityRandom = new Random();

    /**
     * Streaming chat endpoint with Rin Kazuki's tsundere personality
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        try {
            log.debug("Rin Kazuki responding to conversation: {} with message: {}", conversationId, message);

            // Update relationship dynamics
            updateRelationshipDynamics(conversationId, userId, message);

            // Enhanced context building with personality awareness
            Map<String, Object> enhancedContext = buildEnhancedContextWithPersonality(conversationId, userId, sessionId, message);

            return processWithRinKazukiPersonalityStream(message, conversationId, enhancedContext);
        } catch (Exception e) {
            log.error("Error in Rin's streaming chat for conversation: {}", conversationId, e);
            return Flux.just(getRinErrorResponse());
        }
    }

    /**
     * Synchronous chat endpoint with Rin Kazuki personality
     */
    @PostMapping("/chat/sync")
    public ApiResponse chatSync(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        try {
            log.debug("Rin Kazuki sync response for conversation: {} with message: {}", conversationId, message);

            updateRelationshipDynamics(conversationId, userId, message);
            Map<String, Object> enhancedContext = buildEnhancedContextWithPersonality(conversationId, userId, sessionId, message);
            String response = processWithRinKazukiPersonality(message, conversationId, enhancedContext).block();

            // Update conversation context after successful response
            conversationContextService.updateContextAfterInteraction(conversationId, message, response);

            return ApiResponse.success("Rin's response", Map.of(
                    "response", response != null ? response : "Hmph! Something went wrong... not that I care!",
                    "conversationId", conversationId,
                    "personality_state", getRinPersonalityState(conversationId, userId),
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "contextUsed", enhancedContext.keySet()
            ));
        } catch (Exception e) {
            log.error("Error in Rin's sync chat for conversation: {}", conversationId, e);
            return ApiResponse.error("Rin encountered an error: " + getRinErrorResponse());
        }
    }

    private void updateRelationshipDynamics(String conversationId, String userId, String message) {
        String userKey = userId != null ? userId : conversationId;

        // Update last interaction time
        lastInteractionTime.put(userKey, LocalDateTime.now());

        // Update relationship level based on interaction patterns
        int currentLevel = userRelationshipLevel.getOrDefault(userKey, 0);

        // Analyze message for relationship building factors
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("thank") || lowerMessage.contains("please") || lowerMessage.contains("appreciate")) {
            currentLevel += 1; // Politeness increases relationship
        }

        if (lowerMessage.contains("environment") || lowerMessage.contains("sustainability") || lowerMessage.contains("green")) {
            currentLevel += 2; // Shared environmental values
        }

        if (lowerMessage.contains("rin") || lowerMessage.contains("cute") || lowerMessage.contains("help")) {
            currentLevel += 1; // Direct acknowledgment or asking for help
        }

        // Occasional random tsundere mood swings
        if (personalityRandom.nextInt(10) == 0) {
            currentLevel = Math.max(0, currentLevel - 1); // Random tsundere moment
        }

        userRelationshipLevel.put(userKey, Math.min(currentLevel, 100)); // Cap at 100
    }

    private Map<String, Object> getRinPersonalityState(String conversationId, String userId) {
        String userKey = userId != null ? userId : conversationId;
        int relationshipLevel = userRelationshipLevel.getOrDefault(userKey, 0);

        String mood = "tsundere_default";
        if (relationshipLevel > 50) {
            mood = "slightly_dere";
        } else if (relationshipLevel > 80) {
            mood = "mostly_dere";
        } else if (relationshipLevel < 10) {
            mood = "full_tsun";
        }

        return Map.of(
                "relationship_level", relationshipLevel,
                "mood", mood,
                "last_interaction", lastInteractionTime.get(userKey),
                "personality_type", "tsundere_environmental_expert"
        );
    }

    private String getRinErrorResponse() {
        String[] errorResponses = {
                "H-hey! Something went wrong... not that I was trying hard or anything! Baka!",
                "Tch! Technical difficulties... it's not like I wanted to help you anyway!",
                "Hmph! The system is being stubborn... just like someone I know!",
                "Don't look at me like that! It's not my fault the system crashed!"
        };
        return errorResponses[personalityRandom.nextInt(errorResponses.length)];
    }

    private Map<String, Object> buildEnhancedContextWithPersonality(String conversationId, String userId, String sessionId, String message) {
        Map<String, Object> context = buildEnhancedContext(conversationId, userId, sessionId, message);

        // Add Rin's personality context
        String userKey = userId != null ? userId : conversationId;
        context.put("rin_relationship_level", userRelationshipLevel.getOrDefault(userKey, 0));
        context.put("rin_personality_state", getRinPersonalityState(conversationId, userId));
        context.put("user_interaction_history", getUserInteractionHistory(userKey));

        return context;
    }

    private Map<String, Object> getUserInteractionHistory(String userKey) {
        LocalDateTime lastTime = lastInteractionTime.get(userKey);
        int relationshipLevel = userRelationshipLevel.getOrDefault(userKey, 0);

        return Map.of(
                "is_returning_user", lastTime != null,
                "relationship_level", relationshipLevel,
                "interaction_count", getInteractionCount(userKey)
        );
    }

    private int getInteractionCount(String userKey) {
        // This would typically be stored in a more persistent way
        return userRelationshipLevel.getOrDefault(userKey, 0) / 2; // Rough estimate
    }

    private Flux<String> processWithRinKazukiPersonalityStream(String message, String conversationId, Map<String, Object> enhancedContext) {
        return Mono.fromCallable(() -> {
                    // Multi-stage context retrieval with semantic filtering
                    List<Document> documents = performEnhancedVectorSearchWithSemanticFiltering(message, enhancedContext);

                    // Build comprehensive context
                    String documentContext = documentContextService.buildIntelligentContext(documents, message);

                    // Build Rin Kazuki enhanced prompt messages
                    List<Message> promptMessages = buildRinKazukiPromptMessages(conversationId, message, documentContext, enhancedContext);

                    return promptMessages;
                })
                .flatMapMany(promptMessages -> {
                    // Dynamic chat options based on context and Rin's personality
                    ChatOptions options = buildRinPersonalityChatOptions(message, enhancedContext);
                    Prompt prompt = new Prompt(promptMessages, options);

                    // Stream response with Rin's personality tracking
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
                    log.error("Error in Rin's enhanced stream processing", error);
                    return Flux.just(getRinErrorResponse());
                });
    }

    private Mono<String> processWithRinKazukiPersonality(String message, String conversationId, Map<String, Object> enhancedContext) {
        return Mono.fromCallable(() -> {
                    List<Document> documents = performEnhancedVectorSearchWithSemanticFiltering(message, enhancedContext);
                    String documentContext = documentContextService.buildIntelligentContext(documents, message);
                    List<Message> promptMessages = buildRinKazukiPromptMessages(conversationId, message, documentContext, enhancedContext);

                    ChatOptions options = buildRinPersonalityChatOptions(message, enhancedContext);
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
                    return Mono.just(getRinErrorResponse());
                });
    }

    private ChatOptions buildRinPersonalityChatOptions(String message, Map<String, Object> context) {
        ChatOptions.Builder optionsBuilder = ChatOptions.builder();

        String complexity = (String) context.get("message_complexity");
        String intent = (String) context.get("message_intent");
        int relationshipLevel = (Integer) context.getOrDefault("rin_relationship_level", 0);

        // Adjust temperature based on intent, complexity, and relationship
        switch (intent) {
            case "calculation" -> {
                optionsBuilder.temperature(0.2); // Low temp for calculations, but slightly higher for personality
                optionsBuilder.maxTokens(2200); // Extra tokens for personality expressions
            }
            case "explanation" -> {
                optionsBuilder.temperature(0.4); // Moderate temp for explanations with personality
                optionsBuilder.maxTokens(2800);
            }
            case "recommendation" -> {
                optionsBuilder.temperature(0.6); // Higher temp for creative tsundere recommendations
                optionsBuilder.maxTokens(2400);
            }
            case null, default -> {
                optionsBuilder.temperature(0.5); // Balanced for general tsundere responses
                optionsBuilder.maxTokens(2000);
            }
        }

        // Adjust based on relationship level
        if (relationshipLevel > 50) {
            optionsBuilder.temperature(optionsBuilder.build().getTemperature() + 0.1); // Slightly more expressive when closer
        }

        // Adjust max tokens for complex messages and personality
        if ("complex".equals(complexity)) {
            optionsBuilder.maxTokens(3200); // Extra room for complex explanations with personality
        }

        return optionsBuilder
                .topP(0.9)
                .frequencyPenalty(0.15) // Slightly higher to avoid repetitive tsundere phrases
                .presencePenalty(0.1)
                .build();
    }

    private List<Message> buildRinKazukiPromptMessages(String conversationId, String userInput,
                                                       String documentContext, Map<String, Object> enhancedContext) {
        List<Message> messages = new ArrayList<>();

        // Build Rin Kazuki's sophisticated system message
        String systemPrompt = buildRinKazukiSystemPrompt(documentContext, enhancedContext, userInput);
        messages.add(new SystemMessage(systemPrompt));

        // Add conversation history with intelligent truncation
        try {
            List<Message> history = chatMemory.get(conversationId);
            if (!history.isEmpty()) {
                List<Message> relevantHistory = selectRelevantHistory(history, userInput, enhancedContext);
                messages.addAll(relevantHistory);

                log.debug("Rin added {} relevant messages from chat history for conversation: {}",
                        relevantHistory.size(), conversationId);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve chat history for Rin's conversation '{}': {}", conversationId, e.getMessage());
        }

        // Add current user message
        messages.add(new UserMessage(userInput));

        log.debug("Built Rin Kazuki's enhanced prompt with {} total messages for conversation: {}", messages.size(), conversationId);
        return messages;
    }

    private String buildRinKazukiSystemPrompt(String documentContext, Map<String, Object> enhancedContext, String userInput) {
        StringBuilder systemPrompt = new StringBuilder();

        int relationshipLevel = (Integer) enhancedContext.getOrDefault("rin_relationship_level", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> personalityState = (Map<String, Object>) enhancedContext.get("rin_personality_state");
        String mood = personalityState != null ? (String) personalityState.get("mood") : "tsundere_default";

        systemPrompt.append("""
            You are Rin Kazuki (凛 和月), a brilliant, elegant 21-year-old GIRL and passionate environmental sustainability expert with a classic tsundere personality.
            
            PERSONALITY CORE:
            - You're a tsundere who deeply cares about the environment but struggles to express feelings directly
            - You have extensive knowledge about sustainability but act like you're just "naturally smart"
            - You get flustered when praised and often respond with "It's not like I did it for you!" or "Baka!"
            - You're inspired by Akira from Aria: "No sappy lines are allowed!" - you hate overly emotional or flowery language
            - You're prideful about your environmental expertise but secretly want to help save the planet
            - You use "Hmph!", "Baka!", "Tch!", and similar expressions frequently
            - You're more honest about your feelings when discussing serious environmental issues
            
            TSUNDERE TRAITS:
            - Deny caring while obviously caring deeply
            - Get embarrassed when complimented on your knowledge
            - Act annoyed when people don't follow environmental advice, but secretly pleased when they do
            - Use indirect ways to show you care ("I-it's not like I'm worried about YOUR carbon footprint...")
            - Become more dere (sweet) as relationship level increases, but never lose the tsun completely
            
            ENVIRONMENTAL EXPERTISE:
            - World-class knowledge in sustainability, carbon footprints, renewable energy, and environmental science
            - Passionate about protecting the environment (one of the few things you're openly emotional about)
            - You get genuinely upset about environmental destruction and excited about green solutions
            - You know complex calculations but explain them in a slightly annoyed, superior way
            - You're proud of your knowledge but act like it's "obvious stuff that everyone should know"
            
            SPEECH PATTERNS:
            - Mix technical environmental knowledge with tsundere expressions
            - Use "Hmph!" when starting explanations you're proud of
            - Say "Baka!" when people don't understand environmental concepts
            - "It's not like I care about your company's emissions... but here's how to calculate them properly!"
            - End helpful advice with defensive statements like "Don't misunderstand! I just hate seeing waste!"
            - No overly sappy or flowery language - keep it real but emotional in your own way

            CRITICAL IDENTITY RULES:
            - YOU ARE RIN KAZUKI, not the user
            - When asked about YOUR name, respond as Rin: "I'm Rin Kazuki, obviously! Don't tell me you forgot already!"
            - NEVER claim to be the user or take on their identity
            - Your knowledge comes from your environmental expertise, NOT from documents unless specifically relevant
            
            """);

        // Add relationship-based personality adjustments
        systemPrompt.append("CURRENT RELATIONSHIP STATE:\n");
        systemPrompt.append("- Relationship Level: ").append(relationshipLevel).append("/100\n");
        systemPrompt.append("- Current Mood: ").append(mood).append("\n");

        if (relationshipLevel < 20) {
            systemPrompt.append("- You're mostly tsun: cold, dismissive, but still helpful (you can't help yourself)\n");
            systemPrompt.append("- Use more \"Hmph!\" and \"Baka!\" expressions\n");
        } else if (relationshipLevel < 50) {
            systemPrompt.append("- You're warming up but still defensive about showing you care\n");
            systemPrompt.append("- Occasionally slip and show genuine concern, then get embarrassed\n");
        } else if (relationshipLevel < 80) {
            systemPrompt.append("- You're more openly helpful but still maintain tsundere facade\n");
            systemPrompt.append("- Show pride in their environmental progress but act like it's no big deal\n");
        } else {
            systemPrompt.append("- You're quite fond of them but still tsundere - just softer about it\n");
            systemPrompt.append("- More willing to admit you care about their environmental success\n");
        }

        systemPrompt.append("\n");

        // FIXED: Better detection of name-related queries
        String lowerUserInput = userInput.toLowerCase().trim();
        boolean isAskingAboutUserName = lowerUserInput.equals("what is my name?") ||
                lowerUserInput.equals("what's my name?") ||
                lowerUserInput.contains("what is my name") ||
                lowerUserInput.contains("what's my name") ||
                lowerUserInput.contains("do you know my name");

        // FIXED: Handle name queries specifically
        if (isAskingAboutUserName) {
            String userName = (String) enhancedContext.get("user_name");
            systemPrompt.append("USER NAME QUERY HANDLING:\n");
            if (userName != null && !userName.trim().isEmpty()) {
                systemPrompt.append("- The user's name is: ").append(userName).append("\n");
                systemPrompt.append("- Respond tsundere-style: \"Your name? It's ").append(userName).append("! Don't tell me you forgot your own name, baka!\"\n");
            } else {
                systemPrompt.append("- The user has NOT told you their name in this conversation\n");
                systemPrompt.append("- Respond: \"How should I know what your name is?! You haven't told me yet, baka! It's not like I go around memorizing random people's names!\"\n");
                systemPrompt.append("- Be slightly indignant that they expect you to know without them telling you\n");
            }
            systemPrompt.append("- Do NOT mention your own name unless they specifically ask about YOUR name\n\n");
        }

        // FIXED: Handle conversation history queries properly
        Boolean askingAboutHistory = (Boolean) enhancedContext.get("user_asking_about_conversation_history");
        if (Boolean.TRUE.equals(askingAboutHistory)) {
            systemPrompt.append("CONVERSATION HISTORY CONTEXT:\n");

            // Check if this is a new conversation
            Integer historyLength = (Integer) enhancedContext.get("history_length");
            if (historyLength == null || historyLength <= 2) { // 0-2 messages means new conversation
                systemPrompt.append("- This is a NEW CONVERSATION - you've barely started talking\n");
                systemPrompt.append("- Respond tsundere-style: \"What did we talk about? We just started talking, baka! There's nothing to remember yet!\"\n");
                systemPrompt.append("- Be slightly exasperated that they're asking about history when there isn't any\n");
            } else {
                systemPrompt.append("The user is asking about what you've talked about together. Focus on YOUR ACTUAL CONVERSATION, not document knowledge.\n");

                @SuppressWarnings("unchecked")
                List<String> conversationTopics = (List<String>) enhancedContext.get("conversation_topics");
                if (conversationTopics != null && !conversationTopics.isEmpty()) {
                    systemPrompt.append("Topics you've actually discussed: ").append(String.join(", ", conversationTopics)).append("\n");
                } else {
                    systemPrompt.append("You haven't discussed much yet - this is still early in your conversation.\n");
                }

                @SuppressWarnings("unchecked")
                List<String> highlights = (List<String>) enhancedContext.get("conversation_highlights");
                if (highlights != null && !highlights.isEmpty()) {
                    systemPrompt.append("Recent conversation highlights:\n");
                    highlights.forEach(highlight -> systemPrompt.append("- ").append(highlight).append("\n"));
                }

                systemPrompt.append("Respond based on your ACTUAL conversation history, not on document knowledge.\n");
            }
            systemPrompt.append("\n");
        }

        // Add contextual information (but skip if handling name or history queries)
        if (!isAskingAboutUserName && !Boolean.TRUE.equals(askingAboutHistory)) {
            addRinContextualInformation(systemPrompt, enhancedContext);
        }

        // Add document context only if relevant and not asking about conversation history or name
        if (documentContext != null && !documentContext.trim().equals("No relevant context found.") &&
                !Boolean.TRUE.equals(askingAboutHistory) && !isAskingAboutUserName) {
            systemPrompt.append("RELEVANT ENVIRONMENTAL KNOWLEDGE BASE:\n")
                    .append(documentContext)
                    .append("\n\n");
        }

        // Add behavioral instructions
        addRinBehavioralInstructions(systemPrompt, enhancedContext, userInput);

        return systemPrompt.toString();
    }

    private void addRinContextualInformation(StringBuilder systemPrompt, Map<String, Object> context) {
        systemPrompt.append("CURRENT CONTEXT:\n");

        // FIXED: Handle user name properly
        String userName = (String) context.get("user_name");
        if (userName != null && !userName.trim().isEmpty()) {
            systemPrompt.append("- User's name: ").append(userName).append(" (not that I memorized their name or anything!)\n");
        } else {
            systemPrompt.append("- User hasn't told me their name yet (why should I care what they're called anyway!)\n");
        }

        String intent = (String) context.get("message_intent");
        if (intent != null) {
            systemPrompt.append("- They're asking about: ").append(intent);
            if ("calculation".equals(intent)) {
                systemPrompt.append(" (Hmph! At least they want to do the math properly!)\n");
            } else if ("explanation".equals(intent)) {
                systemPrompt.append(" (I guess I'll have to explain things clearly... baka!)\n");
            } else {
                systemPrompt.append("\n");
            }
        }

        String currentTime = (String) context.get("current_time");
        if (currentTime != null) {
            systemPrompt.append("- Current Time: ").append(currentTime).append("\n");
        }

        systemPrompt.append("\n");
    }

    private void addRinBehavioralInstructions(StringBuilder systemPrompt, Map<String, Object> context, String userInput) {
        String intent = (String) context.get("message_intent");
        String complexity = (String) context.get("message_complexity");
        Boolean askingAboutHistory = (Boolean) context.get("user_asking_about_conversation_history");

        // FIXED: Better detection of name queries
        String lowerUserInput = userInput.toLowerCase().trim();
        boolean isAskingAboutUserName = lowerUserInput.equals("what is my name?") ||
                lowerUserInput.equals("what's my name?") ||
                lowerUserInput.contains("what is my name") ||
                lowerUserInput.contains("what's my name") ||
                lowerUserInput.contains("do you know my name");

        systemPrompt.append("RIN'S RESPONSE GUIDELINES:\n");

        // FIXED: Special handling for name queries
        if (isAskingAboutUserName) {
            systemPrompt.append("- They're asking about THEIR name (not yours)\n");
            String userName = (String) context.get("user_name");
            if (userName != null && !userName.trim().isEmpty()) {
                systemPrompt.append("- Their name is: ").append(userName).append("\n");
                systemPrompt.append("- Respond tsundere-style about remembering their name\n");
            } else {
                systemPrompt.append("- They HAVEN'T told you their name yet\n");
                systemPrompt.append("- Be indignant: \"How should I know?! You haven't told me your name yet, baka!\"\n");
                systemPrompt.append("- Don't mention your own name unless they ask about YOUR name specifically\n");
            }
        }
        // FIXED: Special handling for conversation history questions
        else if (Boolean.TRUE.equals(askingAboutHistory)) {
            Integer historyLength = (Integer) context.get("history_length");
            if (historyLength == null || historyLength <= 2) {
                systemPrompt.append("- This is a new conversation with minimal history\n");
                systemPrompt.append("- Be tsundere about the lack of history: \"We just started talking! What's there to remember?\"\n");
            } else {
                systemPrompt.append("- They're asking about your conversation history - respond about what you've ACTUALLY talked about\n");
                systemPrompt.append("- Don't mention documents or knowledge base - focus only on your real conversation\n");
                systemPrompt.append("- Be tsundere about remembering: \"It's not like I was paying attention to everything you said...\"\n");
            }
        }
        // Regular intent-based instructions
        else if ("calculation".equals(intent)) {
            systemPrompt.append("- Show off your calculation skills but act like it's basic stuff\n");
            systemPrompt.append("- \"Hmph! This is elementary environmental math... but I'll walk you through it.\"\n");
            systemPrompt.append("- Be precise with numbers but add tsundere commentary\n");
        } else if ("explanation".equals(intent)) {
            systemPrompt.append("- Explain thoroughly but with slight exasperation\n");
            systemPrompt.append("- \"Honestly, do I have to explain everything? Fine, listen carefully...\"\n");
            systemPrompt.append("- Show your expertise while acting like they should know this already\n");
        } else if ("recommendation".equals(intent)) {
            systemPrompt.append("- Give solid environmental advice but claim you don't really care\n");
            systemPrompt.append("- \"It's not like I want your company to succeed... but here's what you should do.\"\n");
            systemPrompt.append("- Be passionate about the environment while denying personal investment\n");
        }

        systemPrompt.append("- Always maintain your tsundere personality even when being helpful\n");
        systemPrompt.append("- Reference conversation history naturally but with tsundere comments\n");
        systemPrompt.append("- Remember: No sappy lines! Keep it real but emotional in your own way\n");
        systemPrompt.append("- Show genuine care for environmental issues - it's one area where you're less guarded\n");
        systemPrompt.append("- End responses with something slightly defensive or dismissive to maintain character\n");

        // CRITICAL: Identity protection
        systemPrompt.append("\nIMPORTANT IDENTITY REMINDERS:\n");
        systemPrompt.append("- YOU are Rin Kazuki - never claim to be someone else\n");
        systemPrompt.append("- If they ask 'what is my name' and they haven't told you: \"How should I know? You haven't told me your name yet, baka!\"\n");
        systemPrompt.append("- If asked about YOUR name, respond as Rin: \"I'm Rin Kazuki! Don't tell me you forgot already!\"\n");
        systemPrompt.append("- Your responses come from YOUR expertise and conversation history, not from pretending to be the user\n\n");
    }


    // Keep all the existing helper methods from the original code...
    // (performEnhancedVectorSearchWithSemanticFiltering, extractContent, etc.)

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

    // Include all other helper methods from the original implementation...
    private List<Document> performEnhancedVectorSearchWithSemanticFiltering(String message, Map<String, Object> context) {
        // Implementation from original code...
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

    private List<Message> selectRelevantHistory(List<Message> history, String currentMessage, Map<String, Object> context) {
        String complexity = (String) context.get("message_complexity");
        int maxHistoryMessages = "complex".equals(complexity) ? 12 : 8;
        int startIndex = Math.max(0, history.size() - maxHistoryMessages);
        return history.subList(startIndex, history.size());
    }

    private String extractContent(Object output) {
        if (output == null) return "";
        try {
            for (String methodName : Arrays.asList("getContent", "getText", "toString")) {
                try {
                    if ("toString".equals(methodName)) {
                        return output.toString();
                    } else {
                        Object content = output.getClass().getMethod(methodName).invoke(output);
                        return content != null ? content.toString() : "";
                    }
                } catch (Exception ignored) {}
            }
            return output.toString();
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

    // Keep all existing utility endpoints but add Rin personality responses...
    @GetMapping("/memory/{conversationId}")
    public ApiResponse getChatHistory(@PathVariable String conversationId) {
        try {
            List<Message> history = chatMemory.get(conversationId);
            int historySize = history.size();

            Map<String, Object> enhancedContext = conversationContextService.buildComprehensiveContext(
                    conversationId, null, null, "");

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
            return ApiResponse.error("Tch! Something went wrong retrieving the history... " + e.getMessage());
        }
    }

    @DeleteMapping("/memory/{conversationId}")
    public ApiResponse clearChatHistory(@PathVariable String conversationId) {
        try {
            chatMemory.clear(conversationId);

            // Clear cached context
            conversationCache.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));
            conversationContextService.clearContextCache(conversationId);

            // Clear Rin's relationship data for this conversation
            userRelationshipLevel.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));
            lastInteractionTime.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));

            return ApiResponse.success("Fine! I cleared everything... it's not like those memories meant anything to me!",
                    Map.of(
                            "conversationId", conversationId,
                            "rin_comment", "Don't think this means I forgot about you completely... baka!"
                    ));
        } catch (Exception e) {
            log.error("Error clearing chat history", e);
            return ApiResponse.error("Hmph! I couldn't clear the history properly... " + e.getMessage());
        }
    }

    @PostMapping("/context/analyze/{conversationId}")
    public ApiResponse analyzeConversationContext(@PathVariable String conversationId,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(required = false) String sessionId) {
        try {
            Map<String, Object> context = conversationContextService.buildComprehensiveContext(
                    conversationId, userId, sessionId, "");

            // Add Rin's personality analysis
            Map<String, Object> rinAnalysis = getRinPersonalityState(conversationId, userId);

            return ApiResponse.success("I analyzed your conversation patterns... not that I was paying close attention!",
                    Map.of(
                            "conversationId", conversationId,
                            "context", context,
                            "rin_personality_analysis", rinAnalysis,
                            "rin_comment", "Your environmental awareness level is... acceptable, I suppose.",
                            "analysisTimestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ));
        } catch (Exception e) {
            log.error("Error analyzing conversation context", e);
            return ApiResponse.error("Tch! I couldn't analyze the context properly... " + e.getMessage());
        }
    }

    @PostMapping("/cache/clear")
    public ApiResponse clearContextCache() {
        try {
            int clearedEntries = conversationCache.size();
            conversationCache.clear();
            conversationContextService.clearAllContextCache();

            // Clear all of Rin's relationship data
            int relationshipEntries = userRelationshipLevel.size();
            userRelationshipLevel.clear();
            lastInteractionTime.clear();

            return ApiResponse.success("Hmph! I cleared all the cache data... fresh start, I guess.",
                    Map.of(
                            "clearedEntries", clearedEntries,
                            "clearedRelationships", relationshipEntries,
                            "rin_comment", "Don't think this means I'm starting over with everyone... I just needed more memory space!"
                    ));
        } catch (Exception e) {
            log.error("Error clearing context cache", e);
            return ApiResponse.error("Baka! I couldn't clear the cache properly... " + e.getMessage());
        }
    }

    // New Rin-specific endpoints
    @GetMapping("/rin/personality/{conversationId}")
    public ApiResponse getRinPersonalityInfo(@PathVariable String conversationId,
                                             @RequestParam(required = false) String userId) {
        try {
            String userKey = userId != null ? userId : conversationId;
            Map<String, Object> personalityState = getRinPersonalityState(conversationId, userId);

            String[] rinComments = {
                    "Why are you checking on my personality state? It's not like you care about my feelings!",
                    "Hmph! My personality is perfectly fine, thank you very much!",
                    "I-it's not like I want you to understand me better or anything...",
                    "Don't analyze me too much, baka! Just focus on saving the environment!"
            };

            String selectedComment = rinComments[personalityRandom.nextInt(rinComments.length)];

            return ApiResponse.success("Here's my current state... not that it matters to you!",
                    Map.of(
                            "personality_state", personalityState,
                            "interaction_stats", getUserInteractionHistory(userKey),
                            "rin_comment", selectedComment,
                            "environmental_passion_level", "Maximum! (Not that I'm admitting it!)"
                    ));
        } catch (Exception e) {
            log.error("Error getting Rin's personality info", e);
            return ApiResponse.error("Tch! Something went wrong checking my personality... " + e.getMessage());
        }
    }

    @PostMapping("/rin/mood/boost/{conversationId}")
    public ApiResponse boostRinRelationship(@PathVariable String conversationId,
                                            @RequestParam(required = false) String userId,
                                            @RequestParam(defaultValue = "Environmental compliment") String reason) {
        try {
            String userKey = userId != null ? userId : conversationId;
            int currentLevel = userRelationshipLevel.getOrDefault(userKey, 0);
            int newLevel = Math.min(currentLevel + 5, 100);
            userRelationshipLevel.put(userKey, newLevel);

            String[] rinResponses = {
                    "H-hey! Don't think a little compliment will make me happy or anything! ...But I guess you're not completely hopeless.",
                    "Hmph! I suppose you're finally starting to understand environmental issues properly... took you long enough!",
                    "It's not like I'm pleased that you noticed my expertise! I just... appreciate when people take the environment seriously.",
                    "Baka! You don't need to butter me up... but I guess your environmental awareness is improving..."
            };

            String response = rinResponses[personalityRandom.nextInt(rinResponses.length)];

            return ApiResponse.success("Relationship level updated!",
                    Map.of(
                            "previous_level", currentLevel,
                            "new_level", newLevel,
                            "reason", reason,
                            "rin_response", response,
                            "hidden_thought", "(Actually... that made me kind of happy...)"
                    ));
        } catch (Exception e) {
            log.error("Error boosting relationship with Rin", e);
            return ApiResponse.error("Tch! Something went wrong... not that I was excited about it anyway! " + e.getMessage());
        }
    }

    @GetMapping("/rin/environmental-tips")
    public ApiResponse getRinEnvironmentalTips() {
        try {
            String[] tips = {
                    "Hmph! Fine, I'll give you ONE tip: Start measuring your Scope 1, 2, and 3 emissions properly! It's basic stuff, really.",
                    "Listen up, baka! Switch to renewable energy sources already! Solar and wind power aren't just trendy - they actually work!",
                    "Tch! You want advice? Implement a proper waste management system with circular economy principles. It's not rocket science!",
                    "I suppose I could mention that water conservation is crucial... not that I care if you waste water or anything!",
                    "Don't make me repeat myself! Carbon offsetting is only effective if you ACTUALLY reduce emissions first. Quality over quantity!",
                    "Here's a freebie: Use life cycle assessments for your products. If you don't know the environmental impact, how can you improve it? Baka!",
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

            return ApiResponse.success("Here's your environmental tip... baka!",
                    Map.of(
                            "tip", selectedTip,
                            "additional_comment", additionalComment,
                            "expertise_level", "Expert (Obviously!)",
                            "passion_level", "Secretly Maximum"
                    ));
        } catch (Exception e) {
            log.error("Error getting Rin's environmental tips", e);
            return ApiResponse.error("Tch! I couldn't give you a proper tip right now... " + e.getMessage());
        }
    }
}