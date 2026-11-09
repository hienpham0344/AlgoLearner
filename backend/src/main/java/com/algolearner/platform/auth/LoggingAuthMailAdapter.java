package com.algolearner.platform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class LoggingAuthMailAdapter implements AuthMailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthMailAdapter.class);

    @Override
    public void sendOtp(String email, String username, String otpCode, OtpPurpose purpose, Instant expiresAt) {
        log.info("[OTP MAIL DISPATCH] To: {} ({}) | Purpose: {} | Code: {} | ExpiresAt: {}",
                email, username, purpose, otpCode, expiresAt);
    }
}
