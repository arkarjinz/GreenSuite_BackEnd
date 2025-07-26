package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.CarbonTotal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

import java.util.Optional;

public interface CarbonTotalRepository extends MongoRepository<CarbonTotal, String> {
    Optional<CarbonTotal> findByUserIdAndMonthAndYear(String userId,String companyId, String month, String year);

    //Htet Htet
    List<CarbonTotal> findByCompanyIdAndYearIn(String companyId, List<String> years);
    List<CarbonTotal> findByCompanyIdAndYearAndMonth(String companyId, String year, String month);
}
