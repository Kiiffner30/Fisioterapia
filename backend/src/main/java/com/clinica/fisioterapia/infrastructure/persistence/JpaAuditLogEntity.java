package com.clinica.fisioterapia.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidad JPA para {@code audit_logs}.
 */
@Entity
@Table(name = "audit_logs")
public class JpaAuditLogEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 120)
    private String entity;

    @Column(name = "entity_id", length = 120)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_before", columnDefinition = "jsonb")
    private Map<String, Object> payloadBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_after", columnDefinition = "jsonb")
    private Map<String, Object> payloadAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JpaAuditLogEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Map<String, Object> getPayloadBefore() {
        return payloadBefore;
    }

    public void setPayloadBefore(Map<String, Object> payloadBefore) {
        this.payloadBefore = payloadBefore;
    }

    public Map<String, Object> getPayloadAfter() {
        return payloadAfter;
    }

    public void setPayloadAfter(Map<String, Object> payloadAfter) {
        this.payloadAfter = payloadAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}