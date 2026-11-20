package com.algolearner.platform.assignment;

import com.algolearner.platform.common.exception.AppException;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** Pure authoring rules; historical grade values are never rewritten by validation. */
final class AssignmentConfigurationPolicy {
    private AssignmentConfigurationPolicy() { }

    static void validate(Instant start, Instant deadline, Integer duration, Integer maxAttempts,
            BigDecimal latePenalty, GradePublishMode publication) {
        if (start != null && deadline != null && !start.isBefore(deadline))
            reject("Deadline must be after the start time.");
        if (duration != null && duration <= 0) reject("Duration must be positive.");
        if (maxAttempts != null && maxAttempts < 0) reject("Maximum attempts cannot be negative; zero means unlimited.");
        if (latePenalty != null && latePenalty.signum() != 0) reject("Late submissions do not incur a score penalty.");
        if (publication == GradePublishMode.AFTER_DEADLINE && deadline == null)
            reject("After-deadline publication requires a deadline.");
    }

    private static void reject(String message) { throw new ConfigurationException(message); }

    private static final class ConfigurationException extends AppException {
        ConfigurationException(String message) { super(message, HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ASSIGNMENT_CONFIGURATION"); }
    }
}
