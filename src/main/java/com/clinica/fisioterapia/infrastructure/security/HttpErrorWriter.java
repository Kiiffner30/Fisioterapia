package com.clinica.fisioterapia.infrastructure.security;

import com.clinica.fisioterapia.presentation.rest.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Utilidad para escribir respuestas de error JSON de forma consistente.
 *
 * <p>Emite EXACTAMENTE el mismo cuerpo que {@link ApiError} (usado por
 * {@code GlobalExceptionHandler}), de modo que el cliente tenga un unico
 * contrato de error: {@code timestamp, status, error, message, path}.</p>
 */
@Component
public class HttpErrorWriter {

    private final ObjectMapper objectMapper;

    public HttpErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Escribe el error con el formato unificado {@link ApiError}. El texto recibido se usa como
     * {@code error} y — al no existir un detalle adicional — tambien como {@code message}, igual que
     * hace {@code GlobalExceptionHandler} cuando no se provee mensaje especifico.
     */
    public void write(HttpServletResponse response, int status, String error, String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(Instant.now(), status, error, error, path == null ? "" : path);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}