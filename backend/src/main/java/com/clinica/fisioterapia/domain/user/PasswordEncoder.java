package com.clinica.fisioterapia.domain.user;

/**
 * Puerto para el cifrado/verificacion de contrasenas.
 * Implementado en infraestructura con BCrypt.
 */
public interface PasswordEncoder {

    String encode(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, String encodedPassword);
}