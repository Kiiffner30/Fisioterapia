package com.clinica.fisioterapia.application.user;

import com.clinica.fisioterapia.application.NotFoundException;
import com.clinica.fisioterapia.application.audit.AuditService;
import com.clinica.fisioterapia.domain.audit.AuditAction;
import com.clinica.fisioterapia.domain.common.Clock;
import com.clinica.fisioterapia.domain.common.Email;
import com.clinica.fisioterapia.domain.common.PasswordHash;
import com.clinica.fisioterapia.domain.user.PasswordEncoder;
import com.clinica.fisioterapia.domain.user.RoleName;
import com.clinica.fisioterapia.domain.user.User;
import com.clinica.fisioterapia.domain.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: alta de usuario del sistema (solo ADMIN).
 */
public class CreateUserUseCase {

    public record CreateUserCommand(String name, String email, String password, String role, UUID actorId) {
    }

    public static final class EmailAlreadyInUseException extends RuntimeException {
        public EmailAlreadyInUseException() {
            super("Ya existe un usuario con ese email");
        }
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public CreateUserUseCase(UserRepository users, PasswordEncoder passwordEncoder,
                             AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public User execute(CreateUserCommand command) {
        Email email = Email.of(command.email());
        if (users.findByEmail(email).isPresent()) {
            throw new EmailAlreadyInUseException();
        }
        String password = command.password();
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }
        User user = User.create(UUID.randomUUID(), command.name(), email,
                new PasswordHash(passwordEncoder.encode(password)),
                RoleName.from(command.role()), clock.now());
        users.save(user);
        audit.record(command.actorId(), AuditAction.USER_CREATED, "USER", user.getId().toString(),
                null, Map.of("email", email.value(), "role", user.getRole().name(), "active", true));
        return user;
    }
}