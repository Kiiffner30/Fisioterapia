package com.clinica.fisioterapia.domain.user;

import com.clinica.fisioterapia.domain.common.Email;
import com.clinica.fisioterapia.domain.common.PasswordHash;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad pura del dominio. No contiene anotaciones JPA ni dependencias de Spring.
 */
public class User {

    private final UUID id;
    private String name;
    private Email email;
    private PasswordHash passwordHash;
    private RoleName role;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(UUID id, String name, Email email, PasswordHash passwordHash,
                RoleName role, boolean active, Instant createdAt, Instant updatedAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        this.id = Objects.requireNonNull(id, "id");
        this.name = name.trim();
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static User create(UUID id, String name, Email email, PasswordHash passwordHash,
                              RoleName role, Instant now) {
        return new User(id, name, email, passwordHash, role, true, now, now);
    }

    public void changeStatus(boolean newStatus, Instant when) {
        if (this.active == newStatus) {
            return;
        }
        this.active = newStatus;
        this.updatedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Email getEmail() {
        return email;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public RoleName getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}