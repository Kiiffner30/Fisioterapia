package com.clinica.fisioterapia.infrastructure.config;

import com.clinica.fisioterapia.domain.common.Clock;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}