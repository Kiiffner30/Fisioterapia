package com.clinica.fisioterapia.presentation.rest.error;

import java.time.Instant;

/**
 * Cuerpo JSON uniforme para errores.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {
}