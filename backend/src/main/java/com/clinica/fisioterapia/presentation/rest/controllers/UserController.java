package com.clinica.fisioterapia.presentation.rest.controllers;

import com.clinica.fisioterapia.application.user.ChangeUserStatusUseCase;
import com.clinica.fisioterapia.application.user.CreateUserUseCase;
import com.clinica.fisioterapia.infrastructure.security.AuthenticatedUser;
import com.clinica.fisioterapia.presentation.rest.dto.ChangeUserStatusRequest;
import com.clinica.fisioterapia.presentation.rest.dto.CreateUserRequest;
import com.clinica.fisioterapia.presentation.rest.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Gestion de usuarios del sistema. Solo ADMIN.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final CreateUserUseCase createUser;
    private final ChangeUserStatusUseCase changeUserStatus;

    public UserController(CreateUserUseCase createUser, ChangeUserStatusUseCase changeUserStatus) {
        this.createUser = createUser;
        this.changeUserStatus = changeUserStatus;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        CreateUserUseCase.CreateUserCommand command = new CreateUserUseCase.CreateUserCommand(
                request.name(), request.email(), request.password(), request.role(), principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(createUser.execute(command)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> changeStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody ChangeUserStatusRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(UserResponse.from(changeUserStatus.execute(id, request.active(), principal.userId())));
    }
}