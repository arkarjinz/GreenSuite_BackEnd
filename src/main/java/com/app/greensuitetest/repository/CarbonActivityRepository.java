package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.CarbonActivity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface CarbonActivityRepository extends MongoRepository<CarbonActivity, String> {
    List<CarbonActivity> findByCompanyId(String companyId);
   // List<CarbonActivity> findByYearAndMonth(String companyId,String year, String month);
   @Query("{ 'company_id': ?0, 'year': ?1, 'month': ?2 }")
   List<CarbonActivity> findByCompanyIdAndYearAndMonth(String companyId, String year, String month);

}
