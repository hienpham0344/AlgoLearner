package com.algolearner.platform.profile;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class ProfileDtos {
    private ProfileDtos() { }
    public record PublicPracticeStatistics(long solved) { }
    @Schema(name="UserProfile", description="Owner-only profile under FR-PRO-001")
    public record UserProfile(String fullName, String username, String email, String avatarUrl, String shortBio,
            String role, Instant joinedAt, boolean isPublic, PublicPracticeStatistics publicPracticeStatistics,
            PracticeStatisticsService.Streak streak) { }
    @Schema(name="PublicUserProfile", description="Public allowlist under FR-PRO-002; never contains email or internal account identity")
    public record PublicUserProfile(String username, String avatarUrl, String shortBio, long solved,
            PracticeStatisticsService.Streak streak) { }
    @Schema(name="ProfileUpdate", additionalProperties=Schema.AdditionalPropertiesValue.FALSE,
            description="Omitted values remain unchanged; null/blank clears a field. Unknown or read-only properties are rejected.")
    public static final class ProfileUpdate {
        String fullName;
        String shortBio;
        boolean hasFullName;
        boolean hasShortBio;
        @JsonSetter("fullName")
        @Schema(maxLength=150, nullable=true)
        public void fullName(String value) { fullName=value; hasFullName=true; }
        @JsonSetter("shortBio")
        @Schema(maxLength=500, nullable=true)
        public void shortBio(String value) { shortBio=value; hasShortBio=true; }
    }
    @Schema(name="ProfilePrivacyUpdate",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
    public record PrivacyUpdate(@NotNull Boolean isPublic) { }
}
