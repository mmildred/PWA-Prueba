package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ExternalServiceException;
import com.proyecto.servicios.exception.ServiceAuthenticationException;
import com.proyecto.servicios.exception.ServiceCommunicationException;
import com.proyecto.servicios.exception.ServiceResponseException;
import com.proyecto.servicios.exception.ServiceTimeoutException;
import com.proyecto.servicios.model.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(ServiceAuthenticationException ex) {
        log.error("Excepción de autenticación capturada: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .codigo(HttpStatus.UNAUTHORIZED.value())
                .mensaje("Fallo de autenticación con el servicio externo")
                .detalle(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(ServiceTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeoutException(ServiceTimeoutException ex) {
        log.error("Excepción de timeout capturada: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .codigo(HttpStatus.GATEWAY_TIMEOUT.value())
                .mensaje("Tiempo de espera agotado al consultar el servicio externo")
                .detalle(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error);
    }

    @ExceptionHandler(ServiceCommunicationException.class)
    public ResponseEntity<ErrorResponse> handleCommunicationException(ServiceCommunicationException ex) {
        log.error("Excepción de comunicación capturada: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .codigo(HttpStatus.SERVICE_UNAVAILABLE.value())
                .mensaje("Servicio externo no disponible temporalmente")
                .detalle(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(ServiceResponseException.class)
    public ResponseEntity<ErrorResponse> handleResponseException(ServiceResponseException ex) {
        log.error("Respuesta no exitosa del servicio externo: rawStatus={}, mensaje={}",
                ex.getRawStatusCode(), ex.getMessage());
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        ErrorResponse error = ErrorResponse.builder()
                .codigo(status.value())
                .mensaje("Respuesta no exitosa del servicio externo")
                .detalle(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleGenericExternalException(ExternalServiceException ex) {
        log.error("Error general de integración capturado: {}", ex.getMessage());
        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse error = ErrorResponse.builder()
                .codigo(status.value())
                .mensaje("Error al procesar la integración con el servicio externo")
                .detalle(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception ex) {
        log.error("Error inesperado no controlado: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.builder()
                .codigo(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .mensaje("Error interno del servidor")
                .detalle("Ocurrió un error inesperado al procesar la solicitud")
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
