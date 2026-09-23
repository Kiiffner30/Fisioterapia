package com.clinica.fisioterapia.presentation.rest.dto;

import com.clinica.fisioterapia.application.auth.AuthenticationService;

/**
 * Respuesta de login/refresh: access token + datos del usuario.
 * El refresh token viaja en cookie HttpOnly (no en el body).
 */
public record TokenResponse(
        String accessToken,
        long expiresInSeconds,
        UserResponse user) {

    public static TokenResponse of(AuthenticationService.LoginResult result) {
        return new TokenResponse(result.accessToken(), result.expiresInSeconds(), UserResponse.from(result.user()));
    }
}