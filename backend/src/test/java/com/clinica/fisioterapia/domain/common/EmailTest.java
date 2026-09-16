package com.clinica.fisioterapia.domain.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @Test
    void normalizaMinusculasYEliminaEspacios() {
        assertEquals("juan@clinica.com", Email.of("  Juan@Clinica.COM ").value());
    }

    @Test
    void rechazaFormatoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> Email.of("no-es-un-email"));
        assertThrows(IllegalArgumentException.class, () -> Email.of("a@b"));
        assertThrows(IllegalArgumentException.class, () -> Email.of(""));
        assertThrows(IllegalArgumentException.class, () -> Email.of(null));
    }
}