package com.clinica.fisioterapia.domain.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de refresh tokens (almacenados con hash).
 */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}