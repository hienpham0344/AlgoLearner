package com.algolearner.platform.profile.avatar;

import com.algolearner.platform.common.exception.AppException;
import org.springframework.http.HttpStatus;

public final class AvatarVersionConflictException extends AppException {
    public AvatarVersionConflictException() {
        super("Avatar changed during upload; retry with the current profile",HttpStatus.CONFLICT,"AVATAR_VERSION_CONFLICT");
    }
}
