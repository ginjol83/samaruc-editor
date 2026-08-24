package com.retroeditor.controller.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.techsenger.jeditermfx.core.CursorShape;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * Gestiona la consola de compilación, el monitor y la terminal embebida.
 *
 * <p>La terminal usa una pseudo-consola real (ConPTY en Windows, PTY en Unix) a través de
 * Pty4J y se renderiza con un emulador VT100/xterm (JediTermFX). Esto permite ejecutar
 * aplicaciones interactivas como TUIs dentro de la pestaña Terminal.</p>
 */
public class TerminalController {
    private final Object terminalLock = new Object();
    private JediTermFxWidget terminalWidget;
    private PtyProcess terminalProcess;
    private TtyConnector terminalTtyConnector;
    private File workingDirectory;
    private StackPane lastTerminalContainer;
    private String terminalShell = "auto";

    private boolean enableLogging = false;
    private PrintWriter logWriter;
    private static final String LOG_FILE = "samaruc.log";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void setEnableLogging(boolean enabled) {
        this.enableLogging = enabled;
        if (enabled) {
            initLogFile();
        } else {
            closeLogFile();
        }
    }

    private void initLogFile() {
        if (logWriter != null) return;
        try {
            logWriter = new PrintWriter(new FileWriter(LOG_FILE, true));
            writeLog("--- SESION INICIADA ---");
        } catch (IOException e) {
            System.err.println("Error inicializando archivo de log: " + e.getMessage());
        }
    }

    private void closeLogFile() {
        if (logWriter != null) {
            writeLog("--- SESION FINALIZADA ---");
            logWriter.close();
            logWriter = null;
        }
    }

    private synchronized void writeLog(String text) {
        if (logWriter != null) {
            String timestamp = LocalDateTime.now().format(ISO_FORMATTER);
            logWriter.println("[" + timestamp + "] " + text);
            logWriter.flush();
        }
    }

    /**
     * Escribe una línea en la pestaña de consola (con salto de línea).
     * @param text Texto a escribir.
     * @param consoleOutputArea Área de texto de la consola donde se escribe el texto.
     */
    public void appendConsoleOutput(String text, StyleClassedTextArea consoleOutputArea) {
        if (consoleOutputArea == null) return;

        Runnable appendAction = () -> appendStyledLine(consoleOutputArea, text, null);

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

        Runnable appendAction = () -> appendStyledLine(debuggerOutputArea, text, null);

        if (Platform.isFxApplicationThread()) {
            appendAction.run();
        } else {
            Platform.runLater(appendAction);
        }
    }

    /**
     * Inicia la terminal embebida dentro del contenedor indicado. Si ya hay una terminal
     * activa no hace nada.
     */
    public void startTerminal(StackPane container) {
        if (container == null) return;
        synchronized (terminalLock) {
            lastTerminalContainer = container;
            if (terminalProcess != null && terminalProcess.isAlive()) {
                return;
            }

            try {
                PtyProcess process = createPtyProcess();
                SamarucPtyTtyConnector connector = new SamarucPtyTtyConnector(process, StandardCharsets.UTF_8);
                JediTermFxWidget widget = new JediTermFxWidget(80, 24, new SamarucTerminalSettingsProvider());
                widget.setTtyConnector(connector);
                widget.start();
                try {
                    widget.getTerminalPanel().setCursorShape(CursorShape.BLINK_BLOCK);
                    widget.getTerminalPanel().setCursorVisible(true);
                } catch (Exception cursorEx) {
                    writeLog("No se pudo aplicar el estilo de cursor: " + cursorEx.getMessage());
                }

                terminalProcess = process;
                terminalTtyConnector = connector;
                terminalWidget = widget;

                container.getChildren().setAll(widget.getPane());
            } catch (Exception ex) {
                writeLog("Error iniciando terminal: " + ex.getMessage());
                System.err.println("Error iniciando terminal: " + ex.getMessage());
            }
        }
    }

    /**
     * Detiene la terminal activa liberando el proceso y el emulador.
     */
    public void stopTerminal() {
        JediTermFxWidget widgetToClose;
        PtyProcess processToStop;
        synchronized (terminalLock) {
            widgetToClose = terminalWidget;
            processToStop = terminalProcess;
            terminalWidget = null;
            terminalProcess = null;
            terminalTtyConnector = null;
        }

        if (widgetToClose != null) {
            try {
                widgetToClose.close();
            } catch (Exception ignored) {
            }
        }
        if (processToStop != null) {
            try {
                processToStop.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Reinicia la terminal: detiene la actual y lanza una nueva en el contenedor.
     */
    public void restartTerminal(StackPane container) {
        stopTerminal();
        startTerminal(container);
    }

    /**
     * Establece el shell a usar en la terminal embebida.
     * Valores validos: auto, cmd, powershell, pwsh, bash.
     * @param shell Shell configurado (cualquier otro valor usa el predeterminado del sistema).
     */
    public void setTerminalShell(String shell) {
        if (shell == null || shell.isBlank()) {
            this.terminalShell = "auto";
        } else {
            this.terminalShell = shell.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Establece el directorio de trabajo que se usará al lanzar la terminal.
     * @param dir Directorio del proyecto actual (puede ser null para usar el cwd del proceso).
     */
    public void setWorkingDirectory(File dir) {
        this.workingDirectory = dir;
    }

    /**
     * Actualiza el directorio de trabajo de la terminal. Si el directorio cambió y la
     * terminal está activa, la reinicia para que el shell arranque en el nuevo directorio.
     */
    public void restartTerminalIfWorkingDirectoryChanged(StackPane container, File dir) {
        File current = workingDirectory;
        if (sameDirectory(current, dir)) return;

        setWorkingDirectory(dir);
        if (!isTerminalRunning() || container == null) return;

        Runnable restartAction = () -> restartTerminal(container);
        if (Platform.isFxApplicationThread()) {
            restartAction.run();
        } else {
            Platform.runLater(restartAction);
        }
    }

    private boolean sameDirectory(File a, File b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException ex) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    public boolean isTerminalRunning() {
        synchronized (terminalLock) {
            return terminalProcess != null && terminalProcess.isAlive();
        }
    }

    /**
     * Se asegura de que la terminal embebida esté activa. Si el shell terminó, la
     * reinicia en el último contenedor conocido para que los programas interactivos
     * (scanf/fgets) puedan ejecutarse con entrada/salida real.
     * @return true si la terminal quedó activa; false si no hay contenedor conocido,
     *         no se está en el hilo FX o el arranque falló.
     */
    public boolean ensureTerminalRunning() {
        synchronized (terminalLock) {
            if (terminalProcess != null && terminalProcess.isAlive()) {
                return true;
            }
        }
        if (!Platform.isFxApplicationThread()) {
            return false;
        }
        StackPane container;
        synchronized (terminalLock) {
            container = lastTerminalContainer;
        }
        if (container == null) return false;

        startTerminal(container);
        synchronized (terminalLock) {
            return terminalProcess != null && terminalProcess.isAlive();
        }
    }

    /**
     * Limpia el buffer visible de la terminal sin reiniciar el proceso.
     */
    public void clearTerminal() {
        JediTermFxWidget widget;
        synchronized (terminalLock) {
            widget = terminalWidget;
        }
        if (widget == null) return;

        Platform.runLater(() -> {
            try {
                widget.getTerminalPanel().clearBuffer();
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Da el foco al emulador para que las pulsaciones de teclado lleguen al shell.
     */
    public void requestTerminalFocus() {
        JediTermFxWidget widget;
        synchronized (terminalLock) {
            widget = terminalWidget;
        }
        if (widget == null) return;

        Platform.runLater(() -> {
            try {
                widget.getPreferredFocusableNode().requestFocus();
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Envía un comando al shell de la terminal embebida, de modo que se ejecute en
     * el terminal real con su entrada/salida interactiva (stdin/stdout).
     * @param command Comando a ejecutar (p. ej. la ruta de un ejecutable compilado).
     * @return true si el comando se escribió en el shell; false si la terminal no está activa.
     */
    public boolean executeCommandInTerminal(String command) {
        if (command == null || command.isBlank()) return false;
        if (!ensureTerminalRunning()) return false;
        PtyProcess process;
        synchronized (terminalLock) {
            process = terminalProcess;
        }
        if (process == null || !process.isAlive()) return false;

        try {
            String lineEnding = isWindows() ? "\r" : "\n";
            OutputStream out = process.getOutputStream();
            out.write((command + lineEnding).getBytes(StandardCharsets.UTF_8));
            out.flush();
            requestTerminalFocus();
            return true;
        } catch (IOException ex) {
            writeLog("Error enviando comando a la terminal: " + ex.getMessage());
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private PtyProcess createPtyProcess() throws IOException {
        String[] command = resolveShellCommand();
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");

        PtyProcessBuilder builder = new PtyProcessBuilder()
            .setCommand(command)
            .setEnvironment(environment)
            .setInitialColumns(80)
            .setInitialRows(24);
        File wd = workingDirectory;
        if (wd != null && wd.isDirectory()) {
            builder.setDirectory(wd.getAbsolutePath());
        }
        return builder.start();
    }

    private String[] resolveShellCommand() {
        String shell = terminalShell != null ? terminalShell : "auto";
        switch (shell) {
            case "cmd":
                return windowsShellCommand("cmd.exe");
            case "powershell":
                return isWindows() ? new String[]{"powershell.exe"} : new String[]{"powershell"};
            case "pwsh":
                return new String[]{"pwsh"};
            case "bash":
                return new String[]{"/bin/bash", "--login"};
            case "auto":
            default:
                if (isWindows()) {
                    String comSpec = System.getenv("ComSpec");
                    return new String[]{comSpec != null && !comSpec.isBlank() ? comSpec : "cmd.exe"};
                }
                return new String[]{"/bin/bash", "--login"};
        }
    }

    private String[] windowsShellCommand(String defaultExe) {
        String comSpec = System.getenv("ComSpec");
        return new String[]{comSpec != null && !comSpec.isBlank() ? comSpec : defaultExe};
    }

    private void appendStyledLine(StyleClassedTextArea area, String text, String styleOverride) {
        if (enableLogging) {
            writeLog(text);
        }
        String line = text + System.lineSeparator();
        int start = area.getLength();
        area.appendText(line);
        int end = area.getLength();

        String styleClass = styleOverride != null ? styleOverride : resolveLineStyle(text);
        if (styleClass != null) {
            area.setStyleClass(start, end, styleClass);
        }
        area.moveTo(area.getLength());
        area.requestFollowCaret();
    }

    private String resolveLineStyle(String line) {
        if (line == null) return null;
        String normalized = line.toLowerCase(Locale.ROOT);

        if (normalized.contains("error")) return "console-line-error";
        if (normalized.contains("warning") || normalized.contains("warn")) return "console-line-warning";
        return null;
    }
}
