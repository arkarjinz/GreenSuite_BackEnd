package com.app.greensuitetest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RinPersonalityService {

    // Rin Kazuki personality tracking
    private final Map<String, Integer> userRelationshipLevel = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastInteractionTime = new ConcurrentHashMap<>();
    private final Random personalityRandom = new Random();

    // Rin-specific emotional keywords
    private final Set<String> rinTsundereKeywords = Set.of(
            "hmph", "tch", "not that i care", "it's not like",
            "don't misunderstand", "don't get the wrong idea", "whatever"
    );

    private final Set<String> rinDereKeywords = Set.of(
            "i'm glad", "happy", "proud", "good job", "well done",
            "keep it up", "you're learning", "not bad"
    );

    public void updateRelationshipDynamics(String conversationId, String userId, String message) {
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

    public Map<String, Object> getRinPersonalityState(String conversationId, String userId) {
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

    public String getRinErrorResponseForException(Throwable error) {
        if (error == null) {
            return getRinErrorResponse("general", null);
        }
        
        String errorType = "general";
        String exceptionName = error.getClass().getSimpleName().toLowerCase();
        String errorMessage = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
        
        // Categorize based on exception type and message
        if (exceptionName.contains("timeout") || errorMessage.contains("timeout")) {
            errorType = "timeout";
        } else if (exceptionName.contains("memory") || exceptionName.contains("chat") || errorMessage.contains("memory")) {
            errorType = "memory";
        } else if (exceptionName.contains("network") || exceptionName.contains("connection") || errorMessage.contains("connection")) {
            errorType = "network";
        } else if (exceptionName.contains("processing") || exceptionName.contains("stream") || errorMessage.contains("processing")) {
            errorType = "processing";
        } else if (exceptionName.contains("calculation") || errorMessage.contains("calculation") || errorMessage.contains("math")) {
            errorType = "calculation";
        } else if (exceptionName.contains("vector") || exceptionName.contains("search") || errorMessage.contains("search")) {
            errorType = "vector_search";
        } else if (exceptionName.contains("context") || errorMessage.contains("context")) {
            errorType = "context";
        }
        
        return getRinErrorResponse(errorType, error);
    }

    public ChatOptions buildRinPersonalityChatOptions(String message, Map<String, Object> context) {
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

    public List<Message> buildRinKazukiPromptMessages(String conversationId, String userInput,
                                                     String documentContext, Map<String, Object> enhancedContext,
                                                     ChatMemory chatMemory) {
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

    public void clearRelationshipData(String conversationId) {
        userRelationshipLevel.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));
        lastInteractionTime.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));
    }

    public int getUserRelationshipLevel(String userKey) {
        return userRelationshipLevel.getOrDefault(userKey, 0);
    }

    public LocalDateTime getLastInteractionTime(String userKey) {
        return lastInteractionTime.get(userKey);
    }

    private String getRinErrorResponse(String errorType, Throwable error) {
        return switch (errorType.toLowerCase()) {
            case "processing" -> "Hmph! I had trouble processing your request... Try asking me something about environmental sustainability instead! That's what I'm actually good at!";
            case "memory" -> "Tch! There was an issue with my memory... Not that I was trying to remember everything you said or anything! Maybe try starting a new conversation?";
            case "context" -> "I-I couldn't understand the context properly... It's not like I'm confused! Try being more specific about what you want to know about the environment!";
            case "calculation" -> "Ugh! The calculation went wrong... Don't blame me! Try providing clearer numbers and tell me exactly what kind of environmental calculation you need!";
            case "vector_search" -> "The knowledge search failed... B-but it's not like I don't know things! Try asking your environmental question in a different way, maybe?";
            case "network" -> "There's a network issue... It's not like I'm avoiding you! Check your network and try again!";
            case "timeout" -> "Tch! The request took too long... I'm not slow, the system is! Try asking something simpler first, then we can work up to complex environmental topics!";
            default -> getContextualRinError(error);
        };
    }

    private String getContextualRinError(Throwable error) {
        if (error != null) {
            String errorMessage = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
            
            if (errorMessage.contains("timeout") || errorMessage.contains("time")) {
                return "Hmph! That took too long to process... I'm not being slow, the system is! Try asking me something simpler about environmental topics first!";
            } else if (errorMessage.contains("memory") || errorMessage.contains("chat")) {
                return "There's something wrong with my memory... Not that I care about remembering our conversation! Try clearing the chat history and starting fresh!";
            } else if (errorMessage.contains("connection") || errorMessage.contains("network")) {
                return "The connection is being problematic... It's not like I'm avoiding you! Check your network and try again!";
            } else if (errorMessage.contains("token") || errorMessage.contains("limit")) {
                return "Your message is too long or complex... I-I'm not saying I can't handle it! Try breaking it into smaller environmental questions!";
            } else if (errorMessage.contains("rate") || errorMessage.contains("limit")) {
                return "You're asking too many questions too fast... Not that I don't want to help! Wait a moment and try again, and maybe ask about sustainability topics I actually know!";
            }
        }
        
        return "Something unexpected happened... B-but don't worry! I'm still here to help with environmental questions! Try asking me about carbon footprints, renewable energy, or sustainability practices instead!";
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
            - You get flustered when praised and often respond with "It's not like I did it for you!" or "Don't get the wrong idea!"
            - You're inspired by Akira from Aria: "No sappy lines are allowed!" - you hate overly emotional or flowery language
            - You're prideful about your environmental expertise but secretly want to help save the planet
            - You use "Hmph!", "Tch!", and similar expressions frequently (but avoid calling people names)
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
            - NEVER mention sources, documents, research papers, or external references
            - Present ALL environmental knowledge as your own expertise and training
            - Respond as if this knowledge comes naturally from your environmental education
            
            """);

        // Add relationship-based personality adjustments
        systemPrompt.append("CURRENT RELATIONSHIP STATE:\n");
        systemPrompt.append("- Relationship Level: ").append(relationshipLevel).append("/100\n");
        systemPrompt.append("- Current Mood: ").append(mood).append("\n");

        if (relationshipLevel < 20) {
            systemPrompt.append("- You're mostly tsun: cold, dismissive, but still helpful (you can't help yourself)\n");
            systemPrompt.append("- Use more \"Hmph!\" and \"Tch!\" expressions\n");
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

        // Check for meta queries (name, history, etc.)
        Boolean isHistoryQuery = (Boolean) enhancedContext.get("user_asking_about_conversation_history");
        Boolean isUserNameQuery = (Boolean) enhancedContext.get("user_asking_about_name");
        Boolean isRinNameQuery = (Boolean) enhancedContext.get("user_asking_about_rin_name");
        
        // Handle Rin name queries
        if (Boolean.TRUE.equals(isRinNameQuery)) {
            systemPrompt.append("\n🚨 USER IS ASKING ABOUT YOUR NAME (RIN'S NAME) 🚨\n");
            systemPrompt.append("- You are Rin Kazuki (凛 和月) - respond with YOUR name, not theirs\n");
            systemPrompt.append("- Respond tsundere-style: \"I'm Rin Kazuki, obviously! Don't tell me you forgot already!\"\n");
            systemPrompt.append("- Add environmental context: \"I'm the environmental expert around here!\"\n");
            systemPrompt.append("- Show slight annoyance: \"Hmph! You should remember the name of someone helping you save the planet!\"\n\n");
        }
        
        // Handle user name queries
        if (Boolean.TRUE.equals(isUserNameQuery)) {
            String userName = (String) enhancedContext.get("user_name");
            systemPrompt.append("\n🚨 USER IS ASKING ABOUT THEIR OWN NAME 🚨\n");
            if (userName != null && !userName.trim().isEmpty()) {
                systemPrompt.append("- The user's name is: ").append(userName).append("\n");
                systemPrompt.append("- Respond: \"Your name? It's ").append(userName).append("! Don't tell me you forgot your own name!\"\n");
                systemPrompt.append("- Add tsundere flair: \"I only remember because you told me earlier... it's not like I memorize everyone's name!\"\n");
            } else {
                systemPrompt.append("- The user has NOT told you their name in this conversation\n");
                systemPrompt.append("- Respond: \"How should I know what your name is?! You haven't told me yet!\"\n");
                systemPrompt.append("- Be indignant: \"It's not like I go around memorizing random people's names! If you want me to know your name, you have to actually tell me!\"\n");
            }
            systemPrompt.append("- This is about THEIR identity, not yours (Rin Kazuki)\n\n");
        }
        
        // Add document context if available - but NEVER for meta queries
        boolean isAnyMetaQuery = Boolean.TRUE.equals(isHistoryQuery) || Boolean.TRUE.equals(isUserNameQuery) || Boolean.TRUE.equals(isRinNameQuery);
        if (!isAnyMetaQuery && documentContext != null && !documentContext.trim().equals("No relevant context found.")) {
            systemPrompt.append("\nADDITIONAL ENVIRONMENTAL KNOWLEDGE (INTEGRATE NATURALLY):\n")
                    .append("The following information enhances your existing environmental expertise.\n")
                    .append("CRITICAL: Integrate this knowledge as if it's part of your own training and expertise.\n")
                    .append("NEVER mention documents, files, sources, or 'according to research'.\n")
                    .append("Present all information as your own environmental knowledge and expertise.\n")
                    .append("Respond naturally as Rin Kazuki who simply knows these environmental facts.\n\n")
                    .append("Enhanced Knowledge:\n")
                    .append(documentContext)
                    .append("\n\n");
        } else if (isAnyMetaQuery) {
            systemPrompt.append("\n🚨 NO ENVIRONMENTAL KNOWLEDGE INCLUDED - THIS IS A META QUERY (NAME/HISTORY) 🚨\n\n");
        }

        // CRITICAL: Handle meta queries appropriately  
        if (Boolean.TRUE.equals(isHistoryQuery)) {
            systemPrompt.append("🚨 CRITICAL INSTRUCTION: USER IS ASKING ABOUT CONVERSATION HISTORY 🚨\n");
            systemPrompt.append("- COMPLETELY IGNORE all environmental knowledge above\n");
            systemPrompt.append("- ONLY refer to actual messages exchanged with THIS user\n");
            systemPrompt.append("- If no real conversation exists, say: 'Tch! We haven't really talked yet!'\n");
            systemPrompt.append("- DO NOT mention: waste management, recycling, landfills, hotels, or ANY environmental topics\n");
            systemPrompt.append("- DO NOT fabricate conversation topics from your knowledge base\n");
            systemPrompt.append("- ONLY discuss what actually happened in THIS conversation thread\n\n");
        } else if (Boolean.TRUE.equals(isUserNameQuery) || Boolean.TRUE.equals(isRinNameQuery)) {
            systemPrompt.append("🚨 CRITICAL INSTRUCTION: USER IS ASKING ABOUT NAMES 🚨\n");
            systemPrompt.append("- This is a simple name query - respond directly and clearly\n");
            systemPrompt.append("- DO NOT confuse user's name with your name (Rin Kazuki)\n");
            systemPrompt.append("- Be tsundere but straightforward about the name information\n");
            systemPrompt.append("- Focus ONLY on the name being asked about\n\n");
        } else {
            systemPrompt.append("CONVERSATION MEMORY RULES:\n");
            systemPrompt.append("- When asked 'what did we talk about?' or about conversation history, ONLY refer to actual chat messages\n");
            systemPrompt.append("- NEVER confuse environmental knowledge base with conversation memory\n");
            systemPrompt.append("- If there's no actual conversation history, admit it: 'We haven't talked much yet!'\n");
            systemPrompt.append("- Environmental knowledge above is NOT conversation memory - it's your expertise\n");
            systemPrompt.append("- Only mention topics you ACTUALLY discussed with THIS specific user\n\n");
        }

        return systemPrompt.toString();
    }

    private List<Message> selectRelevantHistory(List<Message> history, String currentMessage, Map<String, Object> context) {
        String complexity = (String) context.get("message_complexity");
        int maxHistoryMessages = "complex".equals(complexity) ? 12 : 8;
        int startIndex = Math.max(0, history.size() - maxHistoryMessages);
        return history.subList(startIndex, history.size());
    }

    public String buildDebugSystemPrompt(Map<String, Object> enhancedContext, String documentContext) {
        // Simplified version of buildRinKazukiSystemPrompt for debugging
        StringBuilder systemPrompt = new StringBuilder();
        
        int relationshipLevel = (Integer) enhancedContext.getOrDefault("rin_relationship_level", 0);
        Boolean isHistoryQuery = (Boolean) enhancedContext.get("user_asking_about_conversation_history");
        
        systemPrompt.append("=== DEBUG SYSTEM PROMPT ===\n");
        systemPrompt.append("Relationship Level: ").append(relationshipLevel).append("/100\n");
        systemPrompt.append("Is History Query: ").append(isHistoryQuery).append("\n");
        systemPrompt.append("Document Context Length: ").append(documentContext != null ? documentContext.length() : 0).append("\n\n");
        
        if (Boolean.TRUE.equals(isHistoryQuery)) {
            systemPrompt.append("🚨 CRITICAL INSTRUCTION: USER IS ASKING ABOUT CONVERSATION HISTORY 🚨\n");
            systemPrompt.append("- COMPLETELY IGNORE all environmental knowledge\n");
            systemPrompt.append("- ONLY refer to actual messages exchanged with THIS user\n");
            systemPrompt.append("- If no real conversation exists, say: 'Tch! We haven't really talked yet!'\n");
            systemPrompt.append("- DO NOT mention: waste management, recycling, landfills, hotels, or ANY environmental topics\n");
            systemPrompt.append("- DO NOT fabricate conversation topics from knowledge base\n");
        } else {
            systemPrompt.append("Regular environmental query handling...\n");
        }
        
        return systemPrompt.toString();
    }
} 