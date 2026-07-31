package com.retroeditor.controller.terminal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.application.Platform;
import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * Controlador para la pestaña de terminal/console dentro de la aplicación.
 */
public class TerminalController {
    private final Object terminalLock = new Object();
    private Process terminalProcess;
    private BufferedWriter terminalWriter;
    private Thread stdoutPumpThread;
    private Thread stderrPumpThread;

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

    public void startWindowsTerminal(StyleClassedTextArea terminalOutputArea) {
        synchronized (terminalLock) {
            if (terminalProcess != null && terminalProcess.isAlive()) {
                appendTerminalOutput(terminalOutputArea, "Terminal ya iniciada.");
                return;
            }

            try {
                ProcessBuilder processBuilder = createTerminalProcessBuilder();
                processBuilder.directory(resolveWorkingDirectory());
                Process startedProcess = processBuilder.start();
                terminalProcess = startedProcess;

                terminalWriter = new BufferedWriter(new OutputStreamWriter(
                    startedProcess.getOutputStream(),
                    Charset.defaultCharset()
                ));

                stdoutPumpThread = startStreamPump(startedProcess.getInputStream(), terminalOutputArea, false);
                stderrPumpThread = startStreamPump(startedProcess.getErrorStream(), terminalOutputArea, true);

                Thread waitThread = new Thread(() -> {
                    int exitCode = -1;
                    try {
                        exitCode = startedProcess.waitFor();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        synchronized (terminalLock) {
                            if (terminalProcess == startedProcess) {
                                terminalWriter = null;
                                terminalProcess = null;
                                stdoutPumpThread = null;
                                stderrPumpThread = null;
                            }
                        }
                    }

                    appendTerminalOutput(terminalOutputArea, "Terminal finalizada (codigo " + exitCode + ").");
                }, "terminal-wait-thread");
                waitThread.setDaemon(true);
                waitThread.start();

                appendTerminalOutput(terminalOutputArea, "Terminal iniciada (" + String.join(" ", processBuilder.command()) + ").");
            } catch (IOException ex) {
                appendTerminalOutput(terminalOutputArea, "Error iniciando terminal: " + ex.getMessage());
            }
        }
    }

    public void restartWindowsTerminal(StyleClassedTextArea terminalOutputArea) {
        stopWindowsTerminal(terminalOutputArea);
        startWindowsTerminal(terminalOutputArea);
    }

    public void stopWindowsTerminal(StyleClassedTextArea terminalOutputArea) {
        Process processToStop;
        synchronized (terminalLock) {
            processToStop = terminalProcess;
            terminalWriter = null;
            terminalProcess = null;
            stdoutPumpThread = null;
            stderrPumpThread = null;
        }

        if (processToStop != null && processToStop.isAlive()) {
            try {
                processToStop.destroy();
            } catch (Exception ignored) {
            }
        }

        appendTerminalOutput(terminalOutputArea, "Terminal detenida.");
    }

    public boolean isTerminalRunning() {
        synchronized (terminalLock) {
            return terminalProcess != null && terminalProcess.isAlive();
        }
    }

    public void sendTerminalCommand(String command, StyleClassedTextArea terminalOutputArea) {
        String normalizedCommand = command != null ? command : "";
        BufferedWriter writer;
        synchronized (terminalLock) {
            writer = terminalWriter;
        }

        if (writer == null || !isTerminalRunning()) {
            appendTerminalOutput(terminalOutputArea, "La terminal no esta iniciada.");
            return;
        }

        try {
            writer.write(normalizedCommand);
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            appendTerminalOutput(terminalOutputArea, "Error enviando comando a la terminal: " + ex.getMessage());
        }
    }

    public void clearTerminalOutput(StyleClassedTextArea terminalOutputArea) {
        if (terminalOutputArea == null) return;
        Runnable clearAction = terminalOutputArea::clear;
        if (Platform.isFxApplicationThread()) {
            clearAction.run();
        } else {
            Platform.runLater(clearAction);
        }
    }

    public void appendTerminalOutput(StyleClassedTextArea terminalOutputArea, String text) {
        if (terminalOutputArea == null) return;
        Runnable appendAction = () -> appendStyledLine(terminalOutputArea, text, null);
        if (Platform.isFxApplicationThread()) {
            appendAction.run();
        } else {
            Platform.runLater(appendAction);
        }
    }

    private ProcessBuilder createTerminalProcessBuilder() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String comSpec = System.getenv("ComSpec");
            String shell = (comSpec == null || comSpec.isBlank()) ? "cmd.exe" : comSpec;
            return new ProcessBuilder(shell);
        }
        return new ProcessBuilder("/bin/bash", "-i");
    }

    private File resolveWorkingDirectory() {
        try {
            return new File(System.getProperty("user.dir"));
        } catch (Exception ex) {
            return null;
        }
    }

    private Thread startStreamPump(InputStream stream, StyleClassedTextArea terminalOutputArea, boolean errorStream) {
        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String currentLine = line;
                    Runnable appendAction = () -> appendStyledLine(
                        terminalOutputArea,
                        currentLine,
                        errorStream ? "terminal-line-error" : null
                    );
                    if (Platform.isFxApplicationThread()) {
                        appendAction.run();
                    } else {
                        Platform.runLater(appendAction);
                    }
                }
            } catch (IOException ignored) {
            }
        }, errorStream ? "terminal-stderr-pump" : "terminal-stdout-pump");
        pump.setDaemon(true);
        pump.start();
        return pump;
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
        String normalized = line.toLowerCase();

        if (normalized.contains("error")) return "console-line-error";
        if (normalized.contains("warning") || normalized.contains("warn")) return "console-line-warning";
        return null;
    }
}
