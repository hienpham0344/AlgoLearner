package com.algolearner.platform.problem;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.algolearner.platform.judge.JudgeGateway.*;

/** Trusted module port, never serialize a ContentSnapshot to public students. */
public interface PublicProblemCatalog {
    record ContentSnapshot(UUID revisionId,String title,String statement,Set<Language> languages,
                           List<TestCaseSnapshot> testCases,Limits limits,Feedback feedback) {
        public ContentSnapshot {languages=Set.copyOf(languages);testCases=List.copyOf(testCases);}
    }
    ContentSnapshot contentSnapshot(UUID activeTeacherId,UUID publishedProblemId);
}
