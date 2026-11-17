package com.algolearner.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Instant;

@Component
@Profile("!mock")
public class SmtpAuthMailAdapter implements AuthMailPort {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpAuthMailAdapter(JavaMailSender mailSender,
            @Value("${app.auth.mail-from:}") String fromAddress) {
        Assert.hasText(fromAddress, "AUTH_MAIL_FROM must be configured");
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendOtp(String email, String username, String otpCode, OtpPurpose purpose, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject(purpose == OtpPurpose.REGISTER ? "Verify your AlgoLearner account" : "Reset your AlgoLearner password");
        message.setText("Hello " + username + ",\n\nYour AlgoLearner verification code is " + otpCode
                + ". It expires at " + expiresAt + ".\n\nIf you did not request it, ignore this message.");
        mailSender.send(message);
    }
}
