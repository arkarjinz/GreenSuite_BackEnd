package com.app.greensuitetest.model;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
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
    private int aiCredits;

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
}