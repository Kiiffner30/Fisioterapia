package com.clinica.fisioterapia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FisioterapiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(FisioterapiaApplication.class, args);
    }
}