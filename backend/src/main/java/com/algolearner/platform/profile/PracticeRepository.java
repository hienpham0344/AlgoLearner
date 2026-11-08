package com.algolearner.platform.profile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
/** Official ACCEPTED public submissions only: excludes RUN, class practice and assignments. */
public interface PracticeRepository {
    record Accepted(UUID problemId, Instant submittedAt) { }
    List<Accepted> acceptedPublicSubmissions(UUID accountId);
}
