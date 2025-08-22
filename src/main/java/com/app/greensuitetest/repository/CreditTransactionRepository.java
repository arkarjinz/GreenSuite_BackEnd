package com.app.greensuitetest.repository;

import com.app.greensuitetest.model.CreditTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditTransactionRepository extends MongoRepository<CreditTransaction, String> {
    
    /**
     * Find all transactions for a specific user, ordered by timestamp descending
     */
    List<CreditTransaction> findByUserIdOrderByTimestampDesc(String userId);
    
    /**
     * Find all transactions for a specific user with pagination
     */
    Page<CreditTransaction> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
    
    /**
     * Find transactions for a specific conversation
     */
    List<CreditTransaction> findByConversationIdOrderByTimestampDesc(String conversationId);

    /**
     * Find transactions by user ID and type, ordered by timestamp descending
     */
    List<CreditTransaction> findByUserIdAndTypeOrderByTimestampDesc(String userId, CreditTransaction.TransactionType type);
    
    /**
     * Find transaction by Stripe payment intent ID
     */
    java.util.Optional<CreditTransaction> findByStripePaymentIntentId(String stripePaymentIntentId);
    
    /**
     * Find all transactions for a user (for statistics)
     */
    List<CreditTransaction> findByUserId(String userId);

} 