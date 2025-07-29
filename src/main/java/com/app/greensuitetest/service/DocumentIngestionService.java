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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    @Value("classpath:/docs/*.pdf")
    private Resource[] pdfResources;

    @PostConstruct
    public void ingestDocuments() {
        List<Document> documents = Arrays.stream(pdfResources)
                .flatMap(resource -> {
                    try {
                        // build a fresh config per‐resource
                        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                                .withPageExtractedTextFormatter(
                                        ExtractedTextFormatter.builder()
                                                .withNumberOfTopTextLinesToDelete(0)
                                                .build()
                                )
                                .withPagesPerDocument(1)
                                .build();

                        PagePdfDocumentReader reader =
                                new PagePdfDocumentReader(resource, config);

                        // read & split into chunks
                        return new TokenTextSplitter().apply(reader.get()).stream()
                                .peek(doc -> doc.getMetadata()
                                        .put("file_name", resource.getFilename()));
                    } catch (Exception e) {
                        // catch any PDFBox or reader error, log, and skip this file
                        log.warn("Skipping PDF '{}' due to extraction error: {}",
                                resource.getFilename(),
                                e.getMessage());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());

        vectorStore.add(documents);
        log.info("Ingested {} document chunks into vector store", documents.size());
    }
}
