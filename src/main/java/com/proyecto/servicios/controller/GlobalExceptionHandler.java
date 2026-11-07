package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // =========================================================================
    // EXCEPCIONES DE NEGOCIO Y DOMINIO
    // =========================================================================

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleClienteNoEncontrado(ClienteNoEncontradoException ex) {
        log.warn("Cliente no encontrado: {}", ex.getMessage());
        return buildBusinessResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TransaccionNoEncontradaException.class)
    public ResponseEntity<Map<String, Object>> handleTransaccionNoEncontrada(TransaccionNoEncontradaException ex) {
        log.warn("Transacción no encontrada: {}", ex.getMessage());
        return buildBusinessResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TransaccionDuplicadaException.class)
    public ResponseEntity<Map<String, Object>> handleTransaccionDuplicada(TransaccionDuplicadaException ex) {
        log.warn("Transacción duplicada / Idempotencia: {}", ex.getMessage());
        return buildBusinessResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<Map<String, Object>> handleSaldoInsuficiente(SaldoInsuficienteException ex) {
        log.warn("Saldo insuficiente para transacción: {}", ex.getMessage());
        return buildBusinessResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        log.warn("Violación de regla de negocio: {}", ex.getMessage());
        return buildBusinessResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Error de validación en parámetros de entrada");
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Error de Validación");
        response.put("detalles", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // =========================================================================
    // EXCEPCIONES DE SERVICIOS EXTERNOS (GESTOPAGO)
    // =========================================================================

    @ExceptionHandler(ServiceAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(ServiceAuthenticationException ex) {
        log.error("Excepción de autenticación capturada con servicio externo: {}", ex.getMessage());
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
        log.error("Excepción de timeout capturada con servicio externo: {}", ex.getMessage());
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
        log.error("Excepción de comunicación capturada con servicio externo: {}", ex.getMessage());
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
        log.error("Error interno no controlado: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.builder()
                .codigo(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .mensaje("Error interno del servidor")
                .detalle("Ocurrió un error inesperado al procesar la solicitud")
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private ResponseEntity<Map<String, Object>> buildBusinessResponse(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", status.value());
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}
