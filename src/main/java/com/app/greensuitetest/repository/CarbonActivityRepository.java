package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.CarbonActivity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CarbonActivityRepository extends MongoRepository<CarbonActivity, String> {
    List<CarbonActivity> findByCompanyId(String companyId);
   // List<CarbonActivity> findByYearAndMonth(String companyId,String year, String month);
   @Query("{ 'company_id': ?0, 'year': ?1, 'month': ?2 }")
   //Htet Htet
   List<CarbonActivity> findByCompanyIdAndYearAndMonth(String companyId, String year, String month);
    //
    @Query(value = "{ 'company_id': ?0, 'year': ?1 }", fields = "{ 'month': 1, '_id': 0 }")
    List<CarbonActivity> findByCompanyIdAndYearReturnMonths(String companyId, String year);


}
