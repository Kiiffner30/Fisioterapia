package com.clinica.fisioterapia.application.auth;

import com.clinica.fisioterapia.application.audit.AuditService;
import com.clinica.fisioterapia.domain.audit.AuditAction;
import com.clinica.fisioterapia.domain.auth.RefreshToken;
import com.clinica.fisioterapia.domain.auth.RefreshTokenRepository;
import com.clinica.fisioterapia.domain.common.Clock;
import com.clinica.fisioterapia.domain.common.Email;
import com.clinica.fisioterapia.domain.user.PasswordEncoder;
import com.clinica.fisioterapia.domain.user.User;
import com.clinica.fisioterapia.domain.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: autenticacion (login, refresh rotativo, logout).
 *
 * <p>Los payloads de auditoria contienen unicamente datos no sensibles
 * (email), nunca contrasenas ni tokens.</p>
 */
public class AuthenticationService {

    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Credenciales inválidas");
        }
    }

    public static final class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() {
            super("Sesión inválida o expirada");
        }
    }

    public static final class InactiveUserException extends RuntimeException {
        public InactiveUserException() {
            super("El usuario está inactivo");
        }
    }

    public record LoginResult(String accessToken, long expiresInSeconds, String refreshToken, User user) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final Clock clock;
    private final Duration refreshTtl;

    public AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder,
                                 TokenProvider tokenProvider, RefreshTokenRepository refreshTokens,
                                 AuditService audit, Clock clock, Duration refreshTtl) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public LoginResult login(String email, String password) {
        User user = users.findByEmail(Email.of(email))
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) {
            throw new InactiveUserException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash().value())) {
            audit.record(null, AuditAction.LOGIN_FAILED, "USER", null,
                    Map.of("email", user.getEmail().value()), Map.of());
            throw new InvalidCredentialsException();
        }
        audit.record(user.getId(), AuditAction.LOGIN, "USER", user.getId().toString(),
                null, Map.of("email", user.getEmail().value()));
        return issueTokens(user);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshToken stored = refreshTokens.findByTokenHash(tokenProvider.sha256(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.now();
        if (!stored.isValid(now)) {
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(stored.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        if (!user.isActive()) {
            throw new InactiveUserException();
        }
        stored.revoke(now);
        refreshTokens.save(stored);
        audit.record(user.getId(), AuditAction.REFRESH, "USER", user.getId().toString(), null, Map.of());
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(tokenProvider.sha256(rawRefreshToken)).ifPresent(stored -> {
            UUID userId = stored.getUserId();
            // Auditoria del logout: solo el id del usuario (nunca contrasena ni tokens).
            audit.record(userId, AuditAction.LOGOUT, "USER", userId.toString(), null, Map.of());
            stored.revoke(clock.now());
            refreshTokens.save(stored);
        });
    }

    private LoginResult issueTokens(User user) {
        TokenProvider.AccessToken access = tokenProvider.issueAccessToken(
                user.getId(), user.getEmail().value(), user.getRole());
        TokenProvider.RefreshTokenPair refresh = tokenProvider.issueRefreshToken();
        refreshTokens.save(RefreshToken.issue(
                UUID.randomUUID(), refresh.sha256Hash(), user.getId(),
                clock.now().plus(refreshTtl), clock.now()));
        return new LoginResult(access.value(), access.expiresInSeconds(), refresh.rawValue(), user);
    }
}