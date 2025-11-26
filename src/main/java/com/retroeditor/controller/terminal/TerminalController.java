package com.retroeditor.controller.terminal;

/**
 * Controlador para la pestaña de terminal/console dentro de la aplicación.
 */
public class TerminalController {
    /**
     * Escribe una línea en la pestaña de consola (con salto de línea).
     * @param text Texto a escribir.
     * @param consoleOutputArea Área de texto de la consola donde se escribe el texto.
     */
    public void appendConsoleOutput(String text, javafx.scene.control.TextArea consoleOutputArea) {
        if (consoleOutputArea != null) {
            consoleOutputArea.appendText(text + "\n");
        }
    }

    /**
     * Escribe una línea en la pestaña de debugger (con salto de línea).
     * @param text Texto a escribir.
     * @param debuggerOutputArea Área de texto del debugger donde se escribe el texto.
     */
    public void appendDebuggerOutput(String text, javafx.scene.control.TextArea debuggerOutputArea) {
        if (debuggerOutputArea != null) {
            debuggerOutputArea.appendText(text + "\n");
        }
    }
}
