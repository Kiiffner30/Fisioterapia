package com.clinica.fisioterapia.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de la cookie HTTP (refresh token).
 */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(String name, String path, boolean secure, String sameSite) {
}