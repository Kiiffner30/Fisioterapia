package com.clinica.fisioterapia.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AuditLogJpaRepository extends JpaRepository<JpaAuditLogEntity, UUID> {

    List<JpaAuditLogEntity> findAllByAction(String action);
}