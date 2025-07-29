package com.app.greensuitetest.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mongodb.atlas.MongoDBAtlasVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.mongodb.atlas.uri}")
    private String mongoAtlasUri;

    @Value("${spring.ai.vectorstore.mongodb.atlas.database}")
    private String mongoAtlasDatabase;

    @Value("${spring.ai.vectorstore.mongodb.collection-name:green_suite-collect}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.mongodb.index-name:vector_index}")
    private String indexName;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.embedding.options.model:mxbai-embed-large}")
    private String embeddingModel;

    @Bean(name = "atlasMongoClient")
    public MongoClient atlasMongoClient() {
        return MongoClients.create(mongoAtlasUri);
    }

    @Bean(name = "atlasMongoTemplate")
    public MongoTemplate atlasMongoTemplate() {
        return new MongoTemplate(atlasMongoClient(), mongoAtlasDatabase);
    }

    @Bean(name = "vectorStore")
    public VectorStore vectorStore() {
        return MongoDBAtlasVectorStore.builder(atlasMongoTemplate(), embeddingModel())
                .collectionName(collectionName)
                .vectorIndexName(indexName)
                .numCandidates(200)
                .initializeSchema(true)
                .build();
    }

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel() {
        try {
            return OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl(ollamaBaseUrl)
                            .build())
                    .defaultOptions(OllamaOptions.builder()
                            .model(embeddingModel)
                            .build())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .modelManagementOptions(ModelManagementOptions.defaults())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OllamaEmbeddingModel. Make sure Ollama is running and " + embeddingModel + " model is available.", e);
        }
    }

}