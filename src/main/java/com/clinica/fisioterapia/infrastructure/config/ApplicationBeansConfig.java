package com.clinica.fisioterapia.infrastructure.config;

import com.clinica.fisioterapia.application.audit.AuditService;
import com.clinica.fisioterapia.application.auth.AuthenticationService;
import com.clinica.fisioterapia.application.auth.TokenProvider;
import com.clinica.fisioterapia.application.user.ChangeUserStatusUseCase;
import com.clinica.fisioterapia.application.user.CreateUserUseCase;
import com.clinica.fisioterapia.application.user.FindUserByIdUseCase;
import com.clinica.fisioterapia.domain.auth.RefreshTokenRepository;
import com.clinica.fisioterapia.domain.audit.AuditLogRepository;
import com.clinica.fisioterapia.domain.common.Clock;
import com.clinica.fisioterapia.domain.user.PasswordEncoder;
import com.clinica.fisioterapia.domain.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensambla los casos de uso (capa de aplicacion) con sus dependencias.
 */
@Configuration
public class ApplicationBeansConfig {

    @Bean
    public AuditService auditService(AuditLogRepository auditLogRepository, Clock clock) {
        return new AuditService(auditLogRepository, clock);
    }

    @Bean
    public AuthenticationService authenticationService(
            UserRepository userRepository, PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider, RefreshTokenRepository refreshTokenRepository,
            AuditService auditService, Clock clock, JwtProperties jwtProperties) {
        return new AuthenticationService(
                userRepository, passwordEncoder, tokenProvider, refreshTokenRepository,
                auditService, clock, jwtProperties.refreshExpiration());
    }

    @Bean
    public CreateUserUseCase createUserUseCase(
            UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuditService auditService, Clock clock) {
        return new CreateUserUseCase(userRepository, passwordEncoder, auditService, clock);
    }

    @Bean
    public ChangeUserStatusUseCase changeUserStatusUseCase(
            UserRepository userRepository, AuditService auditService, Clock clock) {
        return new ChangeUserStatusUseCase(userRepository, auditService, clock);
    }

    @Bean
    public FindUserByIdUseCase findUserByIdUseCase(UserRepository userRepository) {
        return new FindUserByIdUseCase(userRepository);
    }
}