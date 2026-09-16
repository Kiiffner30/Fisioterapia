package com.clinica.fisioterapia.domain.common;

import java.util.regex.Pattern;

/**
 * Value Object de correo electronico. Se normaliza (minusculas, sin espacios)
 * y valida el formato.
 */
public record Email(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El email no puede estar vacío");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Formato de email inválido: " + value);
        }
    }

    /**
     * Factory que normaliza el valor antes de instanciar el Value Object.
     */
    public static Email of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("El email no puede estar vacío");
        }
        return new Email(raw.trim().toLowerCase());
    }

    @Override
    public String toString() {
        return value;
    }
}