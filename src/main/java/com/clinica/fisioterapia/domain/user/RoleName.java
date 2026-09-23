package com.clinica.fisioterapia.domain.user;

import java.util.Arrays;

/**
 * Perfiles del sistema.
 */
public enum RoleName {

    ADMIN,
    ATENDENTE,
    FISIOTERAPEUTA;

    public static RoleName from(String value) {
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(value != null ? value.trim() : ""))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil inválido: " + value));
    }
}