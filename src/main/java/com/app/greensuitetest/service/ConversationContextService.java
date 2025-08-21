package com.app.greensuitetest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationContextService {

    private final ChatMemory chatMemory;

    // Cache for extracted context to improve performance
    private final Map<String, Map<String, Object>> contextCache = new ConcurrentHashMap<>();

    // Rin Kazuki personality context tracking
    private final Map<String, List<String>> rinPersonalityMoments = new ConcurrentHashMap<>();
    private final Map<String, Integer> environmentalEngagementScore = new ConcurrentHashMap<>();

    // FIXED: Improved pattern matching for user name extraction
    private final List<Pattern> namePatterns = Arrays.asList(
            Pattern.compile("(?i)(?:my name is|i'm called|call me|i am)\\s+([A-Za-z][A-Za-z\\s]{1,30})(?:\\s|$|\\.|,|!)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)i'm\\s+([A-Za-z][A-Za-z]{2,20})(?:\\s|$|\\.|,|!)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)i am\\s+([A-Za-z][A-Za-z]{2,20})(?:\\s|$|\\.|,|!)", Pattern.CASE_INSENSITIVE)
    );

    // Enhanced patterns for Rin's personality detection
    private final List<Pattern> rinInteractionPatterns = Arrays.asList(
            Pattern.compile("(?i)(thank you|thanks|appreciate)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(rin|cute|smart|helpful)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(environment|sustainability|green|carbon|emission)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(please|could you|would you)", Pattern.CASE_INSENSITIVE)
    );

    private final List<Pattern> companyPatterns = Arrays.asList(
            Pattern.compile("(?i)i work at ([A-Za-z0-9\\s&.,]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)my company is ([A-Za-z0-9\\s&.,]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)i'm from ([A-Za-z0-9\\s&.,]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)at ([A-Za-z0-9\\s&.,]+) we", Pattern.CASE_INSENSITIVE)
    );

    private final List<Pattern> rolePatterns = Arrays.asList(
            Pattern.compile("(?i)i'm a ([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)i am a ([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)my role is ([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)i work as a ([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE)
    );

    private final Set<String> sustainabilityKeywords = Set.of(
            "carbon footprint", "emissions", "renewable energy", "sustainability",
            "esg", "environmental", "green", "climate", "waste", "recycling",
            "circular economy", "supply chain", "biodiversity", "water management",
            "scope 1", "scope 2", "scope 3", "ghg", "greenhouse gas", "net zero"
    );

    // Rin-specific emotional keywords
    private final Set<String> rinTsundereKeywords = Set.of(
            "hmph", "tch", "not that i care", "it's not like",
            "don't misunderstand", "don't get the wrong idea", "whatever"
    );

    private final Set<String> rinDereKeywords = Set.of(
            "i'm glad", "happy", "proud", "good job", "well done",
            "keep it up", "you're learning", "not bad"
    );

    // FIXED: Blacklisted names that should never be considered user names
    private final Set<String> blacklistedNames = Set.of(
            "rin", "kazuki", "rin kazuki", "assistant", "ai", "chatbot",
            "system", "bot", "claude", "gpt", "model", "computer", "hello",
            "hi", "hey", "good", "fine", "okay", "yes", "no", "sure", "maybe"
    );

    // FIXED: Patterns to detect name-related queries
    private final List<Pattern> nameQuestionPatterns = Arrays.asList(
            Pattern.compile("(?i)what\\s+is\\s+my\\s+name", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)what's\\s+my\\s+name", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)do\\s+you\\s+know\\s+my\\s+name", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)my\\s+name\\s+is\\s+what", Pattern.CASE_INSENSITIVE)
    );

    // NEW: Patterns to detect when users ask about Rin's name
    private final List<Pattern> rinNameQuestionPatterns = Arrays.asList(
            Pattern.compile("(?i)what\\s+is\\s+your\\s+name", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)what's\\s+your\\s+name", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)who\\s+are\\s+you", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)what\\s+should\\s+i\\s+call\\s+you", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)tell\\s+me\\s+your\\s+name", Pattern.CASE_INSENSITIVE)
    );

    // FIXED: Patterns to detect conversation history queries
    private final List<Pattern> historyQuestionPatterns = Arrays.asList(
            Pattern.compile("(?i)what\\s+did\\s+we\\s+talk\\s+about", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)what\\s+have\\s+we\\s+discussed", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)our\\s+conversation\\s+history", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)what\\s+was\\s+our\\s+conversation", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)recap\\s+our\\s+conversation", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)conversation\\s+summary", Pattern.CASE_INSENSITIVE)
    );

    // Temporarily disable caching to avoid serialization issues
    // @Cacheable(value = "conversationContext", key = "#conversationId + '_' + #currentMessage.hashCode()")
    public Map<String, Object> buildComprehensiveContext(String conversationId, String userId, String sessionId, String currentMessage) {
        Map<String, Object> context = new HashMap<>();

        try {
            // Validate conversation ID to prevent mixing
            validateConversationId(conversationId, userId, sessionId);
            
            // Get conversation history
            List<Message> history = chatMemory.get(conversationId);

            // FIXED: Always check for name and history queries first
            context.putAll(analyzeCurrentMessage(currentMessage, history));

            if (history.isEmpty()) {
                log.debug("No chat history found for conversation: {}", conversationId);
                context.put("is_new_conversation", true);
                context.put("history_length", 0);
                return context;
            }

            // Extract various context types only if not asking meta questions
            boolean isMetaQuery = (Boolean) context.getOrDefault("user_asking_about_name", false) ||
                    (Boolean) context.getOrDefault("user_asking_about_rin_name", false) ||
                    (Boolean) context.getOrDefault("user_asking_about_conversation_history", false);

            if (!isMetaQuery) {
                context.putAll(extractPersonalInformation(history));
                context.putAll(extractBusinessContext(history));
                context.putAll(extractTopicalContext(history));
                context.putAll(extractUserBehaviorPatterns(history));
            }

            // Always extract these for metadata
            context.putAll(extractConversationMetrics(history));
            context.putAll(extractRinPersonalityContext(history, conversationId));

            // Add session and user context if available
            if (userId != null) {
                context.put("user_id", userId);
            }
            if (sessionId != null) {
                context.put("session_id", sessionId);
            }

            // Add conversation metadata
            context.put("conversation_id", conversationId);
            context.put("context_extracted_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            context.put("history_length", history.size());

            log.debug("Built comprehensive context with {} elements for conversation: {}", context.size(), conversationId);

        } catch (Exception e) {
            log.error("Error building comprehensive context: {}", e.getMessage(), e);
            context.put("context_error", e.getMessage());
        }

        return context;
    }

    /**
     * Validate that the conversation ID is properly isolated and not shared
     */
    private void validateConversationId(String conversationId, String userId, String sessionId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            log.warn("Empty conversation ID provided - this should have been handled by the controller");
            return;
        }

        // Warning if using the old problematic default conversation ID
        if ("default".equals(conversationId)) {
            log.warn("DEPRECATED: Using shared 'default' conversation ID - this can cause memory mixing between users");
        }

        // Log conversation ID structure for debugging
        log.debug("Processing conversation: {} for user: {} session: {}", conversationId, userId, sessionId);
    }

    /**
     * FIXED: Analyze current message for meta queries (name and history questions)
     */
    private Map<String, Object> analyzeCurrentMessage(String currentMessage, List<Message> history) {
        Map<String, Object> messageContext = new HashMap<>();

        if (currentMessage == null || currentMessage.trim().isEmpty()) {
            return messageContext;
        }

        String lowerMessage = currentMessage.toLowerCase().trim();

        // FIXED: First check if asking about Rin's name (higher priority)
        boolean isAskingAboutRinName = rinNameQuestionPatterns.stream()
                .anyMatch(pattern -> pattern.matcher(lowerMessage).find());

        if (isAskingAboutRinName) {
            messageContext.put("user_asking_about_rin_name", true);
            log.debug("User asking about Rin's name");
            return messageContext;
        }

        // FIXED: Then check if asking about their own name
        boolean isAskingAboutUserName = nameQuestionPatterns.stream()
                .anyMatch(pattern -> pattern.matcher(lowerMessage).find());

        if (isAskingAboutUserName) {
            messageContext.put("user_asking_about_name", true);

            // Look for user's name in conversation history
            String userName = extractUserNameFromHistory(history);
            messageContext.put("user_name", userName); // null if not found

            log.debug("User asking about their own name. Found name: {}", userName);
            return messageContext;
        }

        // FIXED: Check if asking about conversation history
        boolean isAskingAboutHistory = historyQuestionPatterns.stream()
                .anyMatch(pattern -> pattern.matcher(lowerMessage).find());

        if (isAskingAboutHistory) {
            messageContext.put("user_asking_about_conversation_history", true);

            // FIXED: Determine if this is actually a new conversation
            int meaningfulExchanges = countMeaningfulExchanges(history);

            if (meaningfulExchanges == 0 || history.size() <= 2) {
                messageContext.put("is_new_conversation", true);
                messageContext.put("conversation_status", "just_started");
                log.debug("User asking about history but conversation just started");
            } else {
                messageContext.put("is_new_conversation", false);
                messageContext.putAll(extractConversationSummary(history));
                log.debug("User asking about history with {} meaningful exchanges", meaningfulExchanges);
            }

            return messageContext;
        }

        return messageContext;
    }

    /**
     * FIXED: Extract user name specifically from conversation history
     */
    private String extractUserNameFromHistory(List<Message> history) {
        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message);
                for (Pattern pattern : namePatterns) {
                    Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        String candidateName = matcher.group(1).trim();
                        if (isValidUserName(candidateName)) {
                            return capitalizeFirstLetter(candidateName);
                        }
                    }
                }
            }
        }
        return null; // No name found
    }

    /**
     * FIXED: Count meaningful exchanges (excluding meta questions)
     */
    private int countMeaningfulExchanges(List<Message> history) {
        int meaningfulCount = 0;

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message).toLowerCase().trim();

                // Skip meta questions and very short messages
                if (content.length() > 5 &&
                        !isMetaQuestion(content) &&
                        !isGreeting(content) &&
                        !isSimpleAcknowledgment(content)) {
                    meaningfulCount++;
                }
            }
        }

        return meaningfulCount;
    }

    /**
     * FIXED: Check if message is a meta question
     */
    private boolean isMetaQuestion(String content) {
        return nameQuestionPatterns.stream().anyMatch(p -> p.matcher(content).find()) ||
                rinNameQuestionPatterns.stream().anyMatch(p -> p.matcher(content).find()) ||
                historyQuestionPatterns.stream().anyMatch(p -> p.matcher(content).find());
    }

    /**
     * FIXED: Check if message is just a greeting
     */
    private boolean isGreeting(String content) {
        String[] greetings = {"hi", "hello", "hey", "good morning", "good afternoon", "good evening", 
                             "greetings", "howdy", "what's up", "how are you"};
        return Arrays.stream(greetings).anyMatch(content::contains);
    }

    /**
     * NEW: Check if message is just a simple acknowledgment
     */
    private boolean isSimpleAcknowledgment(String content) {
        String[] acknowledgments = {"ok", "okay", "thanks", "thank you", "got it", "i see", 
                                  "understood", "alright", "yes", "no", "sure", "fine"};
        return content.length() <= 15 && Arrays.stream(acknowledgments).anyMatch(content::equals);
    }

    /**
     * FIXED: Extract conversation summary for history queries
     */
    private Map<String, Object> extractConversationSummary(List<Message> history) {
        Map<String, Object> summaryContext = new HashMap<>();

        summaryContext.put("conversation_topics", extractActualConversationTopics(history));
        summaryContext.put("conversation_highlights", extractConversationHighlights(history));
        summaryContext.put("user_questions_asked", extractUserQuestions(history));
        summaryContext.put("conversation_flow", getConversationFlow(history));

        return summaryContext;
    }

    /**
     * FIXED: Extract actual topics discussed in the conversation (not from documents)
     */
    private List<String> extractActualConversationTopics(List<Message> history) {
        Set<String> topics = new LinkedHashSet<>();

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message).toLowerCase();

                // Skip meta questions
                if (isMetaQuestion(content)) {
                    continue;
                }

                // Extract sustainability topics that were actually discussed
                for (String keyword : sustainabilityKeywords) {
                    if (content.contains(keyword.toLowerCase())) {
                        topics.add(keyword);
                    }
                }
            }
        }

        return new ArrayList<>(topics);
    }

    /**
     * FIXED: Extract conversation highlights (main questions and answers)
     */
    private List<String> extractConversationHighlights(List<Message> history) {
        List<String> highlights = new ArrayList<>();

        for (int i = 0; i < history.size() - 1; i += 2) {
            if (i + 1 < history.size()) {
                Message userMsg = history.get(i);
                Message assistantMsg = history.get(i + 1);

                if (userMsg instanceof UserMessage && assistantMsg instanceof AssistantMessage) {
                    String userContent = getMessageContent(userMsg);
                    String assistantContent = getMessageContent(assistantMsg);

                    // Skip meta questions and greetings
                    if (userContent.length() > 10 && assistantContent.length() > 20 &&
                            !isMetaQuestion(userContent.toLowerCase()) &&
                            !isGreeting(userContent.toLowerCase())) {

                        String summary = "User asked about: " + truncateText(userContent, 100) +
                                " | Rin responded about: " + truncateText(assistantContent, 100);
                        highlights.add(summary);
                    }
                }
            }
        }

        // Return last 5 highlights to avoid overwhelming
        return highlights.stream().skip(Math.max(0, highlights.size() - 5)).collect(Collectors.toList());
    }

    /**
     * FIXED: Extract actual user questions from conversation
     */
    private List<String> extractUserQuestions(List<Message> history) {
        List<String> questions = new ArrayList<>();

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message);

                // Skip meta questions about name and conversation history
                String lowerContent = content.toLowerCase();
                if (isMetaQuestion(lowerContent) || isGreeting(lowerContent)) {
                    continue;
                }

                if (content.contains("?") || content.toLowerCase().startsWith("how") ||
                        content.toLowerCase().startsWith("what") || content.toLowerCase().startsWith("why")) {
                    questions.add(truncateText(content, 150));
                }
            }
        }

        // Return last 3 questions
        return questions.stream().skip(Math.max(0, questions.size() - 3)).collect(Collectors.toList());
    }

    /**
     * FIXED: Get conversation flow summary
     */
    private String getConversationFlow(List<Message> history) {
        int meaningfulExchanges = countMeaningfulExchanges(history);

        if (meaningfulExchanges == 0) {
            return "Just started conversation with introductions";
        } else if (meaningfulExchanges == 1) {
            return "Had one meaningful exchange";
        } else {
            return String.format("Had %d meaningful exchanges covering sustainability topics", meaningfulExchanges);
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * NEW: Extract Rin Kazuki's personality context from conversation history
     */
    private Map<String, Object> extractRinPersonalityContext(List<Message> history, String conversationId) {
        Map<String, Object> rinContext = new HashMap<>();

        int tsundereScore = 0;
        int dereScore = 0;
        int environmentalPassionScore = 0;
        List<String> personalityMoments = new ArrayList<>();
        List<String> environmentalTopics = new ArrayList<>();

        for (Message message : history) {
            if (message instanceof AssistantMessage) {
                String content = getMessageContent(message).toLowerCase();

                // Analyze Rin's tsundere expressions
                for (String keyword : rinTsundereKeywords) {
                    if (content.contains(keyword)) {
                        tsundereScore++;
                        personalityMoments.add("tsundere_moment: " + keyword);
                    }
                }

                // Analyze Rin's dere expressions
                for (String keyword : rinDereKeywords) {
                    if (content.contains(keyword)) {
                        dereScore++;
                        personalityMoments.add("dere_moment: " + keyword);
                    }
                }

                // Analyze environmental passion
                for (String keyword : sustainabilityKeywords) {
                    if (content.contains(keyword)) {
                        environmentalPassionScore++;
                        environmentalTopics.add(keyword);
                    }
                }

                // Special Rin expressions
                if (content.contains("no sappy lines")) {
                    personalityMoments.add("akira_reference: no sappy lines");
                }
            }
        }

        // Calculate personality balance
        double tsundereRatio = tsundereScore > 0 ? (double) tsundereScore / (tsundereScore + dereScore) : 0.7;
        String personalityBalance = tsundereRatio > 0.6 ? "mostly_tsun" :
                tsundereRatio > 0.4 ? "balanced_tsundere" : "leaning_dere";

        rinContext.put("rin_tsundere_score", tsundereScore);
        rinContext.put("rin_dere_score", dereScore);
        rinContext.put("rin_environmental_passion", environmentalPassionScore);
        rinContext.put("rin_personality_balance", personalityBalance);
        rinContext.put("rin_personality_moments", personalityMoments.stream().limit(10).collect(Collectors.toList()));
        rinContext.put("rin_environmental_topics", environmentalTopics.stream().distinct().limit(10).collect(Collectors.toList()));

        // Store for future reference
        rinPersonalityMoments.put(conversationId, personalityMoments);
        environmentalEngagementScore.put(conversationId, environmentalPassionScore);

        return rinContext;
    }

    private Map<String, Object> extractPersonalInformation(List<Message> history) {
        Map<String, Object> personalInfo = new HashMap<>();

        // FIXED: Extract user name with better validation
        String userName = extractUserNameFromHistory(history);
        if (userName != null) {
            personalInfo.put("user_name", userName);
        }

        // Extract preferences
        List<String> preferences = extractPreferences(history);
        if (!preferences.isEmpty()) {
            personalInfo.put("user_preferences", preferences);
        }

        // Extract communication style (enhanced for Rin interactions)
        String communicationStyle = analyzeCommunicationStyleWithRin(history);
        personalInfo.put("communication_style", communicationStyle);

        return personalInfo;
    }

    /**
     * FIXED: Validate if the extracted string is actually a user name
     */
    private boolean isValidUserName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String lowerName = name.toLowerCase().trim();

        // Check against blacklisted names
        if (blacklistedNames.contains(lowerName)) {
            return false;
        }

        // Check if it's too short or too long
        if (lowerName.length() < 2 || lowerName.length() > 30) {
            return false;
        }

        // Check if it contains only letters and spaces
        if (!lowerName.matches("^[a-z\\s]+$")) {
            return false;
        }

        // Check if it's a common word (not a name)
        Set<String> commonWords = Set.of("good", "fine", "okay", "yes", "no", "well", "sure", "maybe",
                "think", "know", "see", "want", "need", "like", "time", "work", "here", "there",
                "with", "from", "they", "this", "that", "will", "have", "been", "their", "said",
                "each", "which", "them", "would", "make", "very", "more", "most", "some", "all");

        if (commonWords.contains(lowerName)) {
            return false;
        }

        return true;
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Enhanced communication style analysis that considers interactions with Rin
     */
    private String analyzeCommunicationStyleWithRin(List<Message> history) {
        int formalCount = 0;
        int casualCount = 0;
        int technicalCount = 0;
        int friendlyCount = 0; // NEW: Track friendly interactions with Rin

        String[] formalWords = {"please", "thank you", "would you", "could you", "appreciate"};
        String[] casualWords = {"hey", "yeah", "ok", "cool", "awesome", "great"};
        String[] technicalWords = {"methodology", "calculation", "algorithm", "parameters", "metrics"};
        String[] friendlyWords = {"rin", "cute", "helpful", "smart", "thanks rin", "good job"}; // NEW

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message).toLowerCase();

                for (String word : formalWords) {
                    if (content.contains(word)) formalCount++;
                }
                for (String word : casualWords) {
                    if (content.contains(word)) casualCount++;
                }
                for (String word : technicalWords) {
                    if (content.contains(word)) technicalCount++;
                }
                for (String word : friendlyWords) { // NEW
                    if (content.contains(word)) friendlyCount++;
                }
            }
        }

        // Enhanced style detection
        if (friendlyCount > 2) {
            return "friendly_with_rin";
        } else if (technicalCount > formalCount && technicalCount > casualCount) {
            return "technical";
        } else if (formalCount > casualCount) {
            return "formal";
        } else if (casualCount > 0) {
            return "casual";
        }

        return "neutral";
    }

    // ... [Keep all the existing helper methods from the original code] ...

    private Map<String, Object> extractBusinessContext(List<Message> history) {
        Map<String, Object> businessContext = new HashMap<>();

        // Extract company information
        String company = extractWithPatterns(history, companyPatterns, "company");
        if (company != null) {
            businessContext.put("company_name", company);
        }

        // Extract role information
        String role = extractWithPatterns(history, rolePatterns, "role");
        if (role != null) {
            businessContext.put("user_role", role);
        }

        // Extract industry/domain with improved logic (FIXED from original)
        String domain = extractDomainImproved(history);
        if (domain != null && !domain.equals("unknown")) {
            businessContext.put("user_domain", domain);
        }

        // Extract business goals
        List<String> goals = extractBusinessGoals(history);
        if (!goals.isEmpty()) {
            businessContext.put("business_goals", goals);
        }

        return businessContext;
    }

    private Map<String, Object> extractTopicalContext(List<Message> history) {
        Map<String, Object> topicalContext = new HashMap<>();

        // Extract sustainability topics discussed
        Set<String> sustainabilityTopics = extractSustainabilityTopics(history);
        if (!sustainabilityTopics.isEmpty()) {
            topicalContext.put("sustainability_topics", new ArrayList<>(sustainabilityTopics));
        }

        // Extract frequently mentioned concepts
        Map<String, Integer> conceptFrequency = extractConceptFrequency(history);
        if (!conceptFrequency.isEmpty()) {
            topicalContext.put("frequent_concepts", conceptFrequency);
        }

        // Extract recent focus areas
        List<String> recentFocusAreas = extractRecentFocusAreas(history);
        if (!recentFocusAreas.isEmpty()) {
            topicalContext.put("recent_focus_areas", recentFocusAreas);
        }

        return topicalContext;
    }

    private Map<String, Object> extractConversationMetrics(List<Message> history) {
        Map<String, Object> metrics = new HashMap<>();

        int userMessages = 0;
        int assistantMessages = 0;
        int totalWords = 0;
        int questions = 0;
        int rinPersonalityExpressions = 0; // NEW

        for (Message message : history) {
            String content = getMessageContent(message);

            if (message instanceof UserMessage) {
                userMessages++;
                questions += content.split("\\?").length - 1;
            } else if (message instanceof AssistantMessage) {
                assistantMessages++;
                // Count Rin's personality expressions
                String lowerContent = content.toLowerCase();
                for (String keyword : rinTsundereKeywords) {
                    if (lowerContent.contains(keyword)) {
                        rinPersonalityExpressions++;
                    }
                }
                for (String keyword : rinDereKeywords) {
                    if (lowerContent.contains(keyword)) {
                        rinPersonalityExpressions++;
                    }
                }
            }

            totalWords += content.split("\\s+").length;
        }

        metrics.put("user_messages", userMessages);
        metrics.put("assistant_messages", assistantMessages);
        metrics.put("total_words", totalWords);
        metrics.put("questions_asked", questions);
        metrics.put("avg_words_per_message", history.isEmpty() ? 0 : totalWords / history.size());
        metrics.put("rin_personality_expressions", rinPersonalityExpressions); // NEW

        return metrics;
    }

    private Map<String, Object> extractUserBehaviorPatterns(List<Message> history) {
        Map<String, Object> patterns = new HashMap<>();

        // Analyze question patterns
        List<String> questionTypes = analyzeQuestionTypes(history);
        if (!questionTypes.isEmpty()) {
            patterns.put("question_patterns", questionTypes);
        }

        // Analyze interaction frequency
        Map<String, Integer> interactionPatterns = analyzeInteractionPatterns(history);
        if (!interactionPatterns.isEmpty()) {
            patterns.put("interaction_patterns", interactionPatterns);
        }

        // Analyze expertise level indicators
        String expertiseLevel = analyzeExpertiseLevel(history);
        patterns.put("expertise_level", expertiseLevel);

        // NEW: Analyze Rin interaction patterns
        Map<String, Integer> rinInteractionPatterns = analyzeRinInteractionPatterns(history);
        if (!rinInteractionPatterns.isEmpty()) {
            patterns.put("rin_interaction_patterns", rinInteractionPatterns);
        }

        return patterns;
    }

    /**
     * NEW: Analyze how users interact with Rin's personality
     */
    private Map<String, Integer> analyzeRinInteractionPatterns(List<Message> history) {
        Map<String, Integer> patterns = new HashMap<>();

        int complimentsToRin = 0;
        int environmentalQuestions = 0;
        int personalityAcknowledgments = 0;
        int politeInteractions = 0;

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message).toLowerCase();

                // Count compliments or acknowledgments to Rin
                if (content.contains("rin") && (content.contains("good") || content.contains("smart") ||
                        content.contains("helpful") || content.contains("cute") || content.contains("thank"))) {
                    complimentsToRin++;
                }

                // Count environmental engagement
                for (String keyword : sustainabilityKeywords) {
                    if (content.contains(keyword)) {
                        environmentalQuestions++;
                        break; // Count once per message
                    }
                }

                // Count personality acknowledgments
                if (content.contains("tsundere") || content.contains("personality") ||
                        content.contains("mood") || content.contains("character")) {
                    personalityAcknowledgments++;
                }

                // Count polite interactions
                for (Pattern pattern : rinInteractionPatterns) {
                    if (pattern.matcher(content).find()) {
                        politeInteractions++;
                        break; // Count once per message
                    }
                }
            }
        }

        patterns.put("compliments_to_rin", complimentsToRin);
        patterns.put("environmental_questions", environmentalQuestions);
        patterns.put("personality_acknowledgments", personalityAcknowledgments);
        patterns.put("polite_interactions", politeInteractions);

        return patterns;
    }

    // Keep all existing helper methods...
    private String extractWithPatterns(List<Message> history, List<Pattern> patterns, String type) {
        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message);
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        String result = matcher.group(1).trim();
                        if (!isCommonWord(result)) {
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<String> extractPreferences(List<Message> history) {
        List<String> preferences = new ArrayList<>();
        Pattern preferencePattern = Pattern.compile("(?i)(i like|i prefer|i love|i enjoy|i'm interested in) ([^.!?]+)", Pattern.CASE_INSENSITIVE);

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message);
                Matcher matcher = preferencePattern.matcher(content);
                while (matcher.find()) {
                    String preference = matcher.group(2).trim();
                    if (preference.length() > 3 && preference.length() < 100) {
                        preferences.add(preference);
                    }
                }
            }
        }

        return preferences.stream().distinct().collect(Collectors.toList());
    }

    /**
     * FIXED: Improved domain extraction with balanced scoring and higher thresholds
     * This addresses the agriculture domain bias issue from the original
     */
    private String extractDomainImproved(List<Message> history) {
        Map<String, Integer> domainScores = new HashMap<>();

        // Initialize all domains
        domainScores.put("manufacturing", 0);
        domainScores.put("technology", 0);
        domainScores.put("finance", 0);
        domainScores.put("healthcare", 0);
        domainScores.put("retail", 0);
        domainScores.put("energy", 0);
        domainScores.put("construction", 0);
        domainScores.put("transportation", 0);
        domainScores.put("agriculture", 0);

        // Enhanced keyword sets with more specific terms
        Map<String, String[]> domainKeywords = Map.of(
                "manufacturing", new String[]{"factory", "production", "assembly", "manufacturing", "industrial", "plant", "machinery", "fabrication"},
                "technology", new String[]{"software", "technology", "digital", "IT", "tech", "platform", "app", "system", "programming", "data"},
                "finance", new String[]{"bank", "financial", "investment", "capital", "fund", "trading", "portfolio", "loan", "credit"},
                "healthcare", new String[]{"hospital", "medical", "healthcare", "pharmaceutical", "clinical", "patient", "medicine", "treatment"},
                "retail", new String[]{"retail", "store", "customer", "sales", "commerce", "shopping", "consumer", "merchandise"},
                "energy", new String[]{"energy", "power", "utility", "grid", "renewable", "electricity", "oil", "gas", "solar", "wind"},
                "construction", new String[]{"construction", "building", "infrastructure", "contractor", "architect", "cement", "concrete"},
                "transportation", new String[]{"logistics", "transportation", "shipping", "freight", "delivery", "trucking", "fleet", "cargo"},
                "agriculture", new String[]{"agriculture", "farming", "crop", "livestock", "agricultural", "harvest", "farm", "rural", "soil"}
        );

        // Count keywords with weighted scoring
        for (Message message : history) {
            String content = getMessageContent(message).toLowerCase();

            for (Map.Entry<String, String[]> entry : domainKeywords.entrySet()) {
                String domain = entry.getKey();
                String[] keywords = entry.getValue();

                for (String keyword : keywords) {
                    if (content.contains(keyword)) {
                        // More specific terms get higher weights
                        int weight = keyword.length() > 6 ? 2 : 1;
                        domainScores.merge(domain, weight, Integer::sum);
                    }
                }
            }
        }

        // Find the highest scoring domain with minimum threshold
        Optional<Map.Entry<String, Integer>> topDomain = domainScores.entrySet().stream()
                .filter(entry -> entry.getValue() >= 3) // Increased threshold to require stronger evidence
                .max(Map.Entry.comparingByValue());

        if (topDomain.isPresent()) {
            // Additional validation: ensure the domain has significantly more mentions than others
            int topScore = topDomain.get().getValue();
            long competingDomains = domainScores.values().stream()
                    .filter(score -> score >= Math.max(2, topScore - 1))
                    .count();

            // Only return domain if it's clearly dominant
            if (competingDomains <= 2) {
                String domain = topDomain.get().getKey();
                log.debug("Detected user domain: {} with score: {}", domain, topScore);
                return domain;
            } else {
                log.debug("Multiple competing domains detected, returning general");
                return "general";
            }
        }

        log.debug("No clear domain detected from conversation history");
        return "general"; // Default to general instead of null
    }

    private List<String> extractBusinessGoals(List<Message> history) {
        List<String> goals = new ArrayList<>();
        String[] goalIndicators = {
                "we want to", "our goal is", "we aim to", "we're trying to",
                "we need to", "objective is", "target is", "hoping to"
        };

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message).toLowerCase();
                for (String indicator : goalIndicators) {
                    if (content.contains(indicator)) {
                        // Extract the goal text after the indicator
                        int startIndex = content.indexOf(indicator) + indicator.length();
                        int endIndex = Math.min(startIndex + 100, content.length());
                        String goalText = content.substring(startIndex, endIndex).trim();

                        // Find sentence end
                        int sentenceEnd = Math.min(
                                goalText.indexOf('.') != -1 ? goalText.indexOf('.') : goalText.length(),
                                goalText.indexOf('!') != -1 ? goalText.indexOf('!') : goalText.length()
                        );

                        if (sentenceEnd > 0) {
                            goalText = goalText.substring(0, sentenceEnd).trim();
                            if (goalText.length() > 10) {
                                goals.add(goalText);
                            }
                        }
                    }
                }
            }
        }

        return goals.stream().distinct().collect(Collectors.toList());
    }

    private Set<String> extractSustainabilityTopics(List<Message> history) {
        Set<String> topics = new HashSet<>();

        for (Message message : history) {
            String content = getMessageContent(message).toLowerCase();
            for (String keyword : sustainabilityKeywords) {
                if (content.contains(keyword.toLowerCase())) {
                    topics.add(keyword);
                }
            }
        }

        return topics;
    }

    private Map<String, Integer> extractConceptFrequency(List<Message> history) {
        Map<String, Integer> conceptFreq = new HashMap<>();

        // Key sustainability concepts to track
        String[] concepts = {
                "carbon", "emissions", "energy", "waste", "water", "sustainability",
                "renewable", "efficiency", "reduction", "calculation", "reporting",
                "compliance", "audit", "certification", "offset", "neutral"
        };

        for (Message message : history) {
            String content = getMessageContent(message).toLowerCase();
            for (String concept : concepts) {
                if (content.contains(concept)) {
                    conceptFreq.merge(concept, 1, Integer::sum);
                }
            }
        }

        // Only return concepts mentioned more than once
        return conceptFreq.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<String> extractRecentFocusAreas(List<Message> history) {
        // Focus on last 10 messages for recent topics
        int startIndex = Math.max(0, history.size() - 10);
        List<Message> recentHistory = history.subList(startIndex, history.size());

        Set<String> recentTopics = new HashSet<>();

        for (Message message : recentHistory) {
            String content = getMessageContent(message).toLowerCase();
            for (String keyword : sustainabilityKeywords) {
                if (content.contains(keyword.toLowerCase())) {
                    recentTopics.add(keyword);
                }
            }
        }

        return new ArrayList<>(recentTopics);
    }

    private List<String> analyzeQuestionTypes(List<Message> history) {
        List<String> questionTypes = new ArrayList<>();

        String[] howQuestions = {"how to", "how do", "how can", "how should"};
        String[] whatQuestions = {"what is", "what are", "what does", "what should"};
        String[] whyQuestions = {"why is", "why do", "why should", "why does"};
        String[] calculationQuestions = {"calculate", "compute", "formula", "equation"};

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message).toLowerCase();

                if (Arrays.stream(howQuestions).anyMatch(content::contains)) {
                    questionTypes.add("how-to");
                }
                if (Arrays.stream(whatQuestions).anyMatch(content::contains)) {
                    questionTypes.add("definition");
                }
                if (Arrays.stream(whyQuestions).anyMatch(content::contains)) {
                    questionTypes.add("explanation");
                }
                if (Arrays.stream(calculationQuestions).anyMatch(content::contains)) {
                    questionTypes.add("calculation");
                }
            }
        }

        return questionTypes.stream().distinct().collect(Collectors.toList());
    }

    private Map<String, Integer> analyzeInteractionPatterns(List<Message> history) {
        Map<String, Integer> patterns = new HashMap<>();

        int followUpQuestions = 0;
        int detailRequests = 0;
        int clarifications = 0;

        String[] followUpWords = {"also", "additionally", "furthermore", "moreover", "and what about"};
        String[] detailWords = {"more details", "elaborate", "explain further", "tell me more"};
        String[] clarificationWords = {"what do you mean", "can you clarify", "i don't understand"};

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message).toLowerCase();

                if (Arrays.stream(followUpWords).anyMatch(content::contains)) {
                    followUpQuestions++;
                }
                if (Arrays.stream(detailWords).anyMatch(content::contains)) {
                    detailRequests++;
                }
                if (Arrays.stream(clarificationWords).anyMatch(content::contains)) {
                    clarifications++;
                }
            }
        }

        patterns.put("follow_up_questions", followUpQuestions);
        patterns.put("detail_requests", detailRequests);
        patterns.put("clarification_requests", clarifications);

        return patterns;
    }

    private String analyzeExpertiseLevel(List<Message> history) {
        int beginnerIndicators = 0;
        int intermediateIndicators = 0;
        int expertIndicators = 0;

        String[] beginnerWords = {"basic", "simple", "beginner", "new to", "don't know", "what is"};
        String[] intermediateWords = {"understand", "familiar", "some experience", "general idea"};
        String[] expertWords = {"methodology", "implementation", "optimization", "advanced", "complex"};

        for (Message message : history) {
            if (message instanceof UserMessage) {
                String content = getMessageContent(message).toLowerCase();

                for (String word : beginnerWords) {
                    if (content.contains(word)) beginnerIndicators++;
                }
                for (String word : intermediateWords) {
                    if (content.contains(word)) intermediateIndicators++;
                }
                for (String word : expertWords) {
                    if (content.contains(word)) expertIndicators++;
                }
            }
        }

        if (expertIndicators > intermediateIndicators && expertIndicators > beginnerIndicators) {
            return "expert";
        } else if (intermediateIndicators > beginnerIndicators) {
            return "intermediate";
        } else if (beginnerIndicators > 0) {
            return "beginner";
        }

        return "unknown";
    }

    private boolean isCommonWord(String word) {
        String[] commonWords = {
                "good", "fine", "okay", "yes", "no", "well", "sure", "maybe",
                "think", "know", "see", "want", "need", "like", "time", "work"
        };
        return Arrays.stream(commonWords).anyMatch(w -> w.equalsIgnoreCase(word));
    }

    private String getMessageContent(Message message) {
        try {
            // Use reflection to get content safely across different Spring AI versions
            for (String methodName : Arrays.asList("getContent", "getText")) {
                try {
                    Object content = message.getClass().getMethod(methodName).invoke(message);
                    return content != null ? content.toString() : "";
                } catch (Exception ignored) {
                    // Try next method
                }
            }
            return message.toString();
        } catch (Exception e) {
            log.warn("Failed to extract message content: {}", e.getMessage());
            return message.toString();
        }
    }

    public void updateContextAfterInteraction(String conversationId, String userMessage, String assistantResponse) {
        try {
            // Update cached context based on new interaction
            String cacheKey = conversationId;
            Map<String, Object> cachedContext = contextCache.get(cacheKey);

            if (cachedContext != null) {
                // Update interaction count
                Integer interactionCount = (Integer) cachedContext.get("interaction_count");
                cachedContext.put("interaction_count", interactionCount != null ? interactionCount + 1 : 1);

                // Update last interaction time
                cachedContext.put("last_interaction", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                // Analyze and update recent topics
                updateRecentTopics(cachedContext, userMessage + " " + assistantResponse);

                // NEW: Update Rin personality tracking
                updateRinPersonalityTracking(conversationId, userMessage, assistantResponse);
            }

            log.debug("Updated context cache for conversation: {}", conversationId);
        } catch (Exception e) {
            log.warn("Failed to update context after interaction: {}", e.getMessage());
        }
    }

    /**
     * NEW: Update Rin's personality tracking based on interaction
     */
    private void updateRinPersonalityTracking(String conversationId, String userMessage, String assistantResponse) {
        List<String> moments = rinPersonalityMoments.getOrDefault(conversationId, new ArrayList<>());

        String lowerResponse = assistantResponse.toLowerCase();
        String lowerUser = userMessage.toLowerCase();

        // Track Rin's tsundere moments
        for (String keyword : rinTsundereKeywords) {
            if (lowerResponse.contains(keyword)) {
                moments.add("tsundere: " + keyword + " at " + LocalDateTime.now().format(DateTimeFormatter.ISO_TIME));
            }
        }

        // Track Rin's dere moments
        for (String keyword : rinDereKeywords) {
            if (lowerResponse.contains(keyword)) {
                moments.add("dere: " + keyword + " at " + LocalDateTime.now().format(DateTimeFormatter.ISO_TIME));
            }
        }

        // Track user's environmental engagement
        int currentScore = environmentalEngagementScore.getOrDefault(conversationId, 0);
        for (String keyword : sustainabilityKeywords) {
            if (lowerUser.contains(keyword)) {
                currentScore++;
                break; // Count once per message
            }
        }

        // Keep only recent moments (last 20)
        if (moments.size() > 20) {
            moments = moments.subList(moments.size() - 20, moments.size());
        }

        rinPersonalityMoments.put(conversationId, moments);
        environmentalEngagementScore.put(conversationId, currentScore);
    }

    private void updateRecentTopics(Map<String, Object> context, String content) {
        @SuppressWarnings("unchecked")
        List<String> recentTopics = (List<String>) context.getOrDefault("recent_topics", new ArrayList<>());

        String lowerContent = content.toLowerCase();
        for (String keyword : sustainabilityKeywords) {
            if (lowerContent.contains(keyword.toLowerCase()) && !recentTopics.contains(keyword)) {
                recentTopics.add(keyword);
                // Keep only the most recent 10 topics
                if (recentTopics.size() > 10) {
                    recentTopics.removeFirst();
                }
            }
        }

        context.put("recent_topics", recentTopics);
    }

    public Map<String, Object> getContextSummary(String conversationId) {
        try {
            Map<String, Object> context = buildComprehensiveContext(conversationId, null, null, "");
            Map<String, Object> summary = new HashMap<>();

            // Extract key summary information
            summary.put("user_name", context.get("user_name"));
            summary.put("company_name", context.get("company_name"));
            summary.put("user_domain", context.get("user_domain"));
            summary.put("expertise_level", context.get("expertise_level"));
            summary.put("communication_style", context.get("communication_style"));
            summary.put("recent_focus_areas", context.get("recent_focus_areas"));
            summary.put("interaction_count", context.get("user_messages"));

            // NEW: Add Rin personality summary
            summary.put("rin_personality_balance", context.get("rin_personality_balance"));
            summary.put("rin_environmental_passion", context.get("rin_environmental_passion"));
            summary.put("environmental_engagement_score", environmentalEngagementScore.get(conversationId));

            return summary;
        } catch (Exception e) {
            log.error("Failed to get context summary: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * NEW: Get Rin's personality moments for a conversation
     */
    public List<String> getRinPersonalityMoments(String conversationId) {
        return rinPersonalityMoments.getOrDefault(conversationId, new ArrayList<>());
    }

    /**
     * NEW: Get environmental engagement score
     */
    public int getEnvironmentalEngagementScore(String conversationId) {
        return environmentalEngagementScore.getOrDefault(conversationId, 0);
    }

    // Temporarily disable cache eviction
    // @CacheEvict(value = "conversationContext", key = "#conversationId + '_*'")
    public void clearContextCache(String conversationId) {
        contextCache.remove(conversationId);
        // NEW: Clear Rin personality data
        rinPersonalityMoments.remove(conversationId);
        environmentalEngagementScore.remove(conversationId);
        log.debug("Cleared context cache and Rin personality data for conversation: {}", conversationId);
    }

    public void clearAllContextCache() {
        contextCache.clear();
        // NEW: Clear all Rin personality data
        rinPersonalityMoments.clear();
        environmentalEngagementScore.clear();
        log.info("Cleared all context cache and Rin personality data");
    }
}