package com.clinica.fisioterapia.infrastructure.config;

import com.clinica.fisioterapia.domain.common.Clock;
import com.clinica.fisioterapia.domain.common.Email;
import com.clinica.fisioterapia.domain.common.PasswordHash;
import com.clinica.fisioterapia.domain.user.PasswordEncoder;
import com.clinica.fisioterapia.domain.user.RoleName;
import com.clinica.fisioterapia.domain.user.User;
import com.clinica.fisioterapia.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Crea el usuario administrador inicial (bootstrap) si no existe.
 * Las credenciales vienen de variables de entorno (BOOTSTRAP_ADMIN_EMAIL /
 * BOOTSTRAP_ADMIN_PASSWORD); en desarrollo sin variable, usa los valores de
 * aplicacion.yml documentados en el README. JAMAS versionar credenciales reales.
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties properties;
    private final Clock clock;

    public AdminBootstrapRunner(UserRepository users, PasswordEncoder passwordEncoder,
                                BootstrapProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        Email email = Email.of(properties.adminEmail());
        if (users.findByEmail(email).isPresent()) {
            return;
        }
        String password = properties.adminPassword();
        if (password == null || password.length() < 8) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD inválida o ausente: no se crea el administrador inicial.");
            return;
        }
        User admin = User.create(
                UUID.randomUUID(),
                "Administrador",
                email,
                new PasswordHash(passwordEncoder.encode(password)),
                RoleName.ADMIN,
                clock.now());
        users.save(admin);
        log.info("Usuario administrador inicial creado: {}", email);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}