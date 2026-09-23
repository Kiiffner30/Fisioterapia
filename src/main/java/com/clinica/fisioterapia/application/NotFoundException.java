package com.clinica.fisioterapia.application;

/**
 * Excepcion de aplicacion para recursos no encontrados.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}