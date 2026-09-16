package com.clinica.fisioterapia.domain.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de auditoria. NUNCA debe contener contrasenas ni tokens.
 */
public record AuditLog(
        UUID id,
        UUID userId,
        AuditAction action,
        String entity,
        String entityId,
        Map<String, Object> payloadBefore,
        Map<String, Object> payloadAfter,
        Instant createdAt) {

    public static AuditLog of(UUID userId, AuditAction action, String entity, String entityId,
                              Map<String, Object> payloadBefore, Map<String, Object> payloadAfter,
                              Instant createdAt) {
        return new AuditLog(UUID.randomUUID(), userId, action, entity, entityId,
                payloadBefore, payloadAfter, createdAt);
    }
}