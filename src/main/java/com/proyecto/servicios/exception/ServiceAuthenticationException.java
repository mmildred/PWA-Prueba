package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

public class ServiceAuthenticationException extends ExternalServiceException {

    public ServiceAuthenticationException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    public ServiceAuthenticationException(String message, HttpStatus status) {
        super(message, status);
    }

    public ServiceAuthenticationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNAUTHORIZED);
    }

    public ServiceAuthenticationException(String message, Throwable cause, HttpStatus status) {
        super(message, cause, status);
    }
}
