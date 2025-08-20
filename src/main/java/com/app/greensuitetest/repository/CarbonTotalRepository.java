package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.CarbonTotal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CarbonTotalRepository extends MongoRepository<CarbonTotal, String> {
    Optional<CarbonTotal> findByUserIdAndMonthAndYear(String userId,String companyId, String month, String year);
}
