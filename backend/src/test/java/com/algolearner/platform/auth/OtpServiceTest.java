package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpChallengeRepository otpRepository;

    @Mock
    private AuthMailPort mailPort;

    private Clock clock;
    private Instant now;
    private OtpService otpService;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-10T12:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        otpService = new OtpService(otpRepository, mailPort, clock);

        account = AccountEntity.builder()
                .id(UUID.randomUUID())
                .username("teststudent")
                .email("test@example.com")
                .role(AccountRole.STUDENT)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();
    }

    @Test
    @DisplayName("FR-ACC-004: Should generate and send 6-digit OTP code")
    void generateAndSendOtpSuccess() {
        when(otpRepository.findLatestByAccountAndPurpose(account.getId(), OtpPurpose.REGISTER))
                .thenReturn(Optional.empty());

        otpService.generateAndSendOtp(account, OtpPurpose.REGISTER);

        ArgumentCaptor<OtpChallengeEntity> challengeCaptor = ArgumentCaptor.forClass(OtpChallengeEntity.class);
        verify(otpRepository).save(challengeCaptor.capture());

        OtpChallengeEntity saved = challengeCaptor.getValue();
        assertThat(saved.getPurpose()).isEqualTo(OtpPurpose.REGISTER);
        assertThat(saved.getCodeHash()).isNotBlank();
        assertThat(saved.getSentAt()).isEqualTo(now);
        assertThat(saved.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(10)));
        assertThat(saved.getConsumedAt()).isNull();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailPort).sendOtp(eq(account.getEmail()), eq(account.getUsername()), codeCaptor.capture(), eq(OtpPurpose.REGISTER), eq(saved.getExpiresAt()));

        String sentCode = codeCaptor.getValue();
        assertThat(sentCode).matches("^\\d{6}$");
    }

    @Test
    @DisplayName("FR-ACC-004 AC-04: Resend within 60s cooldown must be rejected")
    void resendWithinCooldownRejected() {
        OtpChallengeEntity recent = OtpChallengeEntity.builder()
                .sentAt(now.minusSeconds(30)) // sent 30s ago, cooldown still active
                .build();

        when(otpRepository.findLatestByAccountAndPurpose(account.getId(), OtpPurpose.REGISTER))
                .thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> otpService.generateAndSendOtp(account, OtpPurpose.REGISTER))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.OTP_COOLDOWN_ACTIVE.getCode()));

        verify(otpRepository, never()).save(any());
        verify(mailPort, never()).sendOtp(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("FR-ACC-004 AC-01: Valid OTP code should be verified and marked consumed")
    void verifyValidOtpSuccess() {
        // Setup challenge
        when(otpRepository.findLatestByAccountAndPurpose(account.getId(), OtpPurpose.REGISTER))
                .thenReturn(Optional.empty());
        otpService.generateAndSendOtp(account, OtpPurpose.REGISTER);

        ArgumentCaptor<OtpChallengeEntity> challengeCaptor = ArgumentCaptor.forClass(OtpChallengeEntity.class);
        verify(otpRepository).save(challengeCaptor.capture());
        OtpChallengeEntity savedChallenge = challengeCaptor.getValue();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailPort).sendOtp(any(), any(), codeCaptor.capture(), any(), any());
        String generatedCode = codeCaptor.getValue();

        when(otpRepository.findActiveByAccountAndPurpose(account.getId(), OtpPurpose.REGISTER, now))
                .thenReturn(Optional.of(savedChallenge));

        otpService.verifyOtp(account, generatedCode, OtpPurpose.REGISTER);

        assertThat(savedChallenge.getConsumedAt()).isEqualTo(now);
        verify(otpRepository, atLeast(2)).save(savedChallenge);
    }

    @Test
    @DisplayName("FR-ACC-004 AC-03: Wrong OTP should increment failedAttempts and reject after 5 times")
    void verifyWrongOtpIncrementsAttempts() {
        when(otpRepository.findLatestByAccountAndPurpose(account.getId(), OtpPurpose.REGISTER))
                .thenReturn(Optional.empty());
        otpService.generateAndSendOtp(account, OtpPurpose.REGISTER);

        ArgumentCaptor<OtpChallengeEntity> challengeCaptor = ArgumentCaptor.forClass(OtpChallengeEntity.class);
        verify(otpRepository).save(challengeCaptor.capture());
        OtpChallengeEntity challenge = challengeCaptor.getValue();

        when(otpRepository.findActiveByAccountAndPurpose(account.getId(), OtpPurpose.REGISTER, now))
                .thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> otpService.verifyOtp(account, "000000", OtpPurpose.REGISTER))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID.getCode()));

        assertThat(challenge.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-ACC-004 AC-02: Expired OTP is rejected")
    void expiredOtpRejected() {
        when(otpRepository.findActiveByAccountAndPurpose(account.getId(), OtpPurpose.REGISTER, now))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp(account, "123456", OtpPurpose.REGISTER))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.OTP_EXPIRED.getCode()));
    }
}

