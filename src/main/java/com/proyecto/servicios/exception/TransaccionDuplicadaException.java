package com.proyecto.servicios.exception;

public class TransaccionDuplicadaException extends RuntimeException {
    public TransaccionDuplicadaException(String message) {
        super(message);
    }
}
