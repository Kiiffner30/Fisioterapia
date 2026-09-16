package com.clinica.fisioterapia.presentation.rest.error;

import com.clinica.fisioterapia.application.NotFoundException;
import com.clinica.fisioterapia.application.auth.AuthenticationService;
import com.clinica.fisioterapia.application.user.CreateUserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Tratamiento centralizado de excepciones: respuestas JSON uniformes,
 * sin exponer stack traces en produccion.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "Solicitud inválida", message, request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleMalformed(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "La solicitud no pudo interpretarse", request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationService.InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(AuthenticationService.InvalidCredentialsException ex,
                                                             HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Autenticación fallida", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationService.InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefresh(AuthenticationService.InvalidRefreshTokenException ex,
                                                         HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Sesión expirada", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationService.InactiveUserException.class)
    public ResponseEntity<ApiError> handleInactiveUser(AuthenticationService.InactiveUserException ex,
                                                       HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Usuario inactivo", ex.getMessage(), request);
    }

    @ExceptionHandler(CreateUserUseCase.EmailAlreadyInUseException.class)
    public ResponseEntity<ApiError> handleEmailInUse(CreateUserUseCase.EmailAlreadyInUseException ex,
                                                     HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Solicitud inválida", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Acceso denegado", "No tiene permisos para esta acción", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Recurso no encontrado", "La ruta no existe", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado", request);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String error, String message,
                                           HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), error,
                message == null ? error : message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}