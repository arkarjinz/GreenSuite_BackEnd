package com.app.greensuitetest.repository;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    // ============== EXISTING QUERIES ==============
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByCompanyId(String companyId);
    void deleteByCompanyId(String companyId);
    boolean existsByUserName(String userName);
    Optional<User> findByUserName(String userName);

    @Query("{ 'refreshToken': ?0 }")
    Optional<User> findByRefreshToken(String refreshToken);

    List<User> findByCompanyIdAndApprovalStatus(String companyId, ApprovalStatus status);

    Optional<User> findByCompanyIdAndCompanyRole(String companyId, Role role);

    // ============== NEW BAN & REJECTION QUERIES ==============

    // Ban-related queries
    @Query("{ 'isBanned': true }")
    List<User> findAllBannedUsers();

    @Query("{ 'isBanned': false }")
    List<User> findAllActivUsers();

    @Query("{ '$or': [ { 'email': ?0 }, { 'userName': ?1 } ], 'isBanned': true }")
    Optional<User> findBannedUserByEmailOrUsername(String email, String userName);

    // Rejection count queries
    @Query("{ 'rejectionCount': { $gte: ?0 } }")
    List<User> findUsersByRejectionCountGreaterThanEqual(int count);

    @Query("{ 'rejectionCount': { $eq: ?0 } }")
    List<User> findUsersByRejectionCount(int count);

    @Query("{ 'rejectionCount': { $gte: 4 }, 'isBanned': false }")
    List<User> findUsersApproachingBan();

    @Query("{ 'rejectionCount': { $gt: 0 }, 'isBanned': false }")
    List<User> findUsersWithRejections();

    // Approval status with rejection info
    @Query("{ 'email': ?0, 'approvalStatus': 'REJECTED' }")
    Optional<User> findRejectedUserByEmail(String email);

    @Query("{ 'companyId': ?0, 'approvalStatus': 'REJECTED' }")
    List<User> findRejectedUsersByCompany(String companyId);

    @Query("{ 'companyId': ?0, 'approvalStatus': 'PENDING', 'rejectionCount': { $gt: 0 } }")
    List<User> findReapplicantsByCompany(String companyId);

    // Time-based queries
    @Query("{ 'bannedAt': { $gte: ?0, $lte: ?1 } }")
    List<User> findUsersBannedBetween(LocalDateTime startDate, LocalDateTime endDate);

    @Query("{ 'lastRejectionAt': { $gte: ?0, $lte: ?1 } }")
    List<User> findUsersRejectedBetween(LocalDateTime startDate, LocalDateTime endDate);

    // Complex queries for analytics
    @Query("{ 'rejectionCount': { $gte: 1 }, 'approvalStatus': 'APPROVED' }")
    List<User> findApprovedUsersWithPreviousRejections();

    @Query("{ 'companyId': ?0, 'rejectionCount': { $gte: ?1 } }")
    List<User> findUsersByCompanyAndMinRejections(String companyId, int minRejections);

    // Advanced search queries
    @Query("{ '$and': [ " +
            "{ '$or': [ { 'firstName': { $regex: ?0, $options: 'i' } }, " +
            "{ 'lastName': { $regex: ?0, $options: 'i' } }, " +
            "{ 'email': { $regex: ?0, $options: 'i' } }, " +
            "{ 'userName': { $regex: ?0, $options: 'i' } } ] }, " +
            "{ 'isBanned': ?1 } ] }")
    List<User> searchUsersByNameOrEmailWithBanStatus(String searchTerm, boolean isBanned);

    // Aggregation-friendly queries
    @Query("{ 'rejectionCount': { $in: ?0 } }")
    List<User> findUsersByRejectionCounts(List<Integer> rejectionCounts);

    @Query("{ 'companyId': { $in: ?0 }, 'isBanned': true }")
    List<User> findBannedUsersByCompanies(List<String> companyIds);

    // Statistical queries
    @Query(value = "{ 'isBanned': false }", count = true)
    long countActiveUsers();

    @Query(value = "{ 'isBanned': true }", count = true)
    long countBannedUsers();

    @Query(value = "{ 'rejectionCount': { $gte: ?0 } }", count = true)
    long countUsersByMinRejections(int minRejections);

    @Query(value = "{ 'companyId': ?0, 'approvalStatus': 'PENDING', 'rejectionCount': { $gt: 0 } }", count = true)
    long countReapplicantsByCompany(String companyId);

    // Recent activity queries
    @Query("{ 'lastRejectionAt': { $gte: ?0 } }")
    List<User> findUsersRejectedSince(LocalDateTime since);

    @Query("{ 'bannedAt': { $gte: ?0 } }")
    List<User> findUsersBannedSince(LocalDateTime since);

    // Custom method for finding users who need warning notifications
    @Query("{ 'rejectionCount': 4, 'isBanned': false }")
    List<User> findUsersNeedingWarning();

    // Method to find users by rejection history company
    @Query("{ 'rejectionHistory.companyId': ?0 }")
    List<User> findUsersRejectedByCompany(String companyId);

    // Method to find users with multiple rejections from same company
    @Query("{ 'rejectionHistory': { $elemMatch: { 'companyId': ?0 } }, 'rejectionCount': { $gte: ?1 } }")
    List<User> findUsersWithMultipleRejectionsFromCompany(String companyId, int minCount);

    // Recent registrations with rejection history
    @Query("{ 'createdAt': { $gte: ?0 }, 'rejectionCount': { $gt: 0 } }")
    List<User> findRecentReapplicants(LocalDateTime since);

    // Users pending approval with warnings needed
    @Query("{ 'approvalStatus': 'PENDING', 'rejectionCount': { $in: [3, 4] } }")
    List<User> findPendingUsersNearBan();
}