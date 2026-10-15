package com.retroeditor.service.basic.samaruc;

/**
 * Error de sintaxis en Samaruc BASIC (modo {@code REM @samaruc on}; se acepta
 * {@code @modern} como alias). Lleva el numero de linea del fuente original sobre
 * el que se detecto el problema.
 */
public class SamarucBasicException extends Exception {

    private final int line;

    public SamarucBasicException(String message, int line) {
        super(message);
        this.line = line;
    }

    public int getLine() {
        return line;
    }
}
