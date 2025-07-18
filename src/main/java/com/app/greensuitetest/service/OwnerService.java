
package com.app.greensuitetest.service;

import com.app.greensuitetest.constants.ApprovalStatus;
import com.app.greensuitetest.constants.Role;
import com.app.greensuitetest.exception.EntityNotFoundException;
import com.app.greensuitetest.exception.OperationNotAllowedException;
import com.app.greensuitetest.model.User;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerService {
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    public List<User> getPendingUsers() {
        String companyId = securityUtil.getCurrentUserCompanyId();
        return userRepository.findByCompanyIdAndApprovalStatus(companyId, ApprovalStatus.PENDING);
    }

    public User approveUser(String userId) {
        User currentUser = securityUtil.getCurrentUser();
        User targetUser = getUser(userId);

        validateOwnership(currentUser, targetUser);
        validateNotSelfOperation(currentUser, targetUser);
        validateNotOwner(targetUser);

        targetUser.setApprovalStatus(ApprovalStatus.APPROVED);
        return userRepository.save(targetUser);
    }

    public User rejectUser(String userId) {
        User currentUser = securityUtil.getCurrentUser();
        User targetUser = getUser(userId);

        validateOwnership(currentUser, targetUser);
        validateNotSelfOperation(currentUser, targetUser);
        validateNotOwner(targetUser);

        targetUser.setApprovalStatus(ApprovalStatus.REJECTED);
        return userRepository.save(targetUser);
    }

    public User removeUser(String userId) {
        User currentUser = securityUtil.getCurrentUser();
        User targetUser = getUser(userId);

        validateOwnership(currentUser, targetUser);
        validateNotSelfOperation(currentUser, targetUser);
        validateNotOwner(targetUser);

        // Dissociate user from company
        targetUser.setCompanyId(null);
        targetUser.setCompanyRole(null);
        targetUser.setApprovalStatus(ApprovalStatus.REJECTED);
        return userRepository.save(targetUser);
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