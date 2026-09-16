package com.clinica.fisioterapia.presentation.rest.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeUserStatusRequest(
        @NotNull(message = "El estado es obligatorio")
        Boolean active) {
}