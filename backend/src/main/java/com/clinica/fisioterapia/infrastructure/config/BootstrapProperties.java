package com.clinica.fisioterapia.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales del usuario administrador inicial (bootstrap).
 * Se leen de variables de entorno; nunca se versionan credenciales reales.
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminEmail, String adminPassword) {
}