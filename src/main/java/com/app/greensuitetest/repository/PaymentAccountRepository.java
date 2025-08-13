package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.PaymentAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, Long> {
    
    Optional<PaymentAccount> findByUserId(String userId);
    
    Optional<PaymentAccount> findByAccountNumber(String accountNumber);
    
    List<PaymentAccount> findByUserIdOrderByCreatedDateDesc(String userId);
    
    List<PaymentAccount> findByStatus(PaymentAccount.AccountStatus status);
    
    @Query("SELECT pa FROM PaymentAccount pa WHERE pa.userId = :userId AND pa.status = 'ACTIVE'")
    Optional<PaymentAccount> findActiveAccountByUserId(@Param("userId") String userId);
    
    boolean existsByUserId(String userId);
    
    boolean existsByAccountNumber(String accountNumber);
    
    @Query("SELECT COUNT(pa) FROM PaymentAccount pa WHERE pa.userId = :userId")
    long countByUserId(@Param("userId") String userId);
} 