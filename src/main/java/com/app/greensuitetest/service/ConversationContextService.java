package com.app.greensuitetest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

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

    // Pattern matching for context extraction
    private final List<Pattern> namePatterns = Arrays.asList(
            Pattern.compile("(?i)my name is ([A-Za-z]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)i'm ([A-Za-z]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)i am ([A-Za-z]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)call me ([A-Za-z]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)i'm called ([A-Za-z]+)", Pattern.CASE_INSENSITIVE)
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

    public Map<String, Object> buildComprehensiveContext(String conversationId, String userId, String sessionId, String currentMessage) {
        Map<String, Object> context = new HashMap<>();

        try {
            // Get conversation history
            List<Message> history = chatMemory.get(conversationId);
            if (history == null || history.isEmpty()) {
                log.debug("No chat history found for conversation: {}", conversationId);
                return context;
            }

            // Extract various context types
            context.putAll(extractPersonalInformation(history));
            context.putAll(extractBusinessContext(history));
            context.putAll(extractTopicalContext(history));
            context.putAll(extractConversationMetrics(history));
            context.putAll(extractUserBehaviorPatterns(history));

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

    private Map<String, Object> extractPersonalInformation(List<Message> history) {
        Map<String, Object> personalInfo = new HashMap<>();

        // Extract user name
        String userName = extractWithPatterns(history, namePatterns, "name");
        if (userName != null) {
            personalInfo.put("user_name", userName);
        }

        // Extract preferences
        List<String> preferences = extractPreferences(history);
        if (!preferences.isEmpty()) {
            personalInfo.put("user_preferences", preferences);
        }

        // Extract communication style
        String communicationStyle = analyzeCommunicationStyle(history);
        if (communicationStyle != null) {
            personalInfo.put("communication_style", communicationStyle);
        }

        return personalInfo;
    }

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

        // Extract industry/domain
        String domain = extractDomain(history);
        if (domain != null) {
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

        for (Message message : history) {
            String content = getMessageContent(message);

            if (message instanceof UserMessage) {
                userMessages++;
                questions += content.split("\\?").length - 1;
            } else if (message instanceof AssistantMessage) {
                assistantMessages++;
            }

            totalWords += content.split("\\s+").length;
        }

        metrics.put("user_messages", userMessages);
        metrics.put("assistant_messages", assistantMessages);
        metrics.put("total_words", totalWords);
        metrics.put("questions_asked", questions);
        metrics.put("avg_words_per_message", history.isEmpty() ? 0 : totalWords / history.size());

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
        if (expertiseLevel != null) {
            patterns.put("expertise_level", expertiseLevel);
        }

        return patterns;
    }

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

    private String analyzeCommunicationStyle(List<Message> history) {
        int formalCount = 0;
        int casualCount = 0;
        int technicalCount = 0;

        String[] formalWords = {"please", "thank you", "would you", "could you", "appreciate"};
        String[] casualWords = {"hey", "yeah", "ok", "cool", "awesome", "great"};
        String[] technicalWords = {"methodology", "calculation", "algorithm", "parameters", "metrics"};

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
            }
        }

        if (technicalCount > formalCount && technicalCount > casualCount) {
            return "technical";
        } else if (formalCount > casualCount) {
            return "formal";
        } else if (casualCount > 0) {
            return "casual";
        }

        return "neutral";
    }

    private String extractDomain(List<Message> history) {
        Map<String, Integer> domainKeywords = new HashMap<>();
        domainKeywords.put("manufacturing", 0);
        domainKeywords.put("technology", 0);
        domainKeywords.put("finance", 0);
        domainKeywords.put("healthcare", 0);
        domainKeywords.put("retail", 0);
        domainKeywords.put("energy", 0);
        domainKeywords.put("construction", 0);
        domainKeywords.put("transportation", 0);
        domainKeywords.put("agriculture", 0);

        String[] manufacturingWords = {"factory", "production", "assembly", "manufacturing", "industrial"};
        String[] techWords = {"software", "technology", "digital", "IT", "tech", "platform"};
        String[] financeWords = {"bank", "financial", "investment", "capital", "fund"};
        String[] healthcareWords = {"hospital", "medical", "healthcare", "pharmaceutical", "clinical"};
        String[] retailWords = {"retail", "store", "customer", "sales", "commerce"};
        String[] energyWords = {"energy", "power", "utility", "grid", "renewable"};
        String[] constructionWords = {"construction", "building", "infrastructure", "contractor"};
        String[] transportWords = {"logistics", "transportation", "shipping", "freight", "delivery"};
        String[] agriWords = {"agriculture", "farming", "crop", "livestock", "agricultural"};

        for (Message message : history) {
            String content = getMessageContent(message).toLowerCase();

            countKeywords(content, manufacturingWords, domainKeywords, "manufacturing");
            countKeywords(content, techWords, domainKeywords, "technology");
            countKeywords(content, financeWords, domainKeywords, "finance");
            countKeywords(content, healthcareWords, domainKeywords, "healthcare");
            countKeywords(content, retailWords, domainKeywords, "retail");
            countKeywords(content, energyWords, domainKeywords, "energy");
            countKeywords(content, constructionWords, domainKeywords, "construction");
            countKeywords(content, transportWords, domainKeywords, "transportation");
            countKeywords(content, agriWords, domainKeywords, "agriculture");
        }

        return domainKeywords.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void countKeywords(String content, String[] keywords, Map<String, Integer> domainKeywords, String domain) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                domainKeywords.merge(domain, 1, Integer::sum);
            }
        }
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
            }

            log.debug("Updated context cache for conversation: {}", conversationId);
        } catch (Exception e) {
            log.warn("Failed to update context after interaction: {}", e.getMessage());
        }
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
                    recentTopics.remove(0);
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

            return summary;
        } catch (Exception e) {
            log.error("Failed to get context summary: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    public void clearContextCache(String conversationId) {
        contextCache.remove(conversationId);
        log.debug("Cleared context cache for conversation: {}", conversationId);
    }

    public void clearAllContextCache() {
        contextCache.clear();
        log.info("Cleared all context cache");
    }
}