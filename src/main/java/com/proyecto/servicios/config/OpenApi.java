package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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
                        .title("API de Onboarding de Clientes y Servicios Financieros")
                        .description("API REST para el registro, consulta y administración de clientes personas físicas, domicilios y cuentas bancarias asociadas.")
                        .version("1.0.0")
                        .contact(new Contact().name("Institución Financiera").email("soporte@financiera.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Servidor Local de Desarrollo")
                ));
    }
}
