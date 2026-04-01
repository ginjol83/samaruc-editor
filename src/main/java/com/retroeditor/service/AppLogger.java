package com.retroeditor.service;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import javafx.application.Platform;
import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * Registrador centralizado pequeño que puede escribir tanto en la salida estándar como en las áreas de texto de la consola/monitor de la aplicación.
 */
public final class AppLogger {

    private static StyleClassedTextArea consoleArea;
    private static StyleClassedTextArea monitorArea;
    private static volatile ResourceBundle bundle;

    /* Constructor privado para evitar la instanciación */
    private AppLogger() {}

    /**
     * Inicializa las áreas de texto para la consola y el monitor.
     * @param console
     * @param monitor
     */
    public static void init(StyleClassedTextArea console, StyleClassedTextArea monitor) {
        consoleArea = console;
        monitorArea = monitor;
    }

    /**
     * Establece el ResourceBundle activo para internacionalizar mensajes de log.
     */
    public static void setBundle(ResourceBundle activeBundle) {
        bundle = activeBundle;
    }

    /**
     * Registra un mensaje en la consola y en la salida estándar con una etiqueta.
     * @param tag Etiqueta para el mensaje (puede ser null).
     * @param message Mensaje a registrar.
     */
    public static void logConsole(String tag, String message) {
        String fallbackTag = i18n("log.tag.console.default", "consola");
        String line = String.format("[%s] %s", tag != null ? tag : fallbackTag, message);
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
        String fallbackTag = i18n("log.tag.monitor.default", "monitor");
        String line = String.format("[%s] %s", tag != null ? tag : fallbackTag, message);
        System.out.println(line);

        if (monitorArea != null) {
            Platform.runLater(() -> monitorArea.appendText(line + System.lineSeparator()));
        }
    }

    /**
     * Registra un mensaje en el monitor y en la salida estándar.
     * @param message Mensaje a registrar.
     */
    public static void logMonitor(String message) { logMonitor(i18n("log.tag.monitor.default", "monitor"), message); }

    /**
     * Registra un mensaje en la consola y en la salida estándar.
     * @param message Mensaje a registrar.
     */
    public static void logConsole(String message) { logConsole(i18n("log.tag.console.default", "consola"), message); }

    static String i18n(String key, String fallback, Object... args) {
        String pattern = fallback;

        try {
            if (bundle != null && bundle.containsKey(key)) {
                pattern = bundle.getString(key);
            }
        } catch (Exception ignored) {
        }

        if (args == null || args.length == 0) return pattern;
        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception ignored) {
            return pattern;
        }
    }
}
