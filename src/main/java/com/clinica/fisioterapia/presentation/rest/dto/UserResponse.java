package com.clinica.fisioterapia.presentation.rest.dto;

import com.clinica.fisioterapia.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Representacion de un usuario para la API. NUNCA expone el passwordHash.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        String role,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail().value(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt());
    }
}