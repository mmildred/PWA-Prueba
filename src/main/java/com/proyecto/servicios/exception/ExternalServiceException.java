package com.proyecto.servicios.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ExternalServiceException extends RuntimeException {

    private final HttpStatus httpStatus;

    public ExternalServiceException(String message) {
        super(message);
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ExternalServiceException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ExternalServiceException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}
