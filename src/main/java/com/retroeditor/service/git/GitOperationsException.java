package com.retroeditor.service.git;

/**
 * Error al ejecutar una operación git (comando no encontrado, salida no cero, timeout, etc.).
 */
public class GitOperationsException extends Exception {

    public GitOperationsException(String message) {
        super(message);
    }

    public GitOperationsException(String message, Throwable cause) {
        super(message, cause);
    }
}