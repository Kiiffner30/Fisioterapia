package com.clinica.fisioterapia.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface RefreshTokenJpaRepository extends JpaRepository<JpaRefreshTokenEntity, UUID> {

    Optional<JpaRefreshTokenEntity> findByTokenHash(String tokenHash);
}