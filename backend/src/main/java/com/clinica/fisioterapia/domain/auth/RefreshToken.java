package com.clinica.fisioterapia.domain.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresh token rotativo. Se persiste unicamente el hash SHA-256 del token.
 */
public class RefreshToken {

    private final UUID id;
    private final String tokenHash;
    private final UUID userId;
    private final Instant expiresAt;
    private Instant revokedAt;
    private final Instant createdAt;

    public RefreshToken(UUID id, String tokenHash, UUID userId,
                        Instant expiresAt, Instant revokedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.revokedAt = revokedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static RefreshToken issue(UUID id, String tokenHash, UUID userId, Instant expiresAt, Instant now) {
        return new RefreshToken(id, tokenHash, userId, expiresAt, null, now);
    }

    public void revoke(Instant when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }

    public boolean isValid(Instant at) {
        return revokedAt == null && at.isBefore(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}