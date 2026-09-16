package com.clinica.fisioterapia.application.user;

import com.clinica.fisioterapia.application.NotFoundException;
import com.clinica.fisioterapia.application.audit.AuditService;
import com.clinica.fisioterapia.domain.audit.AuditAction;
import com.clinica.fisioterapia.domain.common.Clock;
import com.clinica.fisioterapia.domain.user.User;
import com.clinica.fisioterapia.domain.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: activar/desactivar un usuario del sistema (solo ADMIN).
 */
public class ChangeUserStatusUseCase {

    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public ChangeUserStatusUseCase(UserRepository users, AuditService audit, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public User execute(UUID userId, boolean active, UUID actorId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        user.changeStatus(active, clock.now());
        users.save(user);
        Map<String, Object> before = Map.of("active", !active);
        Map<String, Object> after = Map.of("active", active);
        audit.record(actorId,
                active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                "USER", userId.toString(), before, after);
        return user;
    }
}