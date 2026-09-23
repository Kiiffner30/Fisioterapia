package com.clinica.fisioterapia.domain.user;

import com.clinica.fisioterapia.domain.common.Email;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de usuarios. La implementacion JPA vive en infrastructure.
 */
public interface UserRepository {

    Optional<User> findByEmail(Email email);

    Optional<User> findById(UUID id);

    List<User> findAll();

    User save(User user);
}