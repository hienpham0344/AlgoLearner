package com.algolearner.platform.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Internal auth has no persisted official submissions yet. Never expose demo users' activity. */
@Repository
@Profile("internal-auth")
public class InternalPracticeRepository implements PracticeRepository {
    @Override public List<Accepted> acceptedPublicSubmissions(UUID accountId) { return List.of(); }
}
