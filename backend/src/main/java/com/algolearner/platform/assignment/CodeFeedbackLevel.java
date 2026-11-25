package com.algolearner.platform.assignment;

public enum CodeFeedbackLevel {
    PASS_FAIL,
    TESTCASE_STATUS,
    FULL_DETAILS,
    /** Legacy API/database aliases retained for existing assignments. */
    SUMMARY,
    DETAILED;

    com.algolearner.platform.judge.JudgeGateway.Feedback feedback() {
        return switch (this) {
            case PASS_FAIL, SUMMARY -> com.algolearner.platform.judge.JudgeGateway.Feedback.PASS_FAIL;
            case TESTCASE_STATUS -> com.algolearner.platform.judge.JudgeGateway.Feedback.TESTCASE_STATUS;
            case FULL_DETAILS, DETAILED -> com.algolearner.platform.judge.JudgeGateway.Feedback.FULL_DETAILS;
        };
    }
}
