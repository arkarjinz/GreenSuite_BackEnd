package com.app.greensuitetest.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

@Configuration
public class MongoDBConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String localMongoUri;

    @Value("${spring.data.mongodb.database:mongoGreenSuite}")
    private String localDatabase;

    @Override
    protected String getDatabaseName() {
        return localDatabase;
    }

    @Bean
    @Primary
    @Override
    public MongoClient mongoClient() {
        return MongoClients.create(localMongoUri);
    }

    @Bean(name = "mongoTemplate")
    @Primary
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), getDatabaseName());
    }
}