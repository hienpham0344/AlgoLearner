package com.algolearner.platform.practice;

import com.algolearner.platform.practice.PracticeDtos.*;
import java.util.List;

/** Entity to DTO mapping. Entities never leave the service layer. */
final class PracticeMapper {
    private PracticeMapper() { }

    static PracticeSummary summary(ClassroomPracticeEntity p, boolean available, CompletionStatus completion) {
        return new PracticeSummary(p.getId(), p.getClassroom().getId(), p.getLessonId(), p.getTitle(), p.getProblemType(),
                p.getStatus(), available, p.getAllowedLanguages(), p.getTimeLimitMs(), p.getMemoryLimitMb(), completion,
                p.getCreatedAt(), p.getUpdatedAt());
    }

    /** {@code samples} must already be filtered to sample testcases: hidden ones never reach this method. */
    static PracticeDetail detail(ClassroomPracticeEntity p, boolean available, CompletionStatus completion,
                                 List<PracticeTestcaseEntity> samples) {
        return new PracticeDetail(p.getId(), p.getClassroom().getId(), p.getLessonId(), p.getTitle(), p.getDescription(),
                p.getProblemType(), p.getStarterCode(), p.getAllowedLanguages(), p.getTimeLimitMs(), p.getMemoryLimitMb(),
                p.getStatus(), available, p.getTestcaseVersion(), completion,
                samples.stream().filter(PracticeTestcaseEntity::isSample).map(PracticeMapper::sample).toList(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    static SampleTestcase sample(PracticeTestcaseEntity t) {
        return new SampleTestcase(t.getId(), t.getInput(), t.getExpectedOutput(), t.getOrderIndex());
    }

    static TeacherTestcase teacherTestcase(PracticeTestcaseEntity t) {
        return new TeacherTestcase(t.getId(), t.getVersion(), t.getInput(), t.getExpectedOutput(), t.isSample(), t.getOrderIndex());
    }

    static SubmissionView submission(PracticeSubmissionEntity s) {
        return new SubmissionView(s.getId(), s.getPractice().getId(), s.getStudent().getId(), s.getStudent().getUsername(),
                s.getLanguage(), s.getCode(), s.getTestcaseVersion(), s.getVerdict(), s.getTestsPassed(), s.getTotalTests(), s.getRuntimeMs(), s.getMemoryKb(),
                s.getRejudgeStatus(), s.getSubmittedAt());
    }
}
