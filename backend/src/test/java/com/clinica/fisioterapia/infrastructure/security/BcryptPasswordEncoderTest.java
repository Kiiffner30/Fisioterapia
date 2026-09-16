package com.clinica.fisioterapia.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcryptPasswordEncoderTest {

    private final BcryptPasswordEncoder encoder = new BcryptPasswordEncoder();

    @Test
    void hashGeneradoNoEsTextoPlano() {
        String hash = encoder.encode("MiClave-123");
        assertFalse(hash.contains("MiClave-123"));
        assertTrue(hash.startsWith("$2a$"));
    }

    @Test
    void verificaContrasenaCorrecta() {
        String hash = encoder.encode("MiClave-123");
        assertTrue(encoder.matches("MiClave-123", hash));
    }

    @Test
    void rechazaContrasenaIncorrecta() {
        String hash = encoder.encode("MiClave-123");
        assertFalse(encoder.matches("OtraClave-456", hash));
    }

    @Test
    void saltaSalAleatoriaPorHash() {
        String hash1 = encoder.encode("MiClave-123");
        String hash2 = encoder.encode("MiClave-123");
        assertNotEquals(hash1, hash2);
        assertTrue(encoder.matches("MiClave-123", hash1));
        assertTrue(encoder.matches("MiClave-123", hash2));
    }
}