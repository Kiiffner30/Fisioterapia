package com.clinica.fisioterapia.presentation.rest.controllers;

import com.clinica.fisioterapia.application.auth.AuthenticationService;
import com.clinica.fisioterapia.application.user.FindUserByIdUseCase;
import com.clinica.fisioterapia.domain.user.User;
import com.clinica.fisioterapia.infrastructure.config.CookieProperties;
import com.clinica.fisioterapia.infrastructure.config.JwtProperties;
import com.clinica.fisioterapia.infrastructure.security.AuthenticatedUser;
import com.clinica.fisioterapia.presentation.rest.dto.LoginRequest;
import com.clinica.fisioterapia.presentation.rest.dto.TokenResponse;
import com.clinica.fisioterapia.presentation.rest.dto.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Optional;

/**
 * Autenticacion: login, refresh rotativo, logout y consulta del usuario actual.
 * El refresh token viaja en cookie HttpOnly. El access token se entrega en el body.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final FindUserByIdUseCase findUserById;
    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public AuthController(AuthenticationService authenticationService,
                          FindUserByIdUseCase findUserById,
                          CookieProperties cookieProperties,
                          JwtProperties jwtProperties) {
        this.authenticationService = authenticationService;
        this.findUserById = findUserById;
        this.cookieProperties = cookieProperties;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        AuthenticationService.LoginResult result = authenticationService.login(request.email(), request.password());
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(TokenResponse.of(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthenticationService.LoginResult result = authenticationService.refresh(readRefreshCookie(request));
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(TokenResponse.of(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(readRefreshCookie(request));
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = findUserById.execute(principal.userId());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    private String readRefreshCookie(HttpServletRequest request) {
        return Arrays.stream(Optional.ofNullable(request.getCookies()).orElseGet(() -> new Cookie[0]))
                .filter(cookie -> cookieProperties.name().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(cookieProperties.name(), rawRefreshToken)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(cookieProperties.path())
                .sameSite(cookieProperties.sameSite())
                .maxAge(jwtProperties.refreshExpiration().toSeconds())
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieProperties.name(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(cookieProperties.path())
                .sameSite(cookieProperties.sameSite())
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}