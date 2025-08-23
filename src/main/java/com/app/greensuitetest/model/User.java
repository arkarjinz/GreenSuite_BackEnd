package com.app.greensuitetest.model;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.constants.SubscriptionTier;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.*;

@Document(collection = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private String id;

    @Field("first_name")
    private String firstName;

    @Field("last_name")
    private String lastName;

    @Field("user_name")
    @Indexed(unique = true)
    private String userName;

    @Indexed(unique = true)
    private String email;
    private String password;

    @Field("company_id")
    private String companyId;

    @Field("company_role")
    private Role companyRole;

    @Field("global_admin")
    private boolean globalAdmin;

    @Field("subscription_tier")
    @Builder.Default
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    @Field("approval_status")
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("last_active")
    private LocalDateTime lastActive;

    @Field("streak_count")
    private int streakCount;

    @Field("badges")
    private Set<String> badges = new HashSet<>();

    @Field("ai_credits")
    @Builder.Default
    private int aiCredits = 50; // Default 50 credits for new users

    @Field("purchased_features")
    private Set<String> purchasedFeatures = new HashSet<>();

    @Field("refresh_token")
    private String refreshToken;

    @Field("security_questions")
    private Map<String, String> securityQuestions = new HashMap<>();

    @Field("recovery_attempts")
    private int recoveryAttempts;

    @Field("recovery_lock_until")
    private LocalDateTime recoveryLockUntil;

    // New fields for rejection tracking
    @Field("rejection_count")
    private int rejectionCount = 0;

    @Field("is_banned")
    private boolean isBanned = false;

    @Field("banned_at")
    private LocalDateTime bannedAt;

    @Field("ban_reason")
    private String banReason;

    @Field("rejection_history")
    private List<RejectionRecord> rejectionHistory = new ArrayList<>();

    @Field("last_rejection_at")
    private LocalDateTime lastRejectionAt;

    // New fields for AI credit tracking
    @Field("total_credits_purchased")
    @Builder.Default
    private int totalCreditsPurchased = 0;

    @Field("total_credits_used")
    @Builder.Default
    private int totalCreditsUsed = 0;

    @Field("last_credit_purchase")
    private LocalDateTime lastCreditPurchase;

    @Field("stripe_customer_id")
    private String stripeCustomerId;

    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (this.isGlobalAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (this.getCompanyRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + this.getCompanyRole().name()));
        }
        return authorities;
    }

    public boolean isRecoveryLocked() {
        return recoveryLockUntil != null && recoveryLockUntil.isAfter(LocalDateTime.now());
    }

    // Credit-related methods
    public boolean hasCreditsForChat() {
        return this.aiCredits >= 2;
    }

    public int getMaxPossibleChats() {
        return this.aiCredits / 2;
    }

    public boolean isLowOnCredits() {
        return this.aiCredits < 10;
    }

    public void deductCredits(int amount) {
        this.aiCredits = Math.max(0, this.aiCredits - amount);
        this.totalCreditsUsed += amount;
        this.lastActive = LocalDateTime.now();
    }

    public void addCredits(int amount) {
        this.aiCredits += amount;
        this.totalCreditsPurchased += amount;
        this.lastCreditPurchase = LocalDateTime.now();
        this.lastActive = LocalDateTime.now();
    }

    /**
     * Get maximum credits allowed for the user
     * Note: Users can have maximum 50 credits total
     */
    public int getMaxCredits() {
        return 50; // Maximum 50 credits total
    }

    /**
     * Check if user can receive credits
     * Note: Users cannot exceed 50 credits total
     */
    public boolean canReceiveCredits() {
        return this.aiCredits < 50; // Cannot exceed 50 credits
    }

    /**
     * Get the maximum amount of credits that can be added
     */
    public int getMaxRefillAmount() {
        return Math.max(0, 50 - this.aiCredits); // Maximum credits that can be added
    }

    // Rejection tracking methods
    public void incrementRejectionCount(String companyId, String companyName, String rejectedBy) {
        this.rejectionCount++;
        this.lastRejectionAt = LocalDateTime.now();

        // Add to rejection history
        RejectionRecord record = new RejectionRecord();
        record.setCompanyId(companyId);
        record.setCompanyName(companyName);
        record.setRejectedBy(rejectedBy);
        record.setRejectedAt(LocalDateTime.now());
        record.setRejectionNumber(this.rejectionCount);

        this.rejectionHistory.add(record);

        // Remove company association but keep user data
        this.companyId = null;
        this.companyRole = null;
        this.approvalStatus = ApprovalStatus.REJECTED;

        // Check if user should be banned
        if (this.rejectionCount >= 5) {
            this.isBanned = true;
            this.bannedAt = LocalDateTime.now();
            this.banReason = "Exceeded maximum rejection limit (5 rejections)";
        }
    }

    public boolean isApproachingBan() {
        return this.rejectionCount >= 4 && !this.isBanned;
    }

    public int getRemainingAttempts() {
        return Math.max(0, 5 - this.rejectionCount);
    }

    // Initialize default values for existing users
    public void initializeDefaults() {
        if (this.aiCredits == 0) {
            this.aiCredits = 50; // Give existing users default credits
        }
        if (this.badges == null) {
            this.badges = new HashSet<>();
        }
        if (this.purchasedFeatures == null) {
            this.purchasedFeatures = new HashSet<>();
        }
        if (this.securityQuestions == null) {
            this.securityQuestions = new HashMap<>();
        }
        if (this.rejectionHistory == null) {
            this.rejectionHistory = new ArrayList<>();
        }
    }

    // Inner class for rejection records
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectionRecord {
        private String companyId;
        private String companyName;
        private String rejectedBy;
        private LocalDateTime rejectedAt;
        private int rejectionNumber;
    }
}