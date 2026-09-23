package com.clinica.fisioterapia.infrastructure.security;

import com.clinica.fisioterapia.infrastructure.config.JwtProperties;
import com.clinica.fisioterapia.domain.user.RoleName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET = "secreto-de-prueba-suficientemente-largo-1234567890";
    private static final UUID USER_ID = UUID.randomUUID();

    private JwtTokenProvider provider(Duration accessTtl) {
        return new JwtTokenProvider(new JwtProperties(SECRET, accessTtl, Duration.ofDays(30)));
    }

    @Test
    void emiteYValidaAccessToken() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(15));
        var token = provider.issueAccessToken(USER_ID, "user@clinica.test", RoleName.ADMIN);

        Optional<com.clinica.fisioterapia.application.auth.TokenProvider.Claims> parsed = provider.parse(token.value());

        assertTrue(parsed.isPresent());
        assertEquals(USER_ID, parsed.get().userId());
        assertEquals("user@clinica.test", parsed.get().email());
        assertEquals(RoleName.ADMIN, parsed.get().role());
    }

    @Test
    void rechazaTokenConOtraClave() {
        JwtTokenProvider issuer = provider(Duration.ofMinutes(15));
        JwtTokenProvider other = new JwtTokenProvider(
                new JwtProperties("otra-clave-distinta-1234567890abcdefghijkl", Duration.ofMinutes(15), Duration.ofDays(30)));

        String token = issuer.issueAccessToken(USER_ID, "user@clinica.test", RoleName.ADMIN).value();

        assertTrue(other.parse(token).isEmpty());
    }

    @Test
    void rechazaTokenExpirado() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(-1));
        String token = provider.issueAccessToken(USER_ID, "user@clinica.test", RoleName.ADMIN).value();

        assertTrue(provider.parse(token).isEmpty());
    }

    @Test
    void rechazaCadenaQueNoEsToken() {
        assertTrue(provider(Duration.ofMinutes(15)).parse("esto-no-es-un-jwt").isEmpty());
    }

    @Test
    void cadaRefreshTokenEsUnicoYElHashEsConsistente() {
        var one = provider(Duration.ofMinutes(15));
        var pair1 = one.issueRefreshToken();
        var pair2 = one.issueRefreshToken();

        assertNotEquals(pair1.rawValue(), pair2.rawValue());
        assertEquals(pair1.sha256Hash(), one.sha256(pair1.rawValue()));
        assertEquals(64, pair1.sha256Hash().length());
    }
}