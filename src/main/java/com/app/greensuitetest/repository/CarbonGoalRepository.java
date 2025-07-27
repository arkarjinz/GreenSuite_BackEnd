package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.CarbonGoal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CarbonGoalRepository extends MongoRepository<CarbonGoal, String> {
    Optional<CarbonGoal> findByCompanyIdAndYearAndMonth(String companyId, String month,String year);
}
