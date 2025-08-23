package com.app.greensuitetest.dto;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.model.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileDto {
    private String id;
    private String firstName;
    private String lastName;
    private String userName;
    private String email;
    private String companyId;
    private String companyName;
    private Role companyRole;
    private boolean globalAdmin;
    private ApprovalStatus approvalStatus;

    // New fields for rejection tracking
    private int rejectionCount;
    private boolean banned;
    private LocalDateTime bannedAt;
    private String banReason;
    private int remainingAttempts;
    private boolean approachingBan;
    private LocalDateTime lastRejectionAt;
    

    public UserProfileDto(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.userName = user.getUserName();
        this.email = user.getEmail();
        this.companyId = user.getCompanyId();
        this.companyRole = user.getCompanyRole();
        this.globalAdmin = user.isGlobalAdmin();
        this.approvalStatus = user.getApprovalStatus();

        // Set rejection-related fields
        this.rejectionCount = user.getRejectionCount();
        this.banned = user.isBanned();
        this.bannedAt = user.getBannedAt();
        this.banReason = user.getBanReason();
        this.remainingAttempts = user.getRemainingAttempts();
        this.approachingBan = user.isApproachingBan();
        this.lastRejectionAt = user.getLastRejectionAt();
     
        }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public void setRejectionCount(int rejectionCount) {
        this.rejectionCount = rejectionCount;
    }

    public void setRemainingAttempts(int remainingAttempts) {
        this.remainingAttempts = remainingAttempts;
    }

    public void setApproachingBan(boolean approachingBan) {
        this.approachingBan = approachingBan;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }

}