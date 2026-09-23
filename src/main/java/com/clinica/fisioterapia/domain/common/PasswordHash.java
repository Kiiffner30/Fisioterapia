package com.clinica.fisioterapia.domain.common;

/**
 * Value Object que representa un hash de contrasena (BCrypt).
 * El dominio solo manipula el hash; el algoritmo de hashing vive en infraestructura.
 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PasswordHash no puede estar vacío");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}