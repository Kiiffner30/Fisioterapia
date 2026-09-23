package com.clinica.fisioterapia.infrastructure.persistence;

import com.clinica.fisioterapia.domain.audit.AuditLog;
import com.clinica.fisioterapia.domain.audit.AuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementacion JPA del puerto {@link AuditLogRepository}.
 */
@Component
public class JpaAuditLogRepository implements AuditLogRepository {

    private final AuditLogJpaRepository delegate;
    private final AuditLogMapper mapper;

    public JpaAuditLogRepository(AuditLogJpaRepository delegate, AuditLogMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AuditLog save(AuditLog log) {
        JpaAuditLogEntity saved = delegate.save(mapper.toEntity(log));
        return mapper.toDomain(saved);
    }
}