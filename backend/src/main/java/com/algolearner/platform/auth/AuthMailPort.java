package com.algolearner.platform.auth;

import java.time.Instant;

public interface AuthMailPort {
    void sendOtp(String email, String username, String otpCode, OtpPurpose purpose, Instant expiresAt);
}

