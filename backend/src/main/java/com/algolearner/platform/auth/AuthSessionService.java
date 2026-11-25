package com.algolearner.platform.auth;

import static com.algolearner.platform.auth.AuthDtos.*;
import com.algolearner.platform.common.exception.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("internal-auth")
@Transactional(readOnly = true)
public class AuthSessionService {
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private final AuthSessionRepository sessions;
    private final AccountRepository accounts;
    private final JwtEncoder tokens;
    private final Clock clock;
    private final String issuer;

    public AuthSessionService(AuthSessionRepository sessions, AccountRepository accounts, JwtEncoder tokens,
            Clock clock, @Value("${app.security.internal-issuer}") String issuer) {
        this.sessions = sessions; this.accounts = accounts; this.tokens = tokens; this.clock = clock; this.issuer = issuer;
    }

    @Transactional
    public AuthResult issue(AccountEntity account, boolean rememberMe) {
        return issue(account, rememberMe, null);
    }

    @Transactional
    public AuthResult issue(AccountEntity account, boolean rememberMe, String userAgent) {
        var session = new AuthSessionEntity();
        session.setId(UUID.randomUUID());
        session.setAccount(account);
        session.setRememberMe(rememberMe);
        session.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length())));
        Instant now = clock.instant();
        session.setExpiresAt(now.plus(Duration.ofDays(rememberMe ? 30 : 7)));
        return rotate(session, now);
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        var session = lockedSession(refreshToken);
        if (session == null || session.getRevokedAt() != null || !session.getExpiresAt().isAfter(clock.instant())
                || session.getAccount().getStatus() != AccountStatus.ACTIVE)
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        return rotate(session, clock.instant());
    }

    @Transactional
    public void logout(String refreshToken) {
        var session = lockedSession(refreshToken);
        if (session != null) session.setRevokedAt(clock.instant());
    }

    @Transactional
    public void logoutCurrent(UUID accountId, UUID sessionId) {
        accounts.findForUpdateById(accountId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_ACCESS));
        sessions.findById(sessionId)
                .filter(session -> session.getAccount().getId().equals(accountId))
                .ifPresent(session -> session.setRevokedAt(clock.instant()));
    }

    @Transactional
    public void revokeOtherSessions(UUID accountId, UUID keepSessionId) {
        for (var session : sessions.findByAccountIdAndRevokedAtIsNull(accountId)) {
            if (!session.getId().equals(keepSessionId)) session.setRevokedAt(clock.instant());
        }
    }

    public List<SessionView> list(UUID accountId, UUID currentSessionId) {
        Instant now = clock.instant();
        return sessions.findByAccountIdAndRevokedAtIsNull(accountId).stream()
                .filter(session -> session.getExpiresAt().isAfter(now))
                .sorted(Comparator.comparing(AuthSessionEntity::getCreatedAt).reversed())
                .map(session -> new SessionView(session.getId(), session.getCreatedAt(), session.getExpiresAt(),
                        session.isRememberMe(), session.getId().equals(currentSessionId), session.getUserAgent()))
                .toList();
    }

    @Transactional
    public void revoke(UUID accountId, UUID currentSessionId, UUID targetSessionId) {
        if (currentSessionId.equals(targetSessionId))
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Use sign out to revoke the current session.");
        accounts.findForUpdateById(accountId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_ACCESS));
        var target = sessions.findById(targetSessionId)
                .filter(session -> session.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (target.getRevokedAt() == null) target.setRevokedAt(clock.instant());
    }

    @Transactional
    public void logoutAll(UUID accountId) {
        accounts.findForUpdateById(accountId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_ACCESS));
        revokeOtherSessions(accountId, null);
    }

    private AuthSessionEntity lockedSession(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) return null;
        String hash = SecretTokens.hash(raw);
        var found = sessions.findAccountIdByRefreshHash(hash);
        if (found.isEmpty()) return null;
        // Every session/password mutation locks the account first to serialize reset versus refresh.
        accounts.findForUpdateById(found.get()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_ACCESS));
        return sessions.findForUpdateByHash(hash).orElse(null);
    }

    private AuthResult rotate(AuthSessionEntity session, Instant now) {
        String raw = SecretTokens.create();
        session.setRefreshHash(SecretTokens.hash(raw));
        sessions.save(session);
        var account = session.getAccount();
        String token = tokens.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(issuer).audience(List.of("algolearner"))
                        .subject(account.getId().toString()).claim("sid", session.getId().toString())
                        .issuedAt(now).expiresAt(now.plus(ACCESS_LIFETIME)).build())).getTokenValue();
        return new AuthResult(token, new AuthUser(account.getId(), account.getUsername(), account.getEmail(),
                account.getRole(), account.getStatus().name()), ACCESS_LIFETIME.toSeconds(), raw,
                Math.max(0, Duration.between(now, session.getExpiresAt()).toSeconds()), session.isRememberMe());
    }
}
