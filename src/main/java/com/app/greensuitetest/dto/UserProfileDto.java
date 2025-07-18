package com.app.greensuitetest.dto;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.model.User;
import lombok.Data;

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
    }
}