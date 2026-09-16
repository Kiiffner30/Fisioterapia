package com.clinica.fisioterapia.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Utilidad para escribir respuestas de error JSON de forma consistente.
 */
@Component
public class HttpErrorWriter {

    private final ObjectMapper objectMapper;

    public HttpErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "status", status,
                "error", message,
                "path", path == null ? "" : path)));
    }
}