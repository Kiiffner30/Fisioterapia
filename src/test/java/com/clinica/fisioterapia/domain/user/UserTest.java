package com.clinica.fisioterapia.domain.user;

import com.clinica.fisioterapia.domain.common.Email;
import com.clinica.fisioterapia.domain.common.PasswordHash;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    private User newUser() {
        return User.create(UUID.randomUUID(), "María Pérez", Email.of("maria@clinica.com"),
                new PasswordHash("hash"), RoleName.ATENDENTE, NOW);
    }

    @Test
    void nuevoUsuarioNaceActivo() {
        assertTrue(newUser().isActive());
    }

    @Test
    void cambiarEstadoActualizaTimestamp() {
        User user = newUser();
        Instant after = NOW.plusSeconds(60);
        user.changeStatus(false, after);
        assertFalse(user.isActive());
        assertEquals(after, user.getUpdatedAt());
    }

    @Test
    void cambiarEstadoAlMismoValorEsNoOp() {
        User user = newUser();
        user.changeStatus(true, NOW.plusSeconds(60));
        assertEquals(NOW, user.getUpdatedAt());
    }

    @Test
    void noAceptaNombreVacio() {
        assertThrows(IllegalArgumentException.class, () ->
                User.create(UUID.randomUUID(), "   ", Email.of("x@clinica.com"),
                        new PasswordHash("hash"), RoleName.ADMIN, NOW));
    }
}