package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Profile("internal-auth")
public class RegistrationMailer {
    private static final Logger LOG = LoggerFactory.getLogger(RegistrationMailer.class);
    private final JavaMailSender sender;
    private final String from;

    public RegistrationMailer(JavaMailSender sender, @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public void send(String to, String code) {
        send(to, code, "REGISTER");
    }

    public void send(String to, String code, String purpose) {
        if (!StringUtils.hasText(from)) throw new ApiException(ErrorCode.MAIL_NOT_CONFIGURED);
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        String action = "RESET_PASSWORD".equals(purpose) ? "password reset" : "registration";
        message.setSubject("AlgoLearner " + action + " code");
        message.setText("Your " + action + " code is " + code + ". It expires in 10 minutes.");
        try {
            sender.send(message);
        } catch (MailException ex) {
            LOG.warn("Verification email delivery failed: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.MAIL_DELIVERY_FAILED);
        }
    }
}
