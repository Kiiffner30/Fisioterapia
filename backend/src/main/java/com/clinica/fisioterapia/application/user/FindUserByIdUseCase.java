package com.clinica.fisioterapia.application.user;

import com.clinica.fisioterapia.application.NotFoundException;
import com.clinica.fisioterapia.domain.user.User;
import com.clinica.fisioterapia.domain.user.UserRepository;

import java.util.UUID;

/**
 * Caso de uso: consultar un usuario por id (para /api/me).
 */
public class FindUserByIdUseCase {

    private final UserRepository users;

    public FindUserByIdUseCase(UserRepository users) {
        this.users = users;
    }

    public User execute(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }
}