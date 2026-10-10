package com.proyecto.servicios.config;

import feign.Logger;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignClientConfig {

    @Value("${gestopago.productos.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${gestopago.productos.read-timeout:10000}")
    private int readTimeout;

    @Bean
    public ErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                true
        );
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        // Nivel BASIC registra solo método, URL, status y tiempo de ejecución.
        // Protege headers sensibles (Bearer token) de quedar registrados en texto plano.
        return Logger.Level.BASIC;
    }
}
