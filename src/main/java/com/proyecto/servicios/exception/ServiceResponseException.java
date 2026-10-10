package com.proyecto.servicios.exception;

import org.springframework.http.HttpStatus;

public class ServiceResponseException extends ExternalServiceException {

    private final int rawStatusCode;

    public ServiceResponseException(String message, int rawStatusCode) {
        super(message, HttpStatus.resolve(rawStatusCode) != null ? HttpStatus.resolve(rawStatusCode) : HttpStatus.BAD_GATEWAY);
        this.rawStatusCode = rawStatusCode;
    }

    public ServiceResponseException(String message, Throwable cause, int rawStatusCode) {
        super(message, cause, HttpStatus.resolve(rawStatusCode) != null ? HttpStatus.resolve(rawStatusCode) : HttpStatus.BAD_GATEWAY);
        this.rawStatusCode = rawStatusCode;
    }

    public int getRawStatusCode() {
        return rawStatusCode;
    }
}
