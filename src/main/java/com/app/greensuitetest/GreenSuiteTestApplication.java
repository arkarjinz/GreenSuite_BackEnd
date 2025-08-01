package com.app.greensuitetest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class GreenSuiteTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(GreenSuiteTestApplication.class, args);
    }
}