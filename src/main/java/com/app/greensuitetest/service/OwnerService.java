package com.app.greensuitetest.service;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.exception.EntityNotFoundException;
import com.app.greensuitetest.exception.OperationNotAllowedException;
import com.app.greensuitetest.model.Company;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.CompanyRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerService {
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final SecurityUtil securityUtil;

    public List<UserProfileDto> getPendingUsers() {
        String companyId = securityUtil.getCurrentUserCompanyId();
        List<User> users = userRepository.findByCompanyIdAndApprovalStatus(companyId, ApprovalStatus.PENDING);

        // Get company name
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));
        String companyName = company.getName();

        // Convert to DTOs with company name
        return users.stream()
                .map(user -> {
                    UserProfileDto dto = new UserProfileDto(user);
                    dto.setCompanyName(companyName);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public UserProfileDto approveUser(String userId) {
        User currentUser = securityUtil.getCurrentUser();
        User targetUser = getUser(userId);

        validateOwnership(currentUser, targetUser);
        validateNotSelfOperation(currentUser, targetUser);
        validateNotOwner(targetUser);

        targetUser.setApprovalStatus(ApprovalStatus.APPROVED);
        userRepository.save(targetUser);

        return createUserProfileDto(targetUser);
    }

    public UserProfileDto rejectUser(String userId) {
        User currentUser = securityUtil.getCurrentUser();
        User targetUser = getUser(userId);

        validateOwnership(currentUser, targetUser);
        validateNotSelfOperation(currentUser, targetUser);
        validateNotOwner(targetUser);

        targetUser.setApprovalStatus(ApprovalStatus.REJECTED);
        userRepository.save(targetUser);

        return createUserProfileDto(targetUser);
    }

    private UserProfileDto createUserProfileDto(User user) {
        UserProfileDto dto = new UserProfileDto(user);
        // Add company name
        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));
        dto.setCompanyName(company.getName());
        return dto;
    }

    private User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private void validateOwnership(User currentUser, User targetUser) {
        if (!currentUser.getCompanyId().equals(targetUser.getCompanyId())) {
            throw new OperationNotAllowedException("User doesn't belong to your company");
        }
    }

    private void validateNotSelfOperation(User currentUser, User targetUser) {
        if (currentUser.getId().equals(targetUser.getId())) {
            throw new OperationNotAllowedException("Cannot perform this operation on yourself");
        }
    }

    private void validateNotOwner(User targetUser) {
        if (targetUser.getCompanyRole() == Role.OWNER) {
            throw new OperationNotAllowedException("Cannot modify other owners");
        }
    }
}