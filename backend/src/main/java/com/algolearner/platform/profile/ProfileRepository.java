package com.algolearner.platform.profile;
import java.time.Instant;
import java.util.UUID;
/** Storage port. Implement mutations atomically; profiles are immutable snapshots. */
public interface ProfileRepository {
    public record Profile(UUID accountId, String fullName, String username, String email, String avatarKey,
            String shortBio, String role, Instant joinedAt, boolean isPublic) { }
    Profile own(UUID id);
    Profile publicProfile(String username, Instant now);
    Profile byUsername(String username);
    void update(UUID id, String name, String bio, Instant now);
    void privacy(UUID id, boolean isPublic, Instant now);
    void avatar(UUID id, String key, Instant now);
}
