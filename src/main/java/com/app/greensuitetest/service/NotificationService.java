package com.app.greensuitetest.service;

import com.app.greensuitetest.model.Company;
import com.app.greensuitetest.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    // In a real implementation, you would inject email service, SMS service, etc.
    // private final EmailService emailService;
    // private final SmsService smsService;

    public void sendRejectionNotification(User user, Company company, String reason) {
        log.info("Sending rejection notification to user: {} for company: {}",
                user.getEmail(), company.getName());

        String message = String.format(
                "Your application to join %s has been rejected. " +
                        "Reason: %s. " +
                        "You have %d attempts remaining before permanent ban. " +
                        "You can re-apply to this company or try applying to a different company.",
                company.getName(),
                reason,
                user.getRemainingAttempts()
        );

        // TODO: Implement actual email/SMS sending
        // emailService.sendEmail(user.getEmail(), "Application Rejected", message);

        log.info("Rejection notification sent to: {}", user.getEmail());
    }

    public void sendWarningNotification(User user) {
        log.warn("Sending warning notification to user: {} - 4th rejection", user.getEmail());

        String message = String.format(
                "⚠️ WARNING: This is your 4th rejection across all companies. " +
                        "One more rejection will result in a permanent ban from the platform. " +
                        "Please ensure you meet the requirements before applying to companies. " +
                        "Current rejection count: %d/5",
                user.getRejectionCount()
        );

        // TODO: Implement actual email/SMS sending
        // emailService.sendUrgentEmail(user.getEmail(), "⚠️ FINAL WARNING - Account Ban Risk", message);

        log.warn("Warning notification sent to: {}", user.getEmail());
    }

    public void sendBanNotification(User user) {
        log.error("Sending ban notification to user: {} - permanently banned", user.getEmail());

        String message = String.format(
                "Your account has been permanently banned from the platform due to exceeding " +
                        "the maximum number of rejections (5). " +
                        "Ban reason: %s " +
                        "Banned at: %s " +
                        "If you believe this is an error, please contact support.",
                user.getBanReason(),
                user.getBannedAt()
        );

        // TODO: Implement actual email/SMS sending
        // emailService.sendUrgentEmail(user.getEmail(), "🚫 Account Permanently Banned", message);

        log.error("Ban notification sent to: {}", user.getEmail());
    }

    public void sendReapplicationNotification(User user, Company company) {
        log.info("User {} is re-applying to company {} after previous rejections",
                user.getEmail(), company.getName());

        if (user.getRejectionCount() > 0) {
            String message = String.format(
                    "You are re-applying to %s after %d previous rejection(s). " +
                            "You have %d attempts remaining. " +
                            "%s" +
                            "Please ensure you meet all requirements before submitting your application.",
                    company.getName(),
                    user.getRejectionCount(),
                    user.getRemainingAttempts(),
                    user.isApproachingBan() ? "⚠️ WARNING: One more rejection will result in permanent ban! " : ""
            );

            // TODO: Implement actual email/SMS sending
            // emailService.sendEmail(user.getEmail(), "Re-application Submitted", message);
        }
    }

    public void notifyOwnerOfReapplication(User owner, User applicant, Company company) {
        log.info("Notifying owner {} about re-applicant {} with {} previous rejections",
                owner.getEmail(), applicant.getEmail(), applicant.getRejectionCount());

        String message = String.format(
                "User %s (%s %s) is re-applying to join %s. " +
                        "Previous rejections: %d " +
                        "Remaining attempts: %d " +
                        "%s" +
                        "Please review their application carefully.",
                applicant.getEmail(),
                applicant.getFirstName(),
                applicant.getLastName(),
                company.getName(),
                applicant.getRejectionCount(),
                applicant.getRemainingAttempts(),
                applicant.isApproachingBan() ? "⚠️ NOTE: This user will be banned if rejected again! " : ""
        );

        // TODO: Implement actual email/SMS sending
        // emailService.sendEmail(owner.getEmail(), "Re-application Alert", message);

        log.info("Owner notification sent to: {}", owner.getEmail());
    }
}