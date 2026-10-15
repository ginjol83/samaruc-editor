package com.retroeditor.service.atari.basic;

public final class AtariBasicSyntaxException extends Exception {

    private final int line;
    private final int column;

    public AtariBasicSyntaxException(String message, int line, int column) {
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
