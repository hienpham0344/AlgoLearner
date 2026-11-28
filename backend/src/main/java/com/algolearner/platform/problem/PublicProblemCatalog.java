package com.algolearner.platform.problem;

import com.algolearner.platform.judge.JudgeGateway.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Trusted module port, never serialize a ContentSnapshot to public students. */
public interface PublicProblemCatalog {
    record ContentSnapshot(
            UUID revisionId,
            String title,
            String statement,
            Set<Language> languages,
            List<TestCaseSnapshot> testCases,
            Limits limits,
            Feedback feedback,
            ProblemDtos.Difficulty difficulty,
            Set<String> tags,
            String editorial) {
        public ContentSnapshot {
            languages = Set.copyOf(languages);
            testCases = List.copyOf(testCases);
            difficulty = difficulty == null ? ProblemDtos.Difficulty.EASY : difficulty;
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }

        public ContentSnapshot(
                UUID revisionId,
                String title,
                String statement,
                Set<Language> languages,
                List<TestCaseSnapshot> testCases,
                Limits limits,
                Feedback feedback) {
            this(
                    revisionId,
                    title,
                    statement,
                    languages,
                    testCases,
                    limits,
                    feedback,
                    null,
                    null,
                    null);
        }
    }

    ContentSnapshot contentSnapshot(UUID activeTeacherId, UUID publishedProblemId);
}
