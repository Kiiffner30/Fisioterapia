package com.clinica.fisioterapia.application.auth;

import com.clinica.fisioterapia.domain.user.RoleName;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto para emision/validacion de tokens (JWT access + refresh opaco).
 * Implementado en infrastructure con jjwt + SHA-256.
 */
public interface TokenProvider {

    AccessToken issueAccessToken(UUID userId, String email, RoleName role);

    Optional<Claims> parse(String token);

    RefreshTokenPair issueRefreshToken();

    String sha256(String rawValue);

    record AccessToken(String value, long expiresInSeconds) {
    }

    record Claims(UUID userId, String email, RoleName role) {
    }

    record RefreshTokenPair(String rawValue, String sha256Hash) {
    }
}