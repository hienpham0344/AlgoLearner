package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Profile("internal-auth")
public class RegistrationMailer {
    private static final Logger LOG = LoggerFactory.getLogger(RegistrationMailer.class);
    private static final ExecutorService MAIL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private final JavaMailSender sender;
    private final String from;
    private final boolean async;

    public RegistrationMailer(JavaMailSender sender, String from) {
        this(sender, from, false);
    }

    @Autowired
    public RegistrationMailer(
            JavaMailSender sender,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.async:true}") boolean async) {
        this.sender = sender;
        this.from = from;
        this.async = async;
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
        if (!async) {
            deliverSync(message);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> deliverAsync(message), MAIL_EXECUTOR);
                }
            });
        } else {
            CompletableFuture.runAsync(() -> deliverAsync(message), MAIL_EXECUTOR);
        }
    }

    private void deliverSync(SimpleMailMessage message) {
        try {
            sender.send(message);
        } catch (MailException ex) {
            LOG.warn("Verification email delivery failed: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.MAIL_DELIVERY_FAILED);
        }
    }

    private void deliverAsync(SimpleMailMessage message) {
        try {
            sender.send(message);
        } catch (MailException ex) {
            LOG.warn("Async verification email delivery failed: {}", ex.getClass().getSimpleName());
        }
    }
}
