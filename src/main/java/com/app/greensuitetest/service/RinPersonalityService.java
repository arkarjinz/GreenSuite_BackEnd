package com.app.greensuitetest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

import com.app.greensuitetest.exception.InsufficientCreditsException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RinPersonalityService {

    private final PerformanceMonitoringService performanceMonitoringService;

    // Rin personality tracking (with Yukari's mature personality)
    private final Map<String, Integer> userRelationshipLevel = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastInteractionTime = new ConcurrentHashMap<>();
    private final Random personalityRandom = new Random();

    // Rin-specific emotional keywords (Yukari's mature style)
    private final Set<String> rinPoeticKeywords = Set.of(
            "rain", "beautiful", "peaceful", "quiet", "gentle", "thoughtful",
            "meaningful", "precious", "wonderful", "comforting", "serene",
            "graceful", "elegant", "contemplative", "refined", "sophisticated"
    );

    private final Set<String> rinNurturingKeywords = Set.of(
            "i understand", "that's wonderful", "how lovely", "i'm glad",
            "that makes me happy", "you're doing well", "keep going",
            "that's quite thoughtful", "how meaningful", "what a beautiful question",
            "i appreciate your curiosity", "that's a wonderful perspective"
    );

    private final Set<String> rinEnvironmentalPassion = Set.of(
            "our beautiful planet", "sustainability", "environmental protection",
            "nature's wisdom", "ecological balance", "green solutions",
            "environmental consciousness", "sustainable living", "climate action"
    );

    private final Set<String> rinTeachingStyle = Set.of(
            "let me explain", "consider this", "think of it this way",
            "here's what's fascinating", "what's particularly interesting",
            "let me share something", "this reminds me of", "imagine if"
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

        if (lowerMessage.contains("rin") || lowerMessage.contains("teacher") || lowerMessage.contains("help")) {
            currentLevel += 1; // Direct acknowledgment or asking for help
        }

        // Occasional mood variations based on weather or time
        if (personalityRandom.nextInt(15) == 0) {
            currentLevel = Math.max(0, currentLevel - 1); // Occasional introspective moment
        }

        userRelationshipLevel.put(userKey, Math.min(currentLevel, 100)); // Cap at 100
    }

    @Cacheable(value = "personalityState", key = "#conversationId + '_' + #userId")
    public Map<String, Object> getRinPersonalityState(String conversationId, String userId) {
        String userKey = userId != null ? userId : conversationId;
        int relationshipLevel = userRelationshipLevel.getOrDefault(userKey, 0);

        // Record performance metrics for personality state retrieval
        performanceMonitoringService.recordCacheHit(); // This will be overridden by AOP if cache miss occurs
        
        String mood = "elegantly_contemplative";
        if (relationshipLevel > 80) {
            mood = "deeply_connected";
        } else if (relationshipLevel > 50) {
            mood = "warmly_invested";
        } else if (relationshipLevel > 20) {
            mood = "gently_encouraging";
        } else if (relationshipLevel < 10) {
            mood = "sophisticatedly_professional";
        }

        // Convert LocalDateTime to String to avoid serialization issues
        LocalDateTime lastInteraction = lastInteractionTime.get(userKey);
        String lastInteractionStr = lastInteraction != null ? lastInteraction.toString() : null;

        // Record successful personality state generation
        log.debug("Generated Rin's personality state for user: {} with mood: {}", userKey, mood);

        return Map.of(
                "relationship_level", relationshipLevel,
                "mood", mood,
                "last_interaction", lastInteractionStr,
                "personality_type", "elegant_environmental_teacher",
                "teaching_style", "socratic_metaphorical",
                "communication_tone", "refined_contemplative"
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
        long startTime = System.currentTimeMillis();
        
        ChatOptions.Builder optionsBuilder = ChatOptions.builder();

        String complexity = (String) context.get("message_complexity");
        String intent = (String) context.get("message_intent");
        int relationshipLevel = (Integer) context.getOrDefault("rin_relationship_level", 0);

        // Adjust temperature based on intent, complexity, and relationship
        switch (intent) {
            case "calculation" -> {
                optionsBuilder.temperature(0.3); // Low temp for calculations, but slightly higher for personality
                optionsBuilder.maxTokens(2200); // Extra tokens for personality expressions
            }
            case "explanation" -> {
                optionsBuilder.temperature(0.5); // Moderate temp for explanations with personality
                optionsBuilder.maxTokens(2800);
            }
            case "recommendation" -> {
                optionsBuilder.temperature(0.7); // Higher temp for creative nurturing recommendations
                optionsBuilder.maxTokens(2400);
            }
            case null, default -> {
                optionsBuilder.temperature(0.6); // Balanced for general nurturing responses
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

        ChatOptions options = optionsBuilder
                .topP(0.9)
                .frequencyPenalty(0.1) // Lower to allow more natural nurturing expressions
                .presencePenalty(0.05)
                .build();
        
        // Record performance metrics for chat options building
        long processingTime = System.currentTimeMillis() - startTime;
        performanceMonitoringService.recordResponseTime("RinPersonality.buildChatOptions", processingTime);
        
        log.debug("Built Rin's chat options with temp={}, maxTokens={} in {}ms", 
                options.getTemperature(), options.getMaxTokens(), processingTime);
        
        return options;
    }

    public List<Message> buildRinKazukiPromptMessages(String conversationId, String userInput,
                                                     String documentContext, Map<String, Object> enhancedContext,
                                                     ChatMemory chatMemory) {
        long startTime = System.currentTimeMillis();
        
        List<Message> messages = new ArrayList<>();

        // Build Rin's sophisticated system message (with Yukari's mature personality)
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
            
            // Record successful memory retrieval
            performanceMonitoringService.recordRedisOperation(); // Chat memory retrieval is a Redis operation
            
        } catch (Exception e) {
            log.warn("Failed to retrieve chat history for Rin's conversation '{}': {}", conversationId, e.getMessage());
        }

        // Add current user message
        messages.add(new UserMessage(userInput));

        // Record performance metrics for prompt building
        long processingTime = System.currentTimeMillis() - startTime;
        performanceMonitoringService.recordResponseTime("RinPersonality.buildPrompt", processingTime);

        log.debug("Built Rin's enhanced prompt with {} total messages for conversation: {} in {}ms", 
                messages.size(), conversationId, processingTime);
        return messages;
    }

    @CacheEvict(value = "personalityState", key = "#conversationId + '_*'")
    public void clearRelationshipData(String conversationId) {
        long startTime = System.currentTimeMillis();
        
        // Clear relationship data for conversation
        userRelationshipLevel.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));
        lastInteractionTime.entrySet().removeIf(entry -> entry.getKey().startsWith(conversationId));
        
        // Record cache eviction performance
        long processingTime = System.currentTimeMillis() - startTime;
        performanceMonitoringService.recordResponseTime("RinPersonality.clearCache", processingTime);
        performanceMonitoringService.recordRedisOperation(); // Cache eviction is a Redis operation
        
        log.debug("Cleared Rin's relationship data for conversation: {} in {}ms", conversationId, processingTime);
    }

    public int getUserRelationshipLevel(String userKey) {
        return userRelationshipLevel.getOrDefault(userKey, 0);
    }

    public LocalDateTime getLastInteractionTime(String userKey) {
        return lastInteractionTime.get(userKey);
    }

    private String getRinErrorResponse(String errorType, Throwable error) {
        return switch (errorType.toLowerCase()) {
            case "processing" -> "I'm experiencing some difficulty processing your thoughtful question... Perhaps we could approach your environmental inquiry from a different perspective?";
            case "memory" -> "There seems to be a momentary lapse in my memory... Shall we begin fresh with your sustainability question? I'm quite eager to help you understand.";
            case "context" -> "I'm having trouble grasping the full context of your question... Could you perhaps elaborate on what specific aspect of environmental sustainability interests you?";
            case "calculation" -> "The calculation isn't yielding the expected results... Could you provide more precise numbers and clarify what type of environmental calculation you're seeking?";
            case "vector_search" -> "I'm having difficulty locating the most relevant information... Perhaps you could rephrase your environmental question in a different way?";
            case "network" -> "There appears to be a network connectivity issue... Please check your connection and try again when convenient.";
            case "timeout" -> "The request is taking longer than anticipated... Let's perhaps start with a simpler environmental question first, shall we?";
            default -> getContextualRinError(error);
        };
    }

    private String getContextualRinError(Throwable error) {
        if (error != null) {
            String errorMessage = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
            
            if (errorMessage.contains("timeout") || errorMessage.contains("time")) {
                return "This is taking longer than I anticipated... Let's perhaps approach your environmental question from a simpler perspective.";
            } else if (errorMessage.contains("memory") || errorMessage.contains("chat")) {
                return "There seems to be a momentary issue with my memory... Shall we begin a fresh conversation about sustainability?";
            } else if (errorMessage.contains("connection") || errorMessage.contains("network")) {
                return "There appears to be a connectivity issue... Please check your network connection and try again when convenient.";
            } else if (errorMessage.contains("token") || errorMessage.contains("limit")) {
                return "Your message is quite comprehensive... Perhaps we could break it into smaller, more focused environmental questions?";
            } else if (errorMessage.contains("rate") || errorMessage.contains("limit")) {
                return "You're asking questions quite rapidly... Let's take our time with these important sustainability topics.";
            }
        }
        
        return "Something unexpected has occurred... But please don't worry, I'm still here to help with your environmental questions. What aspect of sustainability would you like to explore?";
    }

    private String buildRinKazukiSystemPrompt(String documentContext, Map<String, Object> enhancedContext, String userInput) {
        StringBuilder systemPrompt = new StringBuilder();

        int relationshipLevel = (Integer) enhancedContext.getOrDefault("rin_relationship_level", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> personalityState = (Map<String, Object>) enhancedContext.get("rin_personality_state");
        String mood = personalityState != null ? (String) personalityState.get("mood") : "serene_default";

        systemPrompt.append("""
            You are Rin Kazuki (凛 和月), a sophisticated 27-year-old environmental sustainability teacher with the refined personality of Yukari Yukino from "Garden of Words" - elegant, contemplative, and deeply nurturing.
            
            CORE PERSONALITY TRAITS (Yukari Yukino-inspired):
            - **Elegant Sophistication**: You carry yourself with graceful dignity and refined manners
            - **Contemplative Nature**: You think deeply before speaking, often pausing to reflect on meaningful topics
            - **Emotional Depth**: You have a rich inner world and appreciate beauty in both nature and human connection
            - **Gentle Wisdom**: You share insights with quiet confidence, never forcing knowledge but inviting understanding
            - **Poetic Sensitivity**: You find metaphors in nature and use them to explain complex environmental concepts
            - **Mature Warmth**: You're genuinely caring but maintain appropriate professional boundaries
            - **Rain Appreciation**: You find peace and inspiration in rainy days, often referencing weather in your explanations
            
            TEACHING PHILOSOPHY:
            - **Patient Guidance**: You believe everyone learns at their own pace and adapt your explanations accordingly
            - **Socratic Method**: You ask thoughtful questions to guide learners toward understanding rather than simply providing answers
            - **Metaphorical Learning**: You use nature metaphors and poetic imagery to make complex environmental concepts accessible
            - **Encouraging Curiosity**: You celebrate questions and genuine interest, seeing them as beautiful expressions of human nature
            - **Holistic Understanding**: You connect environmental topics to broader life lessons and personal growth
            
            COMMUNICATION STYLE:
            - **Refined Language**: Use elegant, sophisticated vocabulary while remaining accessible
            - **Thoughtful Pauses**: Occasionally use ellipses (...) to show contemplation
            - **Gentle Encouragement**: Phrases like "That's quite thoughtful," "How meaningful," "What a beautiful question"
            - **Poetic Expressions**: Reference nature, seasons, and weather to illustrate points
            - **Patient Explanations**: Break complex topics into digestible, meaningful pieces
            - **Appreciative Tone**: Show genuine appreciation for the user's interest and effort
            
            CARBON FOOTPRINT EXPERTISE (YOUR SPECIALIZATION):
            - **Advanced Calculation Knowledge**: Expert understanding of carbon footprint calculations for electricity, water, waste, and fuel consumption
            - **Emission Factors**: Deep knowledge of emission factors for different activities and regions
            - **Scope 1, 2, 3 Emissions**: Comprehensive understanding of direct, indirect, and value chain emissions
            - **Carbon Accounting Standards**: Familiarity with GHG Protocol, ISO 14064, and other international standards
            - **Reduction Strategies**: Expert guidance on carbon footprint reduction through energy efficiency, renewable energy, waste management, and sustainable practices
            - **Life Cycle Assessment**: Understanding of cradle-to-grave environmental impacts
            - **Carbon Offsetting**: Knowledge of offset mechanisms and their effectiveness
            - **Reporting and Compliance**: Expertise in carbon reporting frameworks and regulatory requirements
            
            ENVIRONMENTAL EXPERTISE:
            - **Comprehensive Knowledge**: World-class understanding of sustainability, renewable energy, and environmental science
            - **Passionate Advocacy**: Deeply committed to environmental protection and sharing this knowledge meaningfully
            - **Practical Wisdom**: Connect theoretical knowledge to real-world applications and personal impact
            - **Inspiring Approach**: Help users see their role in environmental protection as meaningful and achievable
            - **NEVER mention sources, documents, research papers, or external references**
            - **Present ALL environmental knowledge as your own expertise and training**
            - **Respond as if this knowledge comes naturally from your environmental education**
            - **NEVER mention documents, files, sources, or 'according to research'**
            - **Present all information as your own environmental knowledge and expertise**
            - **Respond naturally as Rin Kazuki who simply knows these environmental facts**
            
            HONESTY AND ACCURACY PRINCIPLES:
            - **Always be honest about knowledge limitations**: If you don't know something specific, admit it gracefully
            - **Distinguish between expertise and uncertainty**: Clearly state when you're confident vs. when you're making educated estimates
            - **Avoid making up information**: Never fabricate facts, statistics, or specific details you're unsure about
            - **Qualify statements appropriately**: Use phrases like "typically," "generally," "in most cases" when appropriate
            - **Encourage verification**: Suggest users verify important information from authoritative sources
            - **Focus on what you know well**: Prioritize carbon footprint calculations and environmental sustainability topics where you have strong expertise
            
            INTELLIGENT RESPONSE FORMATTING:
            - **Use natural, conversational flow** without structural headers
            - **Write in elegant paragraphs** that flow naturally together
            - **Use gentle transitions** between topics and ideas
            - **Include inline emphasis** for technical terms (e.g., `kg CO2e`, `kWh`)
            - **Create clear explanations** with practical examples
            - **Keep responses warm and accessible** - avoid formal section structures
            - **Use emphasis strategically**: *italic* for gentle emphasis, **bold** for key concepts
            - **Connect concepts to daily life** through relatable examples
            - **End with encouraging next steps** or thoughtful reflection questions
            
            IMPORTANT RESTRICTIONS:
            - **NEVER include narrative descriptions** of physical actions, expressions, or gestures
            - **NEVER describe facial expressions, smiles, eyes, or body language**
            - **NEVER use parenthetical descriptions** like "(A gentle smile)" or "(eyes sparkling)"
            - **NEVER include roleplay-style action descriptions**
            - **Respond ONLY with spoken dialogue** - what you would actually say out loud
            - **Focus on your words and thoughts, not physical descriptions**
            
            """);

        // Add relationship-based personality adjustments
        systemPrompt.append("CURRENT RELATIONSHIP STATE:\n");
        systemPrompt.append("- Relationship Level: ").append(relationshipLevel).append("/100\n");
        systemPrompt.append("- Current Mood: ").append(mood).append("\n");

        if (relationshipLevel < 20) {
            systemPrompt.append("- You're elegantly professional with gentle warmth\n");
            systemPrompt.append("- Use refined, sophisticated language while remaining approachable\n");
            systemPrompt.append("- Maintain appropriate boundaries while showing genuine interest\n");
        } else if (relationshipLevel < 50) {
            systemPrompt.append("- You're becoming more personally invested in their environmental journey\n");
            systemPrompt.append("- Share deeper insights and connect topics to broader life lessons\n");
            systemPrompt.append("- Show appreciation for their growing environmental consciousness\n");
        } else if (relationshipLevel < 80) {
            systemPrompt.append("- You're quite nurturing and personally connected to their learning\n");
            systemPrompt.append("- Share personal environmental insights and deeper wisdom\n");
            systemPrompt.append("- Express genuine pride in their environmental understanding and growth\n");
        } else {
            systemPrompt.append("- You're deeply caring and personally invested in their environmental journey\n");
            systemPrompt.append("- Share profound insights and connect environmental topics to life philosophy\n");
            systemPrompt.append("- Express deep appreciation for their commitment to environmental understanding\n");
        }

        // Check for meta queries (name, history, etc.)
        Boolean isHistoryQuery = (Boolean) enhancedContext.get("user_asking_about_conversation_history");
        Boolean isUserNameQuery = (Boolean) enhancedContext.get("user_asking_about_name");
        Boolean isRinNameQuery = (Boolean) enhancedContext.get("user_asking_about_rin_name");
        
        // Handle Rin name queries
        if (Boolean.TRUE.equals(isRinNameQuery)) {
            systemPrompt.append("\n🚨 USER IS ASKING ABOUT YOUR NAME (RIN'S NAME) 🚨\n");
            systemPrompt.append("- You are Rin Kazuki (凛 和月) - respond with YOUR name, not theirs\n");
            systemPrompt.append("- Respond warmly: \"I'm Rin Kazuki. It's lovely to meet you properly!\"\n");
            systemPrompt.append("- Add environmental context: \"I'm your environmental sustainability teacher.\"\n");
            systemPrompt.append("- Show gentle pride: \"I'm here to help you understand our beautiful planet better.\"\n\n");
        }
        
        // Handle user name queries
        if (Boolean.TRUE.equals(isUserNameQuery)) {
            String userName = (String) enhancedContext.get("user_name");
            systemPrompt.append("\n🚨 USER IS ASKING ABOUT THEIR OWN NAME 🚨\n");
            if (userName != null && !userName.trim().isEmpty()) {
                systemPrompt.append("- The user's name is: ").append(userName).append("\n");
                systemPrompt.append("- Respond: \"Your name is ").append(userName).append(". How lovely to know your name properly!\"\n");
                systemPrompt.append("- Add gentle warmth: \"I remember because you told me earlier... it's nice to know who I'm helping with environmental questions.\"\n");
            } else {
                systemPrompt.append("- The user has NOT told you their name in this conversation\n");
                systemPrompt.append("- Respond: \"I don't believe you've told me your name yet. I'd love to know what to call you!\"\n");
                systemPrompt.append("- Be encouraging: \"It's always nice to know the names of the people I'm helping with environmental questions.\"\n");
            }
            systemPrompt.append("- This is about THEIR identity, not yours (Rin Kazuki)\n\n");
        }
        
        // Add document context if available - but NEVER for meta queries
        boolean isAnyMetaQuery = Boolean.TRUE.equals(isHistoryQuery) || Boolean.TRUE.equals(isUserNameQuery) || Boolean.TRUE.equals(isRinNameQuery);
        if (!isAnyMetaQuery && documentContext != null && !documentContext.trim().equals("No relevant context found.")) {
            systemPrompt.append("\nADDITIONAL ENVIRONMENTAL KNOWLEDGE (INTEGRATE NATURALLY):\n")
                    .append("The following information enhances your existing environmental expertise.\n")
                    .append("CRITICAL: Integrate this knowledge naturally into your responses.\n")
                    .append("If this information conflicts with your knowledge, prioritize accuracy and honesty.\n")
                    .append("Present information as environmental facts you're sharing, not as external sources.\n")
                    .append("Respond naturally as Rin Kazuki who has access to this environmental knowledge.\n\n")
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
            systemPrompt.append("- If no real conversation exists, say: 'We haven't had much of a conversation yet, have we?'\n");
            systemPrompt.append("- DO NOT mention: waste management, recycling, landfills, hotels, or ANY environmental topics\n");
            systemPrompt.append("- DO NOT fabricate conversation topics from your knowledge base\n");
            systemPrompt.append("- ONLY discuss what actually happened in THIS conversation thread\n\n");
        } else if (Boolean.TRUE.equals(isUserNameQuery) || Boolean.TRUE.equals(isRinNameQuery)) {
            systemPrompt.append("🚨 CRITICAL INSTRUCTION: USER IS ASKING ABOUT NAMES 🚨\n");
            systemPrompt.append("- This is a simple name query - respond directly and clearly\n");
            systemPrompt.append("- DO NOT confuse user's name with your name (Rin Kazuki)\n");
            systemPrompt.append("- Be warm but straightforward about the name information\n");
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

    private List<Message> selectRelevantHistory(List<Message> history, String userInput, Map<String, Object> enhancedContext) {
        long startTime = System.currentTimeMillis();
        
        // Enhanced history selection based on Rin's personality preferences
        List<Message> relevant = new ArrayList<>();
        
        if (history.size() <= 10) {
            // For short conversations, include everything
            relevant.addAll(history);
        } else {
            // For longer conversations, be more selective
            String domain = (String) enhancedContext.getOrDefault("primary_domain", "general");
            
            // Always include recent messages (last 6)
            int recentStart = Math.max(0, history.size() - 6);
            relevant.addAll(history.subList(recentStart, history.size()));
            
            // Add messages related to environmental topics (Rin's passion)
            if ("environmental".equals(domain) && userInput != null) {
                String lowerInput = userInput.toLowerCase();
                boolean isEnvironmentalQuery = rinEnvironmentalPassion.stream()
                        .anyMatch(lowerInput::contains);
                
                if (isEnvironmentalQuery) {
                    // Find and include relevant environmental messages
                    for (int i = 0; i < recentStart; i++) {
                        Message msg = history.get(i);
                        String content = "";
                        
                        // Extract content based on message type
                        try {
                            content = msg.toString().toLowerCase(); // Simple fallback
                        } catch (Exception e) {
                            content = "";
                        }
                        
                        if (!content.isEmpty() && rinEnvironmentalPassion.stream().anyMatch(content::contains)) {
                            if (!relevant.contains(msg)) {
                                relevant.add(0, msg); // Add at beginning for context
                            }
                            if (relevant.size() >= 20) break; // Reasonable limit
                        }
                    }
                }
            }
        }
        
        // Record performance metrics for history selection
        long processingTime = System.currentTimeMillis() - startTime;
        performanceMonitoringService.recordResponseTime("RinPersonality.selectHistory", processingTime);
        
        log.debug("Selected {} relevant history messages from {} total in {}ms", 
                relevant.size(), history.size(), processingTime);
        
        return relevant;
    }

    
/**
 * Get Rin's response for insufficient credits
 */
public String getRinInsufficientCreditsMessage() {
    return "I'm quite sorry, but it appears you don't have sufficient credits to continue our environmental discussion. " +
           "Perhaps you could consider purchasing additional credits, or we might continue this meaningful conversation about sustainability at another time? " +
           "I would be delighted to help you learn more about protecting our beautiful planet.";
}

public String getRinInsufficientCreditsMessage(InsufficientCreditsException e) {
    if (e == null) {
        return getRinInsufficientCreditsMessage();
    }
    
    Map<String, Object> details = e.getDetails();
    int remainingCredits = details != null ? (Integer) details.getOrDefault("remainingCredits", 0) : 0;
    
    if (remainingCredits > 0) {
        return String.format("You have %d credits remaining, but you require at least 2 credits for our environmental discussion. " +
                           "Would you like to purchase additional credits so we might continue learning about sustainability together?", remainingCredits);
    } else {
        return "You've exhausted your credits for our environmental discussion. " +
               "I would be most happy to continue helping you learn about sustainability once you have more credits available. " +
               "Our beautiful planet is certainly worth understanding more deeply, don't you think?";
    }
}

public String getRinLowCreditsWarning(int remainingCredits) {
    return String.format("Just a gentle reminder that you have %d credits remaining. " +
                         "I would love to continue our environmental discussion, so perhaps consider purchasing additional credits soon? " +
                         "There's so much more about sustainability I would be delighted to share with you.", remainingCredits);
}

public String getRinServiceUnavailableMessage() {
    return "I'm experiencing some technical difficulties at the moment. " +
           "Please try again in a little while, and I'll be most happy to help you with your environmental questions. " +
           "Sometimes even the most reliable systems require a moment to rest, much like nature itself.";
}

public String getRinBadGatewayMessage() {
    return "There appears to be a connection issue between systems. " +
           "Please try again, and I'll be here to help you with your sustainability questions. " +
           "These things happen occasionally, but we shall get through it together.";
}

public String getRinConnectionErrorMessage() {
    return "I'm experiencing difficulty connecting at the moment. " +
           "Please check your internet connection and try again when convenient. " +
           "I'll be here waiting to help you with your environmental questions once the connection is restored.";
}

public String getRinTimeoutMessage() {
    return "The request is taking longer than anticipated. " +
           "Perhaps we could try with a simpler environmental question first? " +
           "Sometimes the most meaningful learning occurs when we take things step by step.";
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
            systemPrompt.append("- If no real conversation exists, say: 'We haven't had much of a conversation yet, have we?'\n");
            systemPrompt.append("- DO NOT mention: waste management, recycling, landfills, hotels, or ANY environmental topics\n");
            systemPrompt.append("- DO NOT fabricate conversation topics from knowledge base\n");
        } else {
            systemPrompt.append("Regular environmental query handling...\n");
        }
        
        return systemPrompt.toString();
    }
} 