package com.clinica.fisioterapia.infrastructure.security;

import com.clinica.fisioterapia.domain.user.RoleName;

import java.util.UUID;

/**
 * Principal de sesion autenticada (poblado por JwtAuthenticationFilter).
 */
public record AuthenticatedUser(UUID userId, String email, RoleName role) {
}