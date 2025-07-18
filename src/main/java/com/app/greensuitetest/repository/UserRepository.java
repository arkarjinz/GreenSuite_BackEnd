package com.app.greensuitetest.repository;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByCompanyId(String companyId);
    void deleteByCompanyId(String companyId);
    boolean existsByUserName(String userName);

    @Query("{ 'refreshToken': ?0 }")
    Optional<User> findByRefreshToken(String refreshToken);

    List<User> findByCompanyIdAndApprovalStatus(String companyId, ApprovalStatus status);

    Optional<User> findByCompanyIdAndCompanyRole(String companyId, Role role);
}