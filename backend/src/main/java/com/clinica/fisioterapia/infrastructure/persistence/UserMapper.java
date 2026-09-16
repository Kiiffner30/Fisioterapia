package com.clinica.fisioterapia.infrastructure.persistence;

import com.clinica.fisioterapia.domain.common.Email;
import com.clinica.fisioterapia.domain.common.PasswordHash;
import com.clinica.fisioterapia.domain.user.RoleName;
import com.clinica.fisioterapia.domain.user.User;
import org.springframework.stereotype.Component;

/**
 * Convierte entre la entidad de dominio {@link User} y la entidad JPA.
 */
@Component
public class UserMapper {

    private final RoleJpaRepository roleJpaRepository;

    public UserMapper(RoleJpaRepository roleJpaRepository) {
        this.roleJpaRepository = roleJpaRepository;
    }

    public User toDomain(JpaUserEntity entity) {
        return new User(
                entity.getId(),
                entity.getName(),
                Email.of(entity.getEmail()),
                new PasswordHash(entity.getPasswordHash()),
                RoleName.valueOf(entity.getRole().getName()),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public JpaUserEntity toEntity(User user) {
        JpaUserEntity entity = new JpaUserEntity();
        entity.setId(user.getId());
        entity.setName(user.getName());
        entity.setEmail(user.getEmail().value());
        entity.setPasswordHash(user.getPasswordHash().value());
        entity.setRole(roleJpaRepository.findByName(user.getRole().name())
                .orElseThrow(() -> new IllegalStateException("Perfil no encontrado: " + user.getRole())));
        entity.setActive(user.isActive());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }
}