package com.proyecto.servicios;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients
@Slf4j
@RequiredArgsConstructor
public class App implements CommandLineRunner {

    private final ApplicationContext context;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) {
        displayInfo(context.getBean(BuildProperties.class));
    }

    private static void displayInfo(BuildProperties buildProperties) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("America/Mexico_City"));
        String out = formatter.format(buildProperties.getTime());
        log.info("""
                Nombre artefacto: {}
                Versión: {}
                Fecha Compilación: {}
                Artefacto: {}
                Grupo: {}""",
                buildProperties.getName(),
                buildProperties.getVersion(),
                out,
                buildProperties.getArtifact(),
                buildProperties.getGroup());
    }
}