package com.retroeditor;

/**
 * Puente de arranque para evitar que el launcher de Java trate la app como
 * "JavaFX application" directa en modo classpath (JAR/EXE empaquetado).
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}

