package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApi {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Bancaria: Onboarding y Pasarela Financiera Gestopago")
                        .description("API REST para onboarding de clientes personas físicas, administración de cuentas bancarias y procesamiento seguro de transacciones financieras con Gestopago (sendTx.do).")
                        .version("1.0.0")
                        .contact(new Contact().name("Institución Financiera").email("soporte@financiera.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Servidor Local de Desarrollo")
                ))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Bearer Token para autenticación en pasarela externa")));
    }
}
