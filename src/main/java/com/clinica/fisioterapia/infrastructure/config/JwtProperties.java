package com.clinica.fisioterapia.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propiedades JWT (secret, vencimiento del access/refresh token).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessExpiration, Duration refreshExpiration) {
}