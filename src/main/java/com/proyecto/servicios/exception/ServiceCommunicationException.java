package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

public class ServiceCommunicationException extends ExternalServiceException {

    public ServiceCommunicationException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ServiceCommunicationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
