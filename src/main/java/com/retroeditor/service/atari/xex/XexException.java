package com.retroeditor.service.atari.xex;

/** Error de formato o de empaquetado de un fichero XEX de Atari 8 bits. */
public class XexException extends Exception {

    public XexException(String message) {
        super(message);
    }

    public XexException(String message, Throwable cause) {
        super(message, cause);
    }
}
