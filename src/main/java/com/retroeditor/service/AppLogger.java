package com.retroeditor.service;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

/**
 * Registrador centralizado pequeño que puede escribir tanto en la salida estándar como en las áreas de texto de la consola/monitor de la aplicación.
 */
public final class AppLogger {

    private static TextArea consoleArea;
    private static TextArea monitorArea;

    /* Constructor privado para evitar la instanciación */
    private AppLogger() {}

    /**
     * Inicializa las áreas de texto para la consola y el monitor.
     * @param console
     * @param monitor
     */
    public static void init(TextArea console, TextArea monitor) {
        consoleArea = console;
        monitorArea = monitor;
    }

    /**
     * Registra un mensaje en la consola y en la salida estándar con una etiqueta.
     * @param tag Etiqueta para el mensaje (puede ser null).
     * @param message Mensaje a registrar.
     */
    public static void logConsole(String tag, String message) {
        String line = String.format("[%s] %s", tag != null ? tag : "consola", message);
        System.out.println(line);

        if (consoleArea != null) {
            Platform.runLater(() -> consoleArea.appendText(line + System.lineSeparator()));
        }
    }

    /**
     * Registra un mensaje en el monitor y en la salida estándar con una etiqueta.
     * @param tag Etiqueta para el mensaje (puede ser null).
     * @param message Mensaje a registrar.
     */
    public static void logMonitor(String tag, String message) {
        String line = String.format("[%s] %s", tag != null ? tag : "monitor", message);
        System.out.println(line);

        if (monitorArea != null) {
            Platform.runLater(() -> monitorArea.appendText(line + System.lineSeparator()));
        }
    }

    /**
     * Registra un mensaje en el monitor y en la salida estándar.
     * @param message Mensaje a registrar.
     */
    public static void logMonitor(String message) { logMonitor("monitor", message); }

    /**
     * Registra un mensaje en la consola y en la salida estándar.
     * @param message Mensaje a registrar.
     */
    public static void logConsole(String message) { logConsole("consola", message); }
}
