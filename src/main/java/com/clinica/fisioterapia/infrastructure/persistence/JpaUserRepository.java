package com.clinica.fisioterapia.infrastructure.persistence;

import com.clinica.fisioterapia.domain.common.Email;
import com.clinica.fisioterapia.domain.user.User;
import com.clinica.fisioterapia.domain.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementacion JPA del puerto {@link UserRepository}.
 */
@Component
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository delegate;
    private final UserMapper mapper;

    public JpaUserRepository(UserJpaRepository delegate, UserMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(Email email) {
        return delegate.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return delegate.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return delegate.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public User save(User user) {
        JpaUserEntity saved = delegate.save(mapper.toEntity(user));
        return mapper.toDomain(saved);
    }
}