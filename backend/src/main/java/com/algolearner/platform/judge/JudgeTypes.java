package com.algolearner.platform.judge;

public final class JudgeTypes {
    private JudgeTypes() {}

    public enum JudgeLanguage { JAVA, PYTHON }
    public enum JudgeContextType { PUBLIC_PROBLEM, CLASS_ASSIGNMENT, CLASS_PRACTICE }
    public enum JudgeProblemFormat { STANDARD_IO, FUNCTION_METHOD }
    public enum JudgeFeedbackLevel { AGGREGATE, TEST_STATUS, DETAILED }
    public enum TestCaseVisibility { SAMPLE, HIDDEN }
    public enum JudgeStatus {
        PENDING, QUEUED, JUDGING, ACCEPTED, WRONG_ANSWER, COMPILATION_ERROR,
        RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, JUDGE_ERROR, CANCELLED
    }
    public enum EvaluationTrigger { INITIAL, REJUDGE }
    public enum RejudgeStatus { QUEUED, RUNNING, COMPLETED, FAILED }
}
