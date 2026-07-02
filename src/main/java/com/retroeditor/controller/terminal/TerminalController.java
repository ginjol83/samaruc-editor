package com.retroeditor.controller.terminal;

import javafx.application.Platform;
import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * Controlador para la pestaña de terminal/console dentro de la aplicación.
 */
public class TerminalController {
    /**
     * Escribe una línea en la pestaña de consola (con salto de línea).
     * @param text Texto a escribir.
     * @param consoleOutputArea Área de texto de la consola donde se escribe el texto.
     */
    public void appendConsoleOutput(String text, StyleClassedTextArea consoleOutputArea) {
        if (consoleOutputArea == null) return;

        Runnable appendAction = () -> appendStyledLine(consoleOutputArea, text);

        if (Platform.isFxApplicationThread()) {
            appendAction.run();
        } else {
            Platform.runLater(appendAction);
        }
    }

    /**
     * Escribe una línea en la pestaña de debugger (con salto de línea).
     * @param text Texto a escribir.
     * @param debuggerOutputArea Área de texto del debugger donde se escribe el texto.
     */
    public void appendDebuggerOutput(String text, StyleClassedTextArea debuggerOutputArea) {
        if (debuggerOutputArea == null) return;

        Runnable appendAction = () -> appendStyledLine(debuggerOutputArea, text);

        if (Platform.isFxApplicationThread()) {
            appendAction.run();
        } else {
            Platform.runLater(appendAction);
        }
    }

    private void appendStyledLine(StyleClassedTextArea area, String text) {
        String line = text + System.lineSeparator();
        int start = area.getLength();
        area.appendText(line);
        int end = area.getLength();

        String styleClass = resolveLineStyle(text);
        if (styleClass != null) {
            area.setStyleClass(start, end, styleClass);
        }
    }

    private String resolveLineStyle(String line) {
        if (line == null) return null;
        String normalized = line.toLowerCase();

        if (normalized.contains("error")) return "console-line-error";
        if (normalized.contains("warning") || normalized.contains("warn")) return "console-line-warning";
        return null;
    }
}
