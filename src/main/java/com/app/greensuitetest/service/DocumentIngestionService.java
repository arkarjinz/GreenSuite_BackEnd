package com.app.greensuitetest.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.tika.TikaDocumentReader;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    @Value("${app.document.ingestion.enabled:true}")
    private boolean ingestionEnabled;

    @Value("${app.document.ingestion.force-reingest:false}")
    private boolean forceReingest;

    @Value("${app.document.ingestion.batch-size:50}")
    private int batchSize;

    @Value("${app.document.ingestion.max-retries:3}")
    private int maxRetries;

    @Value("${app.document.ingestion.chunk-size:1000}")
    private int chunkSize;

    @Value("${app.document.ingestion.chunk-overlap:200}")
    private int chunkOverlap;

    // Document processing statistics
    private final Map<String, Object> ingestionStats = new HashMap<>();
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicInteger totalSkipped = new AtomicInteger(0);

    // Supported file patterns
    private final List<String> supportedPatterns = Arrays.asList(
            "classpath:/docs/*.pdf",
            "classpath:/docs/*.txt",
            "classpath:/docs/*.docx",
            "classpath:/docs/*.doc",
            "classpath:/docs/*.md"
    );

    @PostConstruct
    public void ingestDocuments() {
        if (!ingestionEnabled) {
            log.info("Document ingestion is disabled");
            return;
        }

        log.info("Starting enhanced document ingestion process...");

        try {
            performEnhancedIngestion();
        } catch (Exception e) {
            log.error("Critical error in document ingestion process: {}", e.getMessage(), e);
        }
    }

    private void performEnhancedIngestion() {
        // Initialize statistics
        initializeIngestionStats();

        // Check if documents already exist (unless force reingest is enabled)
        if (!forceReingest && documentsAlreadyExist()) {
            log.info("Documents already exist in vector store. Skipping ingestion. Set app.document.ingestion.force-reingest=true to force re-ingestion.");
            return;
        }

        List<Resource> allResources = collectAllDocuments();
        if (allResources.isEmpty()) {
            log.warn("No documents found for ingestion");
            return;
        }

        log.info("Found {} documents for processing", allResources.size());

        List<Document> allProcessedDocuments = new ArrayList<>();

        // Process documents in batches to manage memory
        for (int i = 0; i < allResources.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, allResources.size());
            List<Resource> batch = allResources.subList(i, endIndex);

            log.info("Processing batch {}/{} ({} documents)",
                    (i / batchSize) + 1,
                    (allResources.size() + batchSize - 1) / batchSize,
                    batch.size());

            List<Document> batchDocuments = processBatch(batch);
            allProcessedDocuments.addAll(batchDocuments);

            // Add batch to vector store immediately to prevent memory issues
            if (!batchDocuments.isEmpty()) {
                addDocumentsToVectorStore(batchDocuments);
                log.info("Added {} documents from current batch to vector store", batchDocuments.size());
            }
        }

        // Log final statistics
        logFinalStatistics();
    }

    private void initializeIngestionStats() {
        ingestionStats.put("start_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        ingestionStats.put("total_files_found", 0);
        ingestionStats.put("successful_files", 0);
        ingestionStats.put("failed_files", 0);
        ingestionStats.put("skipped_files", 0);
        ingestionStats.put("total_chunks_created", 0);
        ingestionStats.put("errors", new ArrayList<String>());
        totalProcessed.set(0);
        totalFailed.set(0);
        totalSkipped.set(0);
    }

    private List<Resource> collectAllDocuments() {
        List<Resource> allResources = new ArrayList<>();

        for (String pattern : supportedPatterns) {
            try {
                Resource[] resources = resourceResolver.getResources(pattern);
                if (resources.length > 0) {
                    allResources.addAll(Arrays.asList(resources));
                    log.debug("Found {} files matching pattern: {}", resources.length, pattern);
                }
            } catch (Exception e) {
                log.warn("Error collecting documents for pattern '{}': {}", pattern, e.getMessage());
                addErrorToStats("Pattern collection error for " + pattern + ": " + e.getMessage());
            }
        }

        ingestionStats.put("total_files_found", allResources.size());
        return allResources;
    }

    private List<Document> processBatch(List<Resource> batch) {
        List<Document> batchDocuments = new ArrayList<>();

        for (Resource resource : batch) {
            try {
                List<Document> documentsFromFile = processDocumentWithRetry(resource);
                if (!documentsFromFile.isEmpty()) {
                    batchDocuments.addAll(documentsFromFile);
                    totalProcessed.incrementAndGet();
                    log.debug("Successfully processed '{}' - extracted {} chunks",
                            resource.getFilename(), documentsFromFile.size());
                } else {
                    log.warn("No content extracted from '{}'", resource.getFilename());
                    totalSkipped.incrementAndGet();
                }
            } catch (Exception e) {
                log.error("Failed to process document '{}' after all retries: {}",
                        resource.getFilename(), e.getMessage());
                totalFailed.incrementAndGet();
                addErrorToStats("Failed to process " + resource.getFilename() + ": " + e.getMessage());
                // Continue processing other files - this is the key improvement
            }
        }

        return batchDocuments;
    }

    private List<Document> processDocumentWithRetry(Resource resource) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return processDocument(resource);
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {}/{} failed for '{}': {}",
                        attempt, maxRetries, resource.getFilename(), e.getMessage());

                if (attempt < maxRetries) {
                    // Wait before retry (exponential backoff)
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Processing interrupted", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed after " + maxRetries + " attempts", lastException);
    }

    private List<Document> processDocument(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Resource has no filename");
        }

        String fileExtension = getFileExtension(filename).toLowerCase();

        try {
            List<Document> rawDocuments = readDocumentBasedOnType(resource, fileExtension);

            if (rawDocuments.isEmpty()) {
                log.warn("No content extracted from '{}'", filename);
                return new ArrayList<>();
            }

            return processAndChunkDocuments(rawDocuments, resource);

        } catch (Exception e) {
            log.error("Error processing document '{}': {}", filename, e.getMessage());
            throw new RuntimeException("Document processing failed for " + filename, e);
        }
    }

    private List<Document> readDocumentBasedOnType(Resource resource, String fileExtension) {
        try {
            switch (fileExtension) {
                case "pdf":
                    return readPdfDocument(resource);
                case "txt":
                case "md":
                    return readTextDocument(resource);
                case "docx":
                case "doc":
                    return readOfficeDocument(resource);
                default:
                    log.warn("Unsupported file type: {} for file: {}", fileExtension, resource.getFilename());
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Failed to read document '{}' of type '{}': {}",
                    resource.getFilename(), fileExtension, e.getMessage());
            throw e;
        }
    }

    private List<Document> readPdfDocument(Resource resource) {
        try {
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(
                            ExtractedTextFormatter.builder()
                                    .withNumberOfTopTextLinesToDelete(0)
                                    .withNumberOfBottomTextLinesToDelete(0)
                                    .build()
                    )
                    .withPagesPerDocument(1)
                    .build();

            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
            return reader.get();
        } catch (Exception e) {
            log.error("Failed to read PDF document '{}': {}", resource.getFilename(), e.getMessage());
            throw new RuntimeException("PDF reading failed", e);
        }
    }

    private List<Document> readTextDocument(Resource resource) {
        try {
            // Use Tika for text documents to handle encoding properly
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            return reader.get();
        } catch (Exception e) {
            log.error("Failed to read text document '{}': {}", resource.getFilename(), e.getMessage());
            throw new RuntimeException("Text document reading failed", e);
        }
    }

    private List<Document> readOfficeDocument(Resource resource) {
        try {
            // Use Tika for Office documents
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            return reader.get();
        } catch (Exception e) {
            log.error("Failed to read Office document '{}': {}", resource.getFilename(), e.getMessage());
            throw new RuntimeException("Office document reading failed", e);
        }
    }

    private List<Document> processAndChunkDocuments(List<Document> rawDocuments, Resource resource) {
        List<Document> processedDocuments = new ArrayList<>();
        String filename = resource.getFilename();

        // Create enhanced text splitter
        TextSplitter splitter = createEnhancedTextSplitter();

        for (Document doc : rawDocuments) {
            try {
                // Clean and preprocess content
                String cleanedContent = preprocessDocumentContent(extractDocumentContent(doc));

                if (!StringUtils.hasText(cleanedContent)) {
                    log.warn("Document from '{}' has no meaningful content after preprocessing", filename);
                    continue;
                }

                // Create a new document with cleaned content
                Document cleanedDoc = new Document(cleanedContent, doc.getMetadata());

                // Split into chunks
                List<Document> chunks = splitter.apply(List.of(cleanedDoc));

                // Enhance metadata for each chunk
                for (int i = 0; i < chunks.size(); i++) {
                    Document chunk = chunks.get(i);
                    enhanceDocumentMetadata(chunk, resource, i, chunks.size());
                    processedDocuments.add(chunk);
                }

            } catch (Exception e) {
                log.warn("Failed to process individual document from '{}': {}", filename, e.getMessage());
                // Continue with other documents in the file
            }
        }

        log.debug("Processed '{}' into {} chunks", filename, processedDocuments.size());
        return processedDocuments;
    }

    private TextSplitter createEnhancedTextSplitter() {
        // Use TokenTextSplitter with the parameterized constructor based on Spring AI documentation
        // TokenTextSplitter(int defaultChunkSize, int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks, boolean keepSeparator)
        return new TokenTextSplitter(
                chunkSize,        // defaultChunkSize - target size of each text chunk in tokens
                50,               // minChunkSizeChars - minimum size of each text chunk in characters
                5,                // minChunkLengthToEmbed - minimum length of a chunk to be included
                10000,            // maxNumChunks - maximum number of chunks to generate
                true              // keepSeparator - whether to keep separators like newlines
        );
    }

    private String preprocessDocumentContent(String content) {
        if (content == null) {
            return "";
        }

        // Clean up common document artifacts
        content = content
                // Remove excessive whitespace
                .replaceAll("\\s+", " ")
                // Remove page headers/footers patterns
                .replaceAll("(?i)page \\d+ of \\d+", "")
                // Remove standalone numbers (likely page numbers)
                .replaceAll("\\n\\d+\\n", "\n")
                // Clean up bullet points
                .replaceAll("•", "- ")
                // Fixed: Properly escape unicode quotes
                .replaceAll("[\u201C\u201D\u201E\u201F\u2033\u2036]", "\"")
                .replaceAll("[\u2018\u2019\u201A\u201B\u2032\u2035]", "'")
                // Remove multiple consecutive newlines
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return content;
    }

    private void enhanceDocumentMetadata(Document document, Resource resource, int chunkIndex, int totalChunks) {
        Map<String, Object> metadata = document.getMetadata();

        // Basic file information
        metadata.put("file_name", resource.getFilename());
        metadata.put("source", resource.getFilename());
        metadata.put("file_type", getFileExtension(resource.getFilename()));

        // Chunk information
        metadata.put("chunk_index", chunkIndex);
        metadata.put("total_chunks", totalChunks);
        metadata.put("chunk_id", resource.getFilename() + "_chunk_" + chunkIndex);

        // Processing information
        metadata.put("ingestion_timestamp", System.currentTimeMillis());
        metadata.put("ingestion_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("content_length", document.getText() != null ? document.getText().length() : 0);

        // Content analysis
        String content = document.getText() != null ? document.getText() : "";
        metadata.put("word_count", content.split("\\s+").length);
        metadata.put("has_numbers", content.matches(".*\\d.*"));
        metadata.put("has_formulas", content.contains("=") || content.matches(".*\\b\\w+\\s*\\(.*\\).*"));

        // Domain classification
        String domain = classifyDocumentDomain(content);
        if (domain != null) {
            metadata.put("domain", domain);
        }

        // Extract key topics
        Set<String> topics = extractKeyTopics(content);
        if (!topics.isEmpty()) {
            metadata.put("topics", new ArrayList<>(topics));
        }
    }

    private String classifyDocumentDomain(String content) {
        String lowerContent = content.toLowerCase();

        if (containsKeywords(lowerContent, Arrays.asList("carbon", "emission", "greenhouse", "co2", "climate"))) {
            return "carbon_management";
        } else if (containsKeywords(lowerContent, Arrays.asList("energy", "renewable", "solar", "wind", "electricity"))) {
            return "energy";
        } else if (containsKeywords(lowerContent, Arrays.asList("waste", "recycling", "circular", "disposal"))) {
            return "waste_management";
        } else if (containsKeywords(lowerContent, Arrays.asList("supply", "chain", "procurement", "vendor"))) {
            return "supply_chain";
        } else if (containsKeywords(lowerContent, Arrays.asList("esg", "sustainability", "reporting", "compliance"))) {
            return "esg_reporting";
        }

        return "general";
    }

    private boolean containsKeywords(String content, List<String> keywords) {
        return keywords.stream().anyMatch(content::contains);
    }

    private Set<String> extractKeyTopics(String content) {
        Set<String> topics = new HashSet<>();
        String lowerContent = content.toLowerCase();

        // Sustainability topics
        String[] sustainabilityTerms = {
                "carbon footprint", "emissions", "renewable energy", "sustainability",
                "esg", "environmental", "green", "climate", "waste", "recycling",
                "circular economy", "supply chain", "biodiversity", "water management"
        };

        for (String term : sustainabilityTerms) {
            if (lowerContent.contains(term)) {
                topics.add(term);
            }
        }

        return topics;
    }

    private void addDocumentsToVectorStore(List<Document> documents) {
        try {
            if (documents.isEmpty()) {
                return;
            }

            vectorStore.add(documents);
            ingestionStats.put("total_chunks_created",
                    (Integer) ingestionStats.get("total_chunks_created") + documents.size());

            log.debug("Successfully added {} documents to vector store", documents.size());
        } catch (Exception e) {
            log.error("Failed to add documents to vector store: {}", e.getMessage(), e);
            addErrorToStats("Vector store addition failed: " + e.getMessage());
            throw new RuntimeException("Vector store addition failed", e);
        }
    }

    private String extractDocumentContent(Document doc) {
        if (doc == null) {
            return "";
        }

        try {
            // Use getText() method from Document class
            return doc.getText() != null ? doc.getText().trim() : "";
        } catch (Exception e) {
            log.warn("Failed to extract content from document: {}", e.getMessage());
            return "";
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private boolean documentsAlreadyExist() {
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("test")
                            .topK(1)
                            .build()
            );
            boolean hasDocuments = results != null && !results.isEmpty();
            log.debug("Vector store contains documents: {}", hasDocuments);
            return hasDocuments;
        } catch (Exception e) {
            log.debug("Could not check if documents exist in vector store: {}", e.getMessage());
            return false;
        }
    }

    private void addErrorToStats(String error) {
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) ingestionStats.get("errors");
        errors.add(error);
    }

    private void logFinalStatistics() {
        ingestionStats.put("end_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        ingestionStats.put("successful_files", totalProcessed.get());
        ingestionStats.put("failed_files", totalFailed.get());
        ingestionStats.put("skipped_files", totalSkipped.get());

        log.info("=== Document Ingestion Complete ===");
        log.info("Total files found: {}", ingestionStats.get("total_files_found"));
        log.info("Successfully processed: {}", totalProcessed.get());
        log.info("Failed files: {}", totalFailed.get());
        log.info("Skipped files: {}", totalSkipped.get());
        log.info("Total chunks created: {}", ingestionStats.get("total_chunks_created"));

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) ingestionStats.get("errors");
        if (!errors.isEmpty()) {
            log.warn("Errors encountered during ingestion:");
            errors.forEach(error -> log.warn("  - {}", error));
        }
    }

    // Public methods for manual control and monitoring

    @Async
    public CompletableFuture<Map<String, Object>> forceReingestDocumentsAsync() {
        log.info("Starting asynchronous document re-ingestion...");
        boolean originalForceReingest = this.forceReingest;
        this.forceReingest = true;

        try {
            performEnhancedIngestion();
            return CompletableFuture.completedFuture(getIngestionStats());
        } catch (Exception e) {
            log.error("Async re-ingestion failed: {}", e.getMessage(), e);
            Map<String, Object> errorStats = new HashMap<>(ingestionStats);
            errorStats.put("error", e.getMessage());
            return CompletableFuture.completedFuture(errorStats);
        } finally {
            this.forceReingest = originalForceReingest;
        }
    }

    public void forceReingestDocuments() {
        log.info("Forcing synchronous document re-ingestion...");
        boolean originalForceReingest = this.forceReingest;
        this.forceReingest = true;
        try {
            performEnhancedIngestion();
        } finally {
            this.forceReingest = originalForceReingest;
        }
    }

    public Map<String, Object> getIngestionStats() {
        Map<String, Object> currentStats = new HashMap<>(ingestionStats);
        currentStats.put("current_processed", totalProcessed.get());
        currentStats.put("current_failed", totalFailed.get());
        currentStats.put("current_skipped", totalSkipped.get());
        return currentStats;
    }

    public boolean isIngestionEnabled() {
        return ingestionEnabled;
    }

    public int getDocumentCount() {
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("*")
                            .topK(1000) // Get more documents to count
                            .build()
            );
            return results != null ? results.size() : 0;
        } catch (Exception e) {
            log.warn("Could not get document count: {}", e.getMessage());
            return -1;
        }
    }

    public Map<String, Object> getDetailedVectorStoreInfo() {
        Map<String, Object> info = new HashMap<>();

        try {
            // Get sample documents to analyze metadata
            List<Document> sampleDocs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("sustainability")
                            .topK(10)
                            .build()
            );

            info.put("total_documents", getDocumentCount());
            info.put("sample_size", sampleDocs.size());

            if (!sampleDocs.isEmpty()) {
                // Analyze document types
                Map<String, Integer> typeCount = new HashMap<>();
                Map<String, Integer> domainCount = new HashMap<>();

                for (Document doc : sampleDocs) {
                    String fileType = (String) doc.getMetadata().get("file_type");
                    if (fileType != null) {
                        typeCount.merge(fileType, 1, Integer::sum);
                    }

                    String domain = (String) doc.getMetadata().get("domain");
                    if (domain != null) {
                        domainCount.merge(domain, 1, Integer::sum);
                    }
                }

                info.put("document_types", typeCount);
                info.put("domain_distribution", domainCount);
                info.put("sample_metadata_keys", sampleDocs.get(0).getMetadata().keySet());
            }

        } catch (Exception e) {
            log.warn("Could not get detailed vector store info: {}", e.getMessage());
            info.put("error", e.getMessage());
        }

        return info;
    }
}