package com.clinica.fisioterapia.infrastructure.persistence;

import com.clinica.fisioterapia.domain.audit.AuditAction;
import com.clinica.fisioterapia.domain.audit.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLog toDomain(JpaAuditLogEntity entity) {
        return new AuditLog(
                entity.getId(),
                entity.getUserId(),
                AuditAction.valueOf(entity.getAction()),
                entity.getEntity(),
                entity.getEntityId(),
                entity.getPayloadBefore(),
                entity.getPayloadAfter(),
                entity.getCreatedAt());
    }

    public JpaAuditLogEntity toEntity(AuditLog log) {
        JpaAuditLogEntity entity = new JpaAuditLogEntity();
        entity.setId(log.id());
        entity.setUserId(log.userId());
        entity.setAction(log.action().name());
        entity.setEntity(log.entity());
        entity.setEntityId(log.entityId());
        entity.setPayloadBefore(log.payloadBefore());
        entity.setPayloadAfter(log.payloadAfter());
        entity.setCreatedAt(log.createdAt());
        return entity;
    }
}