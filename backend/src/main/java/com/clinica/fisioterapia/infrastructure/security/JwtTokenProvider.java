package com.clinica.fisioterapia.infrastructure.security;

import com.clinica.fisioterapia.application.auth.TokenProvider;
import com.clinica.fisioterapia.domain.user.RoleName;
import com.clinica.fisioterapia.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Emision y validacion de JWT (access token) y generacion de refresh tokens
 * opacos con hash SHA-256.
 */
@Component
public class JwtTokenProvider implements TokenProvider {

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = properties.accessExpiration();
    }

    @Override
    public AccessToken issueAccessToken(UUID userId, String email, RoleName role) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer("fisioterapia")
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
        return new AccessToken(token, accessTtl.toSeconds());
    }

    @Override
    public Optional<TokenProvider.Claims> parse(String token) {
        try {
            io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws = Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            io.jsonwebtoken.Claims payload = jws.getPayload();
            if (payload.getExpiration() == null
                    || payload.getExpiration().toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(new TokenProvider.Claims(
                    UUID.fromString(payload.getSubject()),
                    payload.get("email", String.class),
                    RoleName.from(payload.get("role", String.class))));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @Override
    public RefreshTokenPair issueRefreshToken() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return new RefreshTokenPair(raw, sha256(raw));
    }

    @Override
    public String sha256(String rawValue) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawValue.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El proveedor SHA-256 no está disponible", e);
        }
        return HexFormat.of().formatHex(digest);
    }
}