package com.app.greensuitetest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Configuration
public class DatabaseOptimizationConfig {

    /**
     * Database optimization runner that creates indexes and optimizes performance
     * Runs on application startup (except in test profile)
     */
    @Bean
    @Profile("!test")
    public CommandLineRunner databaseOptimizationRunner(
            JdbcTemplate postgresJdbcTemplate,
            MongoTemplate mongoLocalTemplate) {
        
        return args -> {
            log.info("Starting database optimization...");
            
            try {
                // Optimize PostgreSQL
                optimizePostgresDatabase(postgresJdbcTemplate);
                
                // Optimize MongoDB Local
                optimizeMongoLocalDatabase(mongoLocalTemplate);
                
                log.info("Database optimization completed successfully!");
                
            } catch (Exception e) {
                log.warn("Database optimization failed: {}", e.getMessage());
            }
        };
    }

    private void optimizePostgresDatabase(JdbcTemplate jdbcTemplate) {
        try {
            log.info("Optimizing PostgreSQL database...");
            
            // Check if chat_memory table exists before creating indexes
            try {
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_memory LIMIT 1", Integer.class);
                
                // Create indexes for chat memory tables if they don't exist
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_conversation_id ON chat_memory (conversation_id)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_timestamp ON chat_memory (timestamp)");
                
                log.info("PostgreSQL indexes created successfully");
                
            } catch (Exception tableException) {
                log.info("Chat memory table not found, skipping index creation: {}", tableException.getMessage());
            }
            
            // Set PostgreSQL performance parameters (session-level only)
            try {
                jdbcTemplate.execute("SET enable_seqscan = off");
                jdbcTemplate.execute("SET random_page_cost = 1.1");
                jdbcTemplate.execute("SET effective_cache_size = '1GB'");
                jdbcTemplate.execute("SET work_mem = '64MB'");
                log.info("PostgreSQL performance parameters set");
            } catch (Exception paramException) {
                log.warn("Could not set PostgreSQL performance parameters: {}", paramException.getMessage());
            }
            
            log.info("PostgreSQL optimization completed");
            
        } catch (Exception e) {
            log.warn("PostgreSQL optimization failed: {}", e.getMessage());
        }
    }

    private void optimizeMongoLocalDatabase(MongoTemplate mongoTemplate) {
        try {
            log.info("Optimizing MongoDB Local database...");
            
            // Create indexes for users collection (with error handling for existing indexes)
            try {
                mongoTemplate.indexOps("users").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("email", org.springframework.data.domain.Sort.Direction.ASC)
                        .unique()
                );
                log.info("Users email index created/verified");
            } catch (Exception e) {
                log.info("Users email index already exists or failed: {}", e.getMessage());
            }
            
            try {
                mongoTemplate.indexOps("users").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("user_name", org.springframework.data.domain.Sort.Direction.ASC)
                        .unique()
                );
                log.info("Users username index created/verified");
            } catch (Exception e) {
                log.info("Users username index already exists or failed: {}", e.getMessage());
            }
            
            try {
                mongoTemplate.indexOps("users").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("company_id", org.springframework.data.domain.Sort.Direction.ASC)
                );
                log.info("Users company_id index created/verified");
            } catch (Exception e) {
                log.info("Users company_id index already exists or failed: {}", e.getMessage());
            }
            
            // Create indexes for companies collection
            try {
                mongoTemplate.indexOps("companies").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("name", org.springframework.data.domain.Sort.Direction.ASC)
                        .unique()
                );
                log.info("Companies name index created/verified");
            } catch (Exception e) {
                log.info("Companies name index already exists or failed: {}", e.getMessage());
            }
            
            // Create indexes for carbon_activities collection
            try {
                mongoTemplate.indexOps("carbon_activities").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("company_id", org.springframework.data.domain.Sort.Direction.ASC)
                );
                log.info("Carbon activities company_id index created/verified");
            } catch (Exception e) {
                log.info("Carbon activities company_id index already exists or failed: {}", e.getMessage());
            }
            
            try {
                mongoTemplate.indexOps("carbon_activities").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                );
                log.info("Carbon activities user_id index created/verified");
            } catch (Exception e) {
                log.info("Carbon activities user_id index already exists or failed: {}", e.getMessage());
            }
            
            try {
                mongoTemplate.indexOps("carbon_activities").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("timestamp", org.springframework.data.domain.Sort.Direction.DESC)
                );
                log.info("Carbon activities timestamp index created/verified");
            } catch (Exception e) {
                log.info("Carbon activities timestamp index already exists or failed: {}", e.getMessage());
            }
            
            // Create composite indexes for better performance
            try {
                mongoTemplate.indexOps("carbon_activities").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("company_id", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("timestamp", org.springframework.data.domain.Sort.Direction.DESC)
                );
                log.info("Carbon activities composite index created/verified");
            } catch (Exception e) {
                log.info("Carbon activities composite index already exists or failed: {}", e.getMessage());
            }
            
            try {
                mongoTemplate.indexOps("users").ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index()
                        .on("subscription_tier", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("last_active", org.springframework.data.domain.Sort.Direction.DESC)
                );
                log.info("Users subscription tier index created/verified");
            } catch (Exception e) {
                log.info("Users subscription tier index already exists or failed: {}", e.getMessage());
            }
            
            log.info("MongoDB Local optimization completed");
            
        } catch (Exception e) {
            log.warn("MongoDB Local optimization failed: {}", e.getMessage());
        }
    }
} 