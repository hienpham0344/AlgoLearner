package com.algolearner.platform.common.exception;

/** The Code Judge timed out, crashed or could not be reached. Callers must never invent a verdict. */
public class JudgeServiceUnavailableException extends AppException {
    public JudgeServiceUnavailableException(String message) {
        super(ErrorCode.JUDGE_SERVICE_UNAVAILABLE, message);
    }

    public JudgeServiceUnavailableException(String message, Throwable cause) {
        super(ErrorCode.JUDGE_SERVICE_UNAVAILABLE, message, cause);
    }
}
