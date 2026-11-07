package com.proyecto.servicios.exception;

public class TransaccionNoEncontradaException extends RuntimeException {
    public TransaccionNoEncontradaException(String message) {
        super(message);
    }
}
