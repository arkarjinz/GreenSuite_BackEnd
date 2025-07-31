package com.app.greensuitetest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentContextService {

    // Patterns for identifying different types of content
    private final Pattern formulaPattern = Pattern.compile("\\b\\w+\\s*=\\s*[\\w\\s+\\-*/().,]+");
    private final Pattern numberPattern = Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:%|kg|tons?|kwh|mwh|gwh|m[²³]?|ft[²³]?|gal|l|°[cf])?");
    private final Pattern procedurePattern = Pattern.compile("(?i)(?:step|procedure|process|method|approach)\\s*\\d*:?\\s*(.+)");

    // Domain-specific keywords for content classification
    private final Map<String, List<String>> domainKeywords = Map.of(
            "carbon_calculation", Arrays.asList("carbon", "co2", "emission", "ghg", "scope 1", "scope 2", "scope 3"),
            "energy_management", Arrays.asList("energy", "renewable", "efficiency", "consumption", "solar", "wind"),
            "waste_management", Arrays.asList("waste", "recycling", "disposal", "circular", "landfill"),
            "water_management", Arrays.asList("water", "consumption", "treatment", "wastewater", "conservation"),
            "supply_chain", Arrays.asList("supply", "chain", "procurement", "vendor", "supplier", "logistics"),
            "reporting", Arrays.asList("report", "disclosure", "compliance", "audit", "certification", "standard")
    );

    public String buildIntelligentContext(List<Document> documents, String userQuery) {
        if (documents == null || documents.isEmpty()) {
            return "No relevant context found.";
        }

        try {
            // Analyze user query to understand intent
            QueryAnalysis queryAnalysis = analyzeQuery(userQuery);

            // Filter and rank documents based on relevance
            List<EnhancedDocument> enhancedDocs = documents.stream()
                    .map(this::enhanceDocument)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Rank documents by relevance to query
            enhancedDocs = rankDocumentsByRelevance(enhancedDocs, queryAnalysis);

            // Build context based on query type and document content
            return buildContextForQueryType(enhancedDocs, queryAnalysis);

        } catch (Exception e) {
            log.error("Error building intelligent context: {}", e.getMessage(), e);
            return buildFallbackContext(documents);
        }
    }

    private QueryAnalysis analyzeQuery(String query) {
        QueryAnalysis analysis = new QueryAnalysis();
        String lowerQuery = query.toLowerCase();

        // Determine query type
        if (lowerQuery.contains("calculate") || lowerQuery.contains("compute") || lowerQuery.contains("formula")) {
            analysis.type = QueryType.CALCULATION;
        } else if (lowerQuery.contains("explain") || lowerQuery.contains("what is") || lowerQuery.contains("define")) {
            analysis.type = QueryType.EXPLANATION;
        } else if (lowerQuery.contains("how to") || lowerQuery.contains("process") || lowerQuery.contains("steps")) {
            analysis.type = QueryType.PROCEDURE;
        } else if (lowerQuery.contains("compare") || lowerQuery.contains("difference") || lowerQuery.contains("versus")) {
            analysis.type = QueryType.COMPARISON;
        } else if (lowerQuery.contains("recommend") || lowerQuery.contains("suggest") || lowerQuery.contains("best")) {
            analysis.type = QueryType.RECOMMENDATION;
        } else {
            analysis.type = QueryType.GENERAL;
        }

        // Extract key concepts
        analysis.concepts = extractKeyConcepts(query);

        // Determine domain focus
        analysis.domain = determineDomainFocus(query);

        // Analyze complexity
        analysis.complexity = analyzeQueryComplexity(query);

        log.debug("Query analysis: type={}, domain={}, complexity={}, concepts={}",
                analysis.type, analysis.domain, analysis.complexity, analysis.concepts);

        return analysis;
    }

    private List<String> extractKeyConcepts(String query) {
        List<String> concepts = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        // Extract sustainability concepts
        for (Map.Entry<String, List<String>> domainEntry : domainKeywords.entrySet()) {
            for (String keyword : domainEntry.getValue()) {
                if (lowerQuery.contains(keyword.toLowerCase())) {
                    concepts.add(keyword);
                }
            }
        }

        // Extract numerical concepts
        Matcher numberMatcher = numberPattern.matcher(query);
        while (numberMatcher.find()) {
            concepts.add("numerical_data");
            break; // Only add once
        }

        return concepts.stream().distinct().collect(Collectors.toList());
    }

    private String determineDomainFocus(String query) {
        String lowerQuery = query.toLowerCase();
        Map<String, Integer> domainScores = new HashMap<>();

        for (Map.Entry<String, List<String>> domainEntry : domainKeywords.entrySet()) {
            int score = 0;
            for (String keyword : domainEntry.getValue()) {
                if (lowerQuery.contains(keyword.toLowerCase())) {
                    score++;
                }
            }
            if (score > 0) {
                domainScores.put(domainEntry.getKey(), score);
            }
        }

        return domainScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("general");
    }

    private QueryComplexity analyzeQueryComplexity(String query) {
        String[] words = query.split("\\s+");
        int wordCount = words.length;
        int questionMarks = query.length() - query.replace("?", "").length();
        int commas = query.length() - query.replace(",", "").length();

        // Check for complex indicators
        boolean hasMultipleConcepts = extractKeyConcepts(query).size() > 2;
        boolean hasComparisons = query.toLowerCase().contains("compare") || query.toLowerCase().contains("versus");
        boolean hasCalculations = query.toLowerCase().contains("calculate") && query.toLowerCase().contains("and");

        if (wordCount > 30 || questionMarks > 2 || commas > 3 || hasMultipleConcepts || hasComparisons || hasCalculations) {
            return QueryComplexity.COMPLEX;
        } else if (wordCount > 15 || questionMarks > 1 || commas > 1) {
            return QueryComplexity.MODERATE;
        } else {
            return QueryComplexity.SIMPLE;
        }
    }

    private EnhancedDocument enhanceDocument(Document document) {
        try {
            EnhancedDocument enhanced = new EnhancedDocument();
            enhanced.originalDocument = document;
            enhanced.content = extractDocumentContent(document);
            enhanced.metadata = document.getMetadata();

            if (!StringUtils.hasText(enhanced.content)) {
                return null;
            }

            // Analyze content
            enhanced.contentType = analyzeContentType(enhanced.content);
            enhanced.domain = classifyContentDomain(enhanced.content);
            enhanced.hasFormulas = formulaPattern.matcher(enhanced.content).find();
            enhanced.hasNumbers = numberPattern.matcher(enhanced.content).find();
            enhanced.hasProcedures = procedurePattern.matcher(enhanced.content).find();
            enhanced.wordCount = enhanced.content.split("\\s+").length;
            enhanced.keyTerms = extractKeyTerms(enhanced.content);

            return enhanced;
        } catch (Exception e) {
            log.warn("Failed to enhance document: {}", e.getMessage());
            return null;
        }
    }

    private ContentType analyzeContentType(String content) {
        String lowerContent = content.toLowerCase();

        if (formulaPattern.matcher(content).find() && numberPattern.matcher(content).find()) {
            return ContentType.CALCULATION;
        } else if (procedurePattern.matcher(content).find() ||
                lowerContent.contains("step") || lowerContent.contains("process")) {
            return ContentType.PROCEDURE;
        } else if (lowerContent.contains("definition") || lowerContent.contains("means") ||
                lowerContent.contains("refers to")) {
            return ContentType.DEFINITION;
        } else if (lowerContent.contains("example") || lowerContent.contains("case study")) {
            return ContentType.EXAMPLE;
        } else if (lowerContent.contains("standard") || lowerContent.contains("requirement") ||
                lowerContent.contains("compliance")) {
            return ContentType.STANDARD;
        } else {
            return ContentType.GENERAL;
        }
    }

    private String classifyContentDomain(String content) {
        String lowerContent = content.toLowerCase();
        Map<String, Integer> domainScores = new HashMap<>();

        for (Map.Entry<String, List<String>> domainEntry : domainKeywords.entrySet()) {
            int score = 0;
            for (String keyword : domainEntry.getValue()) {
                // Count occurrences of each keyword
                int count = lowerContent.split(keyword.toLowerCase(), -1).length - 1;
                score += count;
            }
            if (score > 0) {
                domainScores.put(domainEntry.getKey(), score);
            }
        }

        return domainScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("general");
    }

    private Set<String> extractKeyTerms(String content) {
        Set<String> keyTerms = new HashSet<>();
        String lowerContent = content.toLowerCase();

        // Extract all domain keywords present
        for (List<String> keywords : domainKeywords.values()) {
            for (String keyword : keywords) {
                if (lowerContent.contains(keyword.toLowerCase())) {
                    keyTerms.add(keyword);
                }
            }
        }

        // Extract numerical values and units
        Matcher numberMatcher = numberPattern.matcher(content);
        while (numberMatcher.find() && keyTerms.size() < 20) { // Limit to prevent overload
            keyTerms.add("numerical_value");
            break; // Just indicate presence, don't extract all numbers
        }

        return keyTerms;
    }

    private List<EnhancedDocument> rankDocumentsByRelevance(List<EnhancedDocument> documents, QueryAnalysis query) {
        return documents.stream()
                .map(doc -> {
                    doc.relevanceScore = calculateRelevanceScore(doc, query);
                    return doc;
                })
                .sorted((d1, d2) -> Double.compare(d2.relevanceScore, d1.relevanceScore))
                .collect(Collectors.toList());
    }

    private double calculateRelevanceScore(EnhancedDocument document, QueryAnalysis query) {
        double score = 0.0;

        // Content type matching
        if (matchesQueryType(document.contentType, query.type)) {
            score += 5.0;
        }

        // Domain matching
        if (document.domain.equals(query.domain)) {
            score += 3.0;
        }

        // Concept overlap
        long conceptOverlap = document.keyTerms.stream()
                .mapToLong(term -> query.concepts.contains(term) ? 1 : 0)
                .sum();
        score += conceptOverlap * 2.0;

        // Content type bonuses
        if (query.type == QueryType.CALCULATION && document.hasFormulas) {
            score += 4.0;
        }
        if (query.type == QueryType.PROCEDURE && document.hasProcedures) {
            score += 4.0;
        }
        if (document.hasNumbers && query.concepts.contains("numerical_data")) {
            score += 2.0;
        }

        // Recency bonus (if metadata available)
        Long timestamp = (Long) document.metadata.get("ingestion_timestamp");
        if (timestamp != null) {
            long daysSinceIngestion = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60 * 24);
            if (daysSinceIngestion < 30) {
                score += 1.0; // Bonus for recent content
            }
        }

        // Content quality indicators
        if (document.wordCount > 100 && document.wordCount < 2000) {
            score += 1.0; // Sweet spot for content length
        }

        return score;
    }

    private boolean matchesQueryType(ContentType contentType, QueryType queryType) {
        switch (queryType) {
            case CALCULATION:
                return contentType == ContentType.CALCULATION;
            case EXPLANATION:
                return contentType == ContentType.DEFINITION || contentType == ContentType.GENERAL;
            case PROCEDURE:
                return contentType == ContentType.PROCEDURE;
            case COMPARISON:
                return contentType == ContentType.EXAMPLE || contentType == ContentType.GENERAL;
            case RECOMMENDATION:
                return contentType == ContentType.EXAMPLE || contentType == ContentType.STANDARD;
            default:
                return true; // General queries can use any content type
        }
    }

    private String buildContextForQueryType(List<EnhancedDocument> documents, QueryAnalysis query) {
        StringBuilder context = new StringBuilder();

        // Limit documents based on query complexity
        int maxDocs = switch (query.complexity) {
            case COMPLEX -> 6;
            case MODERATE -> 4;
            case SIMPLE -> 3;
        };

        List<EnhancedDocument> selectedDocs = documents.stream()
                .limit(maxDocs)
                .collect(Collectors.toList());

        if (selectedDocs.isEmpty()) {
            return "No relevant context found.";
        }

        // Build context based on query type
        switch (query.type) {
            case CALCULATION:
                context.append("CALCULATION RESOURCES:\n");
                buildCalculationContext(context, selectedDocs);
                break;
            case PROCEDURE:
                context.append("PROCEDURAL GUIDANCE:\n");
                buildProceduralContext(context, selectedDocs);
                break;
            case EXPLANATION:
                context.append("EXPLANATORY RESOURCES:\n");
                buildExplanationContext(context, selectedDocs);
                break;
            case COMPARISON:
                context.append("COMPARATIVE INFORMATION:\n");
                buildComparisonContext(context, selectedDocs);
                break;
            case RECOMMENDATION:
                context.append("RECOMMENDATION SOURCES:\n");
                buildRecommendationContext(context, selectedDocs);
                break;
            default:
                context.append("RELEVANT RESOURCES:\n");
                buildGeneralContext(context, selectedDocs);
        }

        // Add summary of sources
        context.append("\n--- SOURCE SUMMARY ---\n");
        Set<String> sources = selectedDocs.stream()
                .map(doc -> (String) doc.metadata.get("file_name"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        context.append("Sources consulted: ").append(String.join(", ", sources));

        return context.toString();
    }

    private void buildCalculationContext(StringBuilder context, List<EnhancedDocument> documents) {
        for (EnhancedDocument doc : documents) {
            String source = (String) doc.metadata.getOrDefault("file_name", "Knowledge Base");
            context.append("\nFrom: ").append(source).append("\n");

            if (doc.hasFormulas) {
                // Extract and highlight formulas
                String formulaContent = extractFormulas(doc.content);
                if (!formulaContent.isEmpty()) {
                    context.append("Formulas and Calculations:\n").append(formulaContent).append("\n");
                }
            }

            // Add relevant content with emphasis on numerical data
            String relevantContent = extractRelevantContent(doc.content, 400);
            context.append("Context: ").append(relevantContent).append("\n");

            if (doc.hasNumbers) {
                String numericalData = extractNumericalData(doc.content);
                if (!numericalData.isEmpty()) {
                    context.append("Key Values: ").append(numericalData).append("\n");
                }
            }

            context.append("---\n");
        }
    }

    private void buildProceduralContext(StringBuilder context, List<EnhancedDocument> documents) {
        for (EnhancedDocument doc : documents) {
            String source = (String) doc.metadata.getOrDefault("file_name", "Knowledge Base");
            context.append("\nFrom: ").append(source).append("\n");

            if (doc.hasProcedures) {
                String procedures = extractProcedures(doc.content);
                if (!procedures.isEmpty()) {
                    context.append("Procedures:\n").append(procedures).append("\n");
                }
            }

            String relevantContent = extractRelevantContent(doc.content, 500);
            context.append("Guidance: ").append(relevantContent).append("\n---\n");
        }
    }

    private void buildExplanationContext(StringBuilder context, List<EnhancedDocument> documents) {
        for (EnhancedDocument doc : documents) {
            String source = (String) doc.metadata.getOrDefault("file_name", "Knowledge Base");
            context.append("\nFrom: ").append(source).append("\n");

            String relevantContent = extractRelevantContent(doc.content, 600);
            context.append("Information: ").append(relevantContent).append("\n");

            // Add key terms for context
            if (!doc.keyTerms.isEmpty()) {
                context.append("Key Concepts: ").append(String.join(", ", doc.keyTerms)).append("\n");
            }

            context.append("---\n");
        }
    }

    private void buildComparisonContext(StringBuilder context, List<EnhancedDocument> documents) {
        Map<String, List<EnhancedDocument>> domainGroups = documents.stream()
                .collect(Collectors.groupingBy(doc -> doc.domain));

        for (Map.Entry<String, List<EnhancedDocument>> group : domainGroups.entrySet()) {
            context.append("\n").append(group.getKey().toUpperCase().replace("_", " ")).append(":\n");

            for (EnhancedDocument doc : group.getValue()) {
                String source = (String) doc.metadata.getOrDefault("file_name", "Knowledge Base");
                String relevantContent = extractRelevantContent(doc.content, 300);
                context.append("• From ").append(source).append(": ").append(relevantContent).append("\n");
            }
        }
    }

    private void buildRecommendationContext(StringBuilder context, List<EnhancedDocument> documents) {
        for (EnhancedDocument doc : documents) {
            String source = (String) doc.metadata.getOrDefault("file_name", "Knowledge Base");
            context.append("\nFrom: ").append(source).append("\n");

            // Look for recommendation indicators
            String recommendations = extractRecommendations(doc.content);
            if (!recommendations.isEmpty()) {
                context.append("Recommendations:\n").append(recommendations).append("\n");
            }

            String relevantContent = extractRelevantContent(doc.content, 400);
            context.append("Supporting Information: ").append(relevantContent).append("\n---\n");
        }
    }

    private void buildGeneralContext(StringBuilder context, List<EnhancedDocument> documents) {
        for (EnhancedDocument doc : documents) {
            String source = (String) doc.metadata.getOrDefault("file_name", "Knowledge Base");
            context.append("\nFrom: ").append(source).append("\n");

            String relevantContent = extractRelevantContent(doc.content, 500);
            context.append("Content: ").append(relevantContent).append("\n---\n");
        }
    }

    private String extractFormulas(String content) {
        StringBuilder formulas = new StringBuilder();
        Matcher matcher = formulaPattern.matcher(content);

        while (matcher.find() && formulas.length() < 500) {
            formulas.append("• ").append(matcher.group().trim()).append("\n");
        }

        return formulas.toString();
    }

    private String extractNumericalData(String content) {
        StringBuilder numbers = new StringBuilder();
        Matcher matcher = numberPattern.matcher(content);
        Set<String> uniqueNumbers = new HashSet<>();

        while (matcher.find() && uniqueNumbers.size() < 10) {
            String number = matcher.group().trim();
            if (uniqueNumbers.add(number)) {
                numbers.append(number).append(", ");
            }
        }

        String result = numbers.toString();
        return result.endsWith(", ") ? result.substring(0, result.length() - 2) : result;
    }

    private String extractProcedures(String content) {
        StringBuilder procedures = new StringBuilder();
        Matcher matcher = procedurePattern.matcher(content);

        while (matcher.find() && procedures.length() < 800) {
            procedures.append("• ").append(matcher.group().trim()).append("\n");
        }

        // Also look for numbered lists
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().matches("^\\d+\\.\\s+.+") && procedures.length() < 800) {
                procedures.append("• ").append(line.trim()).append("\n");
            }
        }

        return procedures.toString();
    }

    private String extractRecommendations(String content) {
        StringBuilder recommendations = new StringBuilder();
        String[] recommendationIndicators = {
                "recommend", "suggest", "should", "best practice", "advised", "optimal"
        };

        String[] sentences = content.split("\\.");
        for (String sentence : sentences) {
            String lowerSentence = sentence.toLowerCase();
            for (String indicator : recommendationIndicators) {
                if (lowerSentence.contains(indicator) && recommendations.length() < 600) {
                    recommendations.append("• ").append(sentence.trim()).append(".\n");
                    break;
                }
            }
        }

        return recommendations.toString();
    }

    private String extractRelevantContent(String content, int maxLength) {
        if (content == null || content.trim().isEmpty()) {
            return "No content available";
        }

        // Clean and truncate content
        String cleaned = content.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\n+", " ");

        if (cleaned.length() <= maxLength) {
            return cleaned;
        }

        // Try to cut at sentence boundary
        String truncated = cleaned.substring(0, maxLength);
        int lastPeriod = truncated.lastIndexOf('.');
        if (lastPeriod > maxLength * 0.7) { // If we can cut at a reasonable sentence boundary
            return truncated.substring(0, lastPeriod + 1);
        } else {
            return truncated + "...";
        }
    }

    private String extractDocumentContent(Document doc) {
        if (doc == null) {
            return "";
        }

        try {
            // Try different methods to get document content
            for (String methodName : Arrays.asList("getContent", "getText")) {
                try {
                    Object content = doc.getClass().getMethod(methodName).invoke(doc);
                    return content != null ? content.toString().trim() : "";
                } catch (Exception ignored) {
                    // Try next method
                }
            }
            return doc.toString();
        } catch (Exception e) {
            log.warn("Failed to extract content from document: {}", e.getMessage());
            return "";
        }
    }

    private String buildFallbackContext(List<Document> documents) {
        StringBuilder fallbackContext = new StringBuilder("AVAILABLE RESOURCES:\n");

        for (int i = 0; i < Math.min(documents.size(), 5); i++) {
            Document doc = documents.get(i);
            try {
                String content = extractDocumentContent(doc);
                String source = (String) doc.getMetadata().getOrDefault("file_name", "Knowledge Base");

                fallbackContext.append("\nFrom: ").append(source).append("\n");
                fallbackContext.append("Content: ").append(extractRelevantContent(content, 300)).append("\n---\n");
            } catch (Exception e) {
                log.warn("Error in fallback context building for document: {}", e.getMessage());
            }
        }

        return fallbackContext.toString();
    }

    // Inner classes for analysis
    private static class QueryAnalysis {
        QueryType type;
        List<String> concepts = new ArrayList<>();
        String domain;
        QueryComplexity complexity;
    }

    private static class EnhancedDocument {
        Document originalDocument;
        String content;
        Map<String, Object> metadata;
        ContentType contentType;
        String domain;
        boolean hasFormulas;
        boolean hasNumbers;
        boolean hasProcedures;
        int wordCount;
        Set<String> keyTerms = new HashSet<>();
        double relevanceScore;
    }

    private enum QueryType {
        CALCULATION, EXPLANATION, PROCEDURE, COMPARISON, RECOMMENDATION, GENERAL
    }

    private enum QueryComplexity {
        SIMPLE, MODERATE, COMPLEX
    }

    private enum ContentType {
        CALCULATION, PROCEDURE, DEFINITION, EXAMPLE, STANDARD, GENERAL
    }
}