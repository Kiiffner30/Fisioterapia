package com.clinica.fisioterapia.infrastructure.config;

import com.clinica.fisioterapia.application.auth.TokenProvider;
import com.clinica.fisioterapia.infrastructure.security.HttpErrorWriter;
import com.clinica.fisioterapia.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Configuracion stateless de Spring Security basada en JWT.
 * La autorizacion se valida SIEMPRE en el backend (roles via @PreAuthorize).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final List<String> PUBLIC_PATHS =
            List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    private final HttpErrorWriter errorWriter;

    public SecurityConfig(HttpErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenProvider tokenProvider) {
        return new JwtAuthenticationFilter(tokenProvider, errorWriter, PUBLIC_PATHS);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(response, 401, "No autenticado", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(response, 403, "Acceso denegado", request.getRequestURI())));
        return http.build();
    }
}