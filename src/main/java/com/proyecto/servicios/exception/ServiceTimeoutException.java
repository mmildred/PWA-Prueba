package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

public class ServiceTimeoutException extends ExternalServiceException {

    public ServiceTimeoutException(String message) {
        super(message, HttpStatus.GATEWAY_TIMEOUT);
    }

    public ServiceTimeoutException(String message, Throwable cause) {
        super(message, cause, HttpStatus.GATEWAY_TIMEOUT);
    }
}
