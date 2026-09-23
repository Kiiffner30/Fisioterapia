package com.clinica.fisioterapia.domain.audit;

/**
 * Acciones sensibles registradas en la bitacora de auditoria.
 */
public enum AuditAction {

    LOGIN,
    LOGIN_FAILED,
    REFRESH,
    LOGOUT,
    USER_CREATED,
    USER_ACTIVATED,
    USER_DEACTIVATED
}