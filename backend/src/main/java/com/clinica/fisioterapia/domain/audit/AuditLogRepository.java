package com.clinica.fisioterapia.domain.audit;

/**
 * Puerto de persistencia de registros de auditoria.
 */
public interface AuditLogRepository {

    AuditLog save(AuditLog log);
}