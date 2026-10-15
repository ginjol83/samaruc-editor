package com.retroeditor.service.zxspectrum.basic;

/** Error de sintaxis al tokenizar codigo Sinclair BASIC ({@code .bas} de Spectrum). */
public final class SinclairBasicSyntaxException extends Exception {

    private final int line;
    private final int column;

    public SinclairBasicSyntaxException(String message, int line, int column) {
        super("line " + line + ", column " + column + ": " + message);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
