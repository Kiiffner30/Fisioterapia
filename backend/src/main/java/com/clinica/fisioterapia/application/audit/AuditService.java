package com.clinica.fisioterapia.application.audit;

import com.clinica.fisioterapia.domain.audit.AuditAction;
import com.clinica.fisioterapia.domain.audit.AuditLogRepository;
import com.clinica.fisioterapia.domain.audit.AuditLog;
import com.clinica.fisioterapia.domain.common.Clock;

import java.util.Map;
import java.util.UUID;

/**
 * Servicio de auditoria centralizado. Registra acciones sensibles sin exponer
 * contrasenas ni tokens en los payloads.
 */
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogs, Clock clock) {
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    public void record(UUID userId, AuditAction action, String entity, String entityId,
                       Map<String, Object> payloadBefore, Map<String, Object> payloadAfter) {
        auditLogs.save(AuditLog.of(userId, action, entity, entityId, payloadBefore, payloadAfter, clock.now()));
    }
}