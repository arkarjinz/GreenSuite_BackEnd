package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.CarbonActivity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CarbonActivityRepository extends MongoRepository<CarbonActivity, String> {
    List<CarbonActivity> findByCompanyId(String companyId);
}