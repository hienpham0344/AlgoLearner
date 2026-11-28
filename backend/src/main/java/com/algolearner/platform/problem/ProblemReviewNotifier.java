package com.algolearner.platform.problem;

import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.notification.NotificationService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!mock")
@RequiredArgsConstructor
public class ProblemReviewNotifier {
    private final AccountRepository accounts;
    private final NotificationService notifications;

    public void notifyAuthor(UUID authorId, UUID problemId, String outcome, String reason) {
        var author = accounts.findById(authorId).orElseThrow();
        String title =
                "APPROVED".equals(outcome)
                        ? "Problem contribution approved"
                        : "Problem contribution rejected";
        String message =
                reason == null ? title : reason.substring(0, Math.min(reason.length(), 1000));
        notifications.notifyStudent(
                author,
                "PUBLIC_PROBLEM_REVIEW",
                title,
                message,
                "/teacher/problems/" + problemId,
                null,
                null);
    }
}
