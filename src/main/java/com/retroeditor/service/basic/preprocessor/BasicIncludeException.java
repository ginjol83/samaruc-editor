package com.retroeditor.service.basic.preprocessor;

public final class BasicIncludeException extends Exception {

    private final int line;

    public BasicIncludeException(int line, String message) {
        super(message);
        this.line = line;
    }

    public BasicIncludeException(int line, String message, Throwable cause) {
        super(message, cause);
        this.line = line;
    }

    public int getLine() {
        return line;
    }
}
