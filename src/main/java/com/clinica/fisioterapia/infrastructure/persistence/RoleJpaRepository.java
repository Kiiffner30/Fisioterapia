package com.clinica.fisioterapia.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface RoleJpaRepository extends JpaRepository<JpaRoleEntity, Long> {

    Optional<JpaRoleEntity> findByName(String name);
}