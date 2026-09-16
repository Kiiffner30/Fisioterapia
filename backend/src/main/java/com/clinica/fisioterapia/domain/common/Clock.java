package com.clinica.fisioterapia.domain.common;

import java.time.Instant;

/**
 * Puerto del dominio para obtener la hora actual.
 * Permite probar reglas de negocio dependientes del tiempo sin acoplarse a
 * infraestructura. Implementado por {@code infrastructure} (SystemClock).
 */
public interface Clock {

    Instant now();
}