package com.app.greensuitetest.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    @Value("classpath:/docs/*.pdf")
    private Resource[] pdfResources;

    @Value("${app.document.ingestion.enabled:true}")
    private boolean ingestionEnabled;

    @Value("${app.document.ingestion.force-reingest:false}")
    private boolean forceReingest;

    @PostConstruct
    public void ingestDocuments() {
        if (!ingestionEnabled) {
            log.info("Document ingestion is disabled");
            return;
        }

        // Check if documents already exist (unless force reingest is enabled)
        if (!forceReingest && documentsAlreadyExist()) {
            log.info("Documents already exist in vector store. Skipping ingestion. Set app.document.ingestion.force-reingest=true to force re-ingestion.");
            return;
        }

        log.info("Starting document ingestion for {} PDF files", pdfResources.length);

        List<Document> allDocuments = new ArrayList<>();
        int successfulFiles = 0;
        int failedFiles = 0;

        for (Resource resource : pdfResources) {
            try {
                List<Document> documentsFromFile = processDocument(resource);
                if (!documentsFromFile.isEmpty()) {
                    allDocuments.addAll(documentsFromFile);
                    successfulFiles++;
                    log.info("Successfully processed '{}' - extracted {} chunks",
                            resource.getFilename(), documentsFromFile.size());
                } else {
                    log.warn("No content extracted from '{}'", resource.getFilename());
                    failedFiles++;
                }
            } catch (Exception e) {
                log.error("Failed to process PDF '{}': {}", resource.getFilename(), e.getMessage());
                failedFiles++;
                // Continue processing other files
            }
        }

        if (!allDocuments.isEmpty()) {
            try {
                vectorStore.add(allDocuments);
                log.info("Successfully ingested {} document chunks from {} files into vector store. Failed files: {}",
                        allDocuments.size(), successfulFiles, failedFiles);
            } catch (Exception e) {
                log.error("Failed to add documents to vector store: {}", e.getMessage(), e);
            }
        } else {
            log.warn("No documents were successfully processed for ingestion");
        }
    }

    private List<Document> processDocument(Resource resource) {
        try {
            // Build a fresh config per resource
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(
                            ExtractedTextFormatter.builder()
                                    .withNumberOfTopTextLinesToDelete(0)
                                    .build()
                    )
                    .withPagesPerDocument(1)
                    .build();

            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);

            // Read documents and handle potential errors
            List<Document> rawDocuments;
            try {
                rawDocuments = reader.get();
            } catch (Exception e) {
                log.warn("Failed to read PDF '{}': {}", resource.getFilename(), e.getMessage());
                return new ArrayList<>();
            }

            if (rawDocuments.isEmpty()) {
                log.warn("No pages extracted from PDF '{}'", resource.getFilename());
                return new ArrayList<>();
            }

            // Split into chunks and add metadata
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> processedDocuments = new ArrayList<>();

            for (Document doc : rawDocuments) {
                try {
                    List<Document> chunks = splitter.apply(List.of(doc));
                    for (Document chunk : chunks) {
                        // Add metadata
                        chunk.getMetadata().put("file_name", resource.getFilename());
                        chunk.getMetadata().put("source", resource.getFilename());
                        chunk.getMetadata().put("ingestion_timestamp", System.currentTimeMillis());
                        processedDocuments.add(chunk);
                    }
                } catch (Exception e) {
                    log.warn("Failed to split document from '{}': {}", resource.getFilename(), e.getMessage());
                    // Continue with other documents
                }
            }

            return processedDocuments;
        } catch (Exception e) {
            log.error("Unexpected error processing '{}': {}", resource.getFilename(), e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean documentsAlreadyExist() {
        try {
            // Try to search for any documents to see if the vector store has content
            List<Document> results = vectorStore.similaritySearch("test");
            boolean hasDocuments = results != null && !results.isEmpty();
            log.debug("Vector store contains documents: {}", hasDocuments);
            return hasDocuments;
        } catch (Exception e) {
            log.debug("Could not check if documents exist in vector store: {}", e.getMessage());
            // If we can't check, assume they don't exist and proceed with ingestion
            return false;
        }
    }

    /**
     * Manual method to force re-ingestion of documents
     */
    public void forceReingestDocuments() {
        log.info("Forcing document re-ingestion...");
        boolean originalForceReingest = this.forceReingest;
        this.forceReingest = true;
        try {
            ingestDocuments();
        } finally {
            this.forceReingest = originalForceReingest;
        }
    }

    /**
     * Get ingestion status
     */
    public boolean isIngestionEnabled() {
        return ingestionEnabled;
    }

    /**
     * Get document count in vector store
     */
    public int getDocumentCount() {
        try {
            List<Document> results = vectorStore.similaritySearch("*");
            return results != null ? results.size() : 0;
        } catch (Exception e) {
            log.warn("Could not get document count: {}", e.getMessage());
            return -1;
        }
    }
}