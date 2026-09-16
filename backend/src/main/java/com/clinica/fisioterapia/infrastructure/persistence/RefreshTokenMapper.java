package com.clinica.fisioterapia.infrastructure.persistence;

import com.clinica.fisioterapia.domain.auth.RefreshToken;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenMapper {

    public RefreshToken toDomain(JpaRefreshTokenEntity entity) {
        return new RefreshToken(
                entity.getId(),
                entity.getTokenHash(),
                entity.getUserId(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt());
    }

    public JpaRefreshTokenEntity toEntity(RefreshToken token) {
        JpaRefreshTokenEntity entity = new JpaRefreshTokenEntity();
        entity.setId(token.getId());
        entity.setUserId(token.getUserId());
        entity.setTokenHash(token.getTokenHash());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setRevokedAt(token.getRevokedAt());
        entity.setCreatedAt(token.getCreatedAt());
        return entity;
    }
}