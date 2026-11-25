package com.algolearner.platform.common.exception;

/** The resource exists but may not be used right now (draft, archived parent). Reported as 404 to hide drafts. */
public class ResourceNotAvailableException extends AppException {
    public ResourceNotAvailableException(String resourceName, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_AVAILABLE, String.format("%s is not available: %s", resourceName, identifier));
    }
}
