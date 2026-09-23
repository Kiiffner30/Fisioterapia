package com.clinica.fisioterapia.infrastructure.persistence;

import com.clinica.fisioterapia.domain.auth.RefreshToken;
import com.clinica.fisioterapia.domain.auth.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementacion JPA del puerto {@link RefreshTokenRepository}.
 */
@Component
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository delegate;
    private final RefreshTokenMapper mapper;

    public JpaRefreshTokenRepository(RefreshTokenJpaRepository delegate, RefreshTokenMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken token) {
        JpaRefreshTokenEntity saved = delegate.save(mapper.toEntity(token));
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return delegate.findByTokenHash(tokenHash).map(mapper::toDomain);
    }
}