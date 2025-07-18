package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.Company;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface CompanyRepository extends MongoRepository<Company, String> {
    Company findByName(String name);
    boolean existsByName(String name);

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<Company> findByNameContainingIgnoreCase(String query);
}