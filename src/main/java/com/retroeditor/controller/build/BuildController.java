package com.retroeditor.controller.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import com.retroeditor.controller.terminal.TerminalController;
import com.retroeditor.emulator.EmulatorLauncher;
import com.retroeditor.model.ConfigModel;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.util.FXUtils;

import javafx.event.ActionEvent;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.fxmisc.richtext.StyleClassedTextArea;

public class BuildController {

    private static final String COMPILER_GBDK = "GBDK";
    private static final String COMPILER_Z88DK = "z88dk";
    private static final String LEGACY_SPECTRUM = "Spectrum";
    private static final String EMULATOR_EMULICIOUS = "Emulicious";
    private static final String EMULATOR_JSPECCY    = "JSpeccy";

    FXUtils            fxUtils            = new FXUtils();
    TerminalController terminalController = new TerminalController();
    private final AtomicReference<Process> activeCompilationProcess = new AtomicReference<>();
    private final AtomicReference<Thread> activeCompilationThread = new AtomicReference<>();
    private final BooleanProperty compilationRunning = new SimpleBooleanProperty(false);

    /**
     * Compila el archivo actualmente abierto en la pestaña seleccionada.
     * @param event
     * @param consoleOutputArea
     * @param tabPane
     * @param tabFileMap
     * @param configModel
     */
    public void onCompilar(ActionEvent event, 
                          StyleClassedTextArea consoleOutputArea,
                          TabPane tabPane,
                          Map<Tab, File> tabFileMap,
                          ConfigModel configModel,
                          BiConsumer<File, String> compilerOutputListener) {

        if (isCompilationRunning()) {
            terminalController.appendConsoleOutput("Ya hay una compilación en curso. Espera a que termine antes de iniciar otra.", consoleOutputArea);
            return;
        }

        Tab tab = fxUtils.getSelectedTab(tabPane);

        if (tab == null) {
            UserActionMonitor.errorOccurred("COMPILE", "Sin archivo abierto");
            terminalController.appendConsoleOutput("No hay archivo abierto para compilar.",consoleOutputArea);
            return;
        }

        File file = tabFileMap.get(tab);

        if (file == null) {
            UserActionMonitor.errorOccurred("COMPILE", "Archivo no guardado");
            terminalController.appendConsoleOutput("No se puede compilar: el archivo no está guardado.",consoleOutputArea);
            return;
        }

        String   selectedCompiler = configModel.getConfigProperty("compilador_seleccionado", "GBDK");
        UserActionMonitor.compilationStarted(selectedCompiler, file.getName());

        String   sourceFileName   = file.getName();
        int      dotIndex         = sourceFileName.lastIndexOf('.');
        String   outputBaseName   = dotIndex >= 0 ? sourceFileName.substring(0, dotIndex) : sourceFileName;
        File     workingDirectory = file.getParentFile();
        String[] cmd;

        if (isSpectrumCompiler(selectedCompiler)) {
            String spectrumBin = configModel.getConfigProperty("spectrum_bin", "");

            if (spectrumBin == null || spectrumBin.isEmpty()) {
                Alert alert = new Alert(AlertType.ERROR);

                alert.setTitle      ("Error de compilación");
                alert.setHeaderText ("No se ha configurado la ruta de z88dk");
                alert.setContentText("Por favor, configura la ruta del bin de z88dk en las opciones de configuración.");
                alert.showAndWait   ();

                terminalController.appendConsoleOutput("ERROR: No se ha configurado la ruta del bin de z88dk.",consoleOutputArea);

                return;
            }

            String compiler = resolveCompilerExecutable(spectrumBin, "zcc");
            String clibOption = configModel.getConfigProperty("z88dk_clib_option", ConfigModel.Z88DK_CLIB_NEW);
            boolean useCrtOrgCode = Boolean.parseBoolean(configModel.getConfigProperty("z88dk_use_pragma_crt_org_code_32768", "true"));
            boolean useCreateApp = Boolean.parseBoolean(configModel.getConfigProperty("z88dk_create_app", "true"));
            String spectrumOutputBase = outputBaseName;
            String z88dkTarget = normalizeZ88dkTarget(configModel.getConfigProperty("z88dk_target", "+zx"));

            List<String> spectrumCmd = new ArrayList<>();
            spectrumCmd.add(compiler);
            spectrumCmd.add(z88dkTarget);
            String clibArgument = resolveZ88dkClibArgument(clibOption);
            if (clibArgument != null) spectrumCmd.add(clibArgument);
            if (useCrtOrgCode) spectrumCmd.add("-pragma-define:CRT_ORG_CODE=32768");
            addMacroFlags(spectrumCmd, configModel.getConfigProperty("z88dk_defines", ""));
            addIncludeFlags(spectrumCmd, configModel.getConfigProperty("z88dk_includes", ""));
            addExtraArgs(spectrumCmd, configModel.getConfigProperty("z88dk_extra_args", ""));
            spectrumCmd.add("-o");
            spectrumCmd.add(spectrumOutputBase);
            spectrumCmd.add(sourceFileName);
            if (useCreateApp) spectrumCmd.add("-create-app");
            cmd = spectrumCmd.toArray(new String[0]);

        } else {
            String gbdkBin = configModel.getConfigProperty("gbdk_bin", "");

            if (gbdkBin == null || gbdkBin.isEmpty()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle      ("Error de compilación");
                alert.setHeaderText ("No se ha configurado la ruta de GBDK");
                alert.setContentText("Por favor, configura la ruta del bin de GBDK en las opciones de configuración.");
                alert.showAndWait   ();

                terminalController.appendConsoleOutput("ERROR: No se ha configurado la ruta del bin de GBDK.",consoleOutputArea);

                return;
            }
            
            String compiler = resolveCompilerExecutable(gbdkBin, "lcc");
            List<String> gbdkCmd = new ArrayList<>();
            gbdkCmd.add(compiler);

            // Opciones de optimización GBDK
            String gbdkOptLevel = configModel.getConfigProperty("gbdk_opt_level", "none");
            if (gbdkOptLevel != null && !gbdkOptLevel.equalsIgnoreCase("none") && !gbdkOptLevel.isBlank()) {
                gbdkCmd.add(gbdkOptLevel);
            }
            if (Boolean.parseBoolean(configModel.getConfigProperty("gbdk_opt_speed", "false"))) {
                gbdkCmd.add("--opt-code-speed");
            }
            if (Boolean.parseBoolean(configModel.getConfigProperty("gbdk_opt_size", "false"))) {
                gbdkCmd.add("--opt-code-size");
            }

            addMacroFlags(gbdkCmd, configModel.getConfigProperty("gbdk_defines", ""));
            addIncludeFlags(gbdkCmd, configModel.getConfigProperty("gbdk_includes", ""));
            addExtraArgs(gbdkCmd, configModel.getConfigProperty("gbdk_extra_args", ""));

            gbdkCmd.add("-o");
            gbdkCmd.add(outputBaseName + ".gb");
            gbdkCmd.add(sourceFileName);
            cmd = gbdkCmd.toArray(new String[0]);
        }

        terminalController.appendConsoleOutput("Compilando con: " + formatCommandForDisplay(cmd),consoleOutputArea);
        if (workingDirectory != null) {
            terminalController.appendConsoleOutput("Directorio de compilacion: " + workingDirectory.getAbsolutePath(), consoleOutputArea);
        }

        startCompilationInBackground(file, cmd, workingDirectory, selectedCompiler, configModel, consoleOutputArea, compilerOutputListener);
    }

    public boolean isCompilationRunning() {
        return compilationRunning.get();
    }

    public ReadOnlyBooleanProperty compilationRunningProperty() {
        return compilationRunning;
    }

    public void cancelActiveCompilation(StyleClassedTextArea consoleOutputArea) {
        if (!isCompilationRunning()) {
            terminalController.appendConsoleOutput("No hay ninguna compilación activa para cancelar.", consoleOutputArea);
            return;
        }

        terminalController.appendConsoleOutput("Cancelando compilación en curso...", consoleOutputArea);
        stopActiveCompilation();
    }

    public void stopActiveCompilation() {
        Process process = activeCompilationProcess.getAndSet(null);
        Thread thread = activeCompilationThread.getAndSet(null);

        if (process != null) {
            try {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        }

        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    private void startCompilationInBackground(File file,
                                              String[] cmd,
                                              File workingDirectory,
                                              String selectedCompiler,
                                              ConfigModel configModel,
                                              StyleClassedTextArea consoleOutputArea,
                                              BiConsumer<File, String> compilerOutputListener) {
        updateCompilationRunning(true);

        Thread compileThread = new Thread(() -> {
            Process proc = null;

            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                if (workingDirectory != null) {
                    pb.directory(workingDirectory);
                }

                pb.redirectErrorStream(true);

                if (isSpectrumCompiler(selectedCompiler)) {
                    String spectrumBin = configModel.getConfigProperty("spectrum_bin", "");
                    String currentPath = pb.environment().get("PATH");
                    String cleanSpectrumBin = normalizeConfiguredPath(spectrumBin);
                    if (currentPath == null || currentPath.isBlank()) {
                        pb.environment().put("PATH", cleanSpectrumBin);
                    } else {
                        pb.environment().put("PATH", cleanSpectrumBin + File.pathSeparator + currentPath);
                    }
                }

                proc = pb.start();
                activeCompilationProcess.set(proc);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        terminalController.appendConsoleOutput(line, consoleOutputArea);
                        if (compilerOutputListener != null) {
                            compilerOutputListener.accept(file, line);
                        }
                    }
                }

                int exitCode = proc.waitFor();
                String compilationEndLine = "Compilación finalizada. Código de salida: " + exitCode;
                terminalController.appendConsoleOutput(compilationEndLine, consoleOutputArea);
                if (compilerOutputListener != null) {
                    compilerOutputListener.accept(file, compilationEndLine);
                }
                UserActionMonitor.compilationFinished(exitCode, file.getName());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                terminalController.appendConsoleOutput("Compilación interrumpida.", consoleOutputArea);
                UserActionMonitor.compilationError("Compilación interrumpida");
            } catch (Exception e) {
                UserActionMonitor.compilationError(e.getMessage());
                terminalController.appendConsoleOutput("Error al ejecutar el compilador: " + e.getMessage(), consoleOutputArea);
            } finally {
                if (proc != null) {
                    try {
                        proc.getInputStream().close();
                    } catch (Exception ignored) {
                    }
                    try {
                        proc.getErrorStream().close();
                    } catch (Exception ignored) {
                    }
                    try {
                        proc.getOutputStream().close();
                    } catch (Exception ignored) {
                    }
                }

                activeCompilationProcess.compareAndSet(proc, null);
                activeCompilationThread.compareAndSet(Thread.currentThread(), null);
                updateCompilationRunning(false);
            }
        }, "compiler-" + file.getName());

        compileThread.setDaemon(true);
        activeCompilationThread.set(compileThread);
        compileThread.start();
    }

    private void updateCompilationRunning(boolean running) {
        if (Platform.isFxApplicationThread()) {
            compilationRunning.set(running);
        } else {
            Platform.runLater(() -> compilationRunning.set(running));
        }
    }

    /**
     * Ejecuta el archivo compilado en el emulador correspondiente.
     * @param event
     * @param consoleOutputArea
     * @param tabPane
     * @param tabFileMap
     * @param configModel
     */
    public  void onEjecutar( ActionEvent event,
                             StyleClassedTextArea consoleOutputArea,
                             TabPane tabPane,
                             Map<Tab, File> tabFileMap,
                             ConfigModel configModel,
                             Stage ownerStage) {

        terminalController.appendConsoleOutput("Compilando y ejecutando...", consoleOutputArea);

        File file = getCurrentFile(tabPane, tabFileMap);

        if (file == null) {
            UserActionMonitor.errorOccurred("EXECUTION", "Sin archivo para ejecutar");
            terminalController.appendConsoleOutput("No hay archivo abierto para ejecutar.", consoleOutputArea);
            return;
        }

        String selectedCompiler = configModel.getConfigProperty("compilador_seleccionado", COMPILER_GBDK);
        String selectedEmulator = getEmulatorForCompiler(selectedCompiler);
        
        UserActionMonitor.executionStarted(selectedEmulator, file.getName());

        if (EMULATOR_EMULICIOUS.equalsIgnoreCase(selectedEmulator)) {
            launchCoffeeGb(file, consoleOutputArea, ownerStage);
        } else if (EMULATOR_JSPECCY.equalsIgnoreCase(selectedEmulator)) {
            launchJSpeccy(file, consoleOutputArea, ownerStage);
        } else {
            terminalController.appendConsoleOutput(
                "Emulador no soportado o no configurado: " + selectedEmulator,
                consoleOutputArea
            );
        }
    }

    private void launchCoffeeGb(File sourceFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File romFile = buildOutputFile(sourceFile, ".gb");

        if (!romFile.exists()) {
            terminalController.appendConsoleOutput(
                "No se encontró la ROM de Game Boy para ejecutar: " + romFile.getAbsolutePath(),
                consoleOutputArea
            );
            terminalController.appendConsoleOutput(
                "Compila el proyecto con GBDK antes de lanzar Emulicious.",
                consoleOutputArea
            );
            return;
        }

        File emuliciousJar = resolveBundledJar("Emulicious");

        if (emuliciousJar == null) {
            terminalController.appendConsoleOutput(
                "No se encontró Emulicious.jar en la carpeta libs.",
                consoleOutputArea
            );
            return;
        }

        try {
            UserActionMonitor.emulatorLaunched("Emulicious", romFile.getName());
            EmulatorLauncher.LaunchResult launch = EmulatorLauncher.launchEmulicious(emuliciousJar, romFile, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning("Emulicious");
            monitorEmulatorStartup("Emulicious", launch.process, ownerStage, consoleOutputArea);
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando Emulicious con: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception e) {
            UserActionMonitor.executionError("Emulicious: " + e.getMessage());
            terminalController.appendConsoleOutput("Error al lanzar Emulicious: " + e.getMessage(), consoleOutputArea);
        }
    }

    private void launchJSpeccy(File sourceFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File spectrumArtifact = resolveSpectrumArtifact(sourceFile);

        if (spectrumArtifact == null) {
            terminalController.appendConsoleOutput(
                "No se encontró un archivo compatible para JSpeccy (.tap, .sna o .z80).",
                consoleOutputArea
            );
            terminalController.appendConsoleOutput(
                "Compila el proyecto para Spectrum antes de ejecutar con JSpeccy.",
                consoleOutputArea
            );
            return;
        }

        File jspeccyFile = resolveJspeccyFile();

        if (jspeccyFile == null) {
            Alert alert = new Alert(AlertType.ERROR);

            alert.setTitle("Error al ejecutar emulador Spectrum");
            alert.setHeaderText("No se encontró JSpeccy integrado");
            alert.setContentText("Verifica que exista el archivo libs/JSpeccy.jar en la instalación de la aplicación.");
            alert.showAndWait();

            terminalController.appendConsoleOutput(
                "ERROR: No se encontró libs/JSpeccy.jar para lanzar el emulador Spectrum.",
                consoleOutputArea
            );
            return;
        }

        try {
            UserActionMonitor.emulatorLaunched("JSpeccy", spectrumArtifact.getName());
            EmulatorLauncher.launchJSpeccy(jspeccyFile, spectrumArtifact, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning("JSpeccy");
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando JSpeccy con: " + spectrumArtifact.getName(),
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError("JSpeccy: " + ex.getMessage());
            terminalController.appendConsoleOutput("Error al lanzar JSpeccy: " + ex.getMessage(), consoleOutputArea);
        }
    }

    private String getEmulatorForCompiler(String compiler) {
        return isSpectrumCompiler(compiler) ? EMULATOR_JSPECCY : EMULATOR_EMULICIOUS;
    }

    private boolean isSpectrumCompiler(String compiler) {
        return COMPILER_Z88DK.equalsIgnoreCase(compiler) || LEGACY_SPECTRUM.equalsIgnoreCase(compiler);
    }

    private File resolveJspeccyFile() {
        File bundled = new File(System.getProperty("user.dir"), "libs/JSpeccy.jar");
        return bundled.exists() ? bundled : null;
    }

    private File resolveBundledJar(String prefix) {
        File libsDir = new File(System.getProperty("user.dir"), "libs");

        if (!libsDir.exists() || !libsDir.isDirectory()) return null;

        File[] matches = libsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.startsWith(prefix.toLowerCase()) && lower.endsWith(".jar");
        });

        if (matches == null || matches.length == 0) return null;

        Arrays.sort(matches);
        return matches[0];
    }

    private File resolveSpectrumArtifact(File sourceFile) {
        for (String extension : new String[] { ".tap", ".sna", ".z80" }) {
            File artifact = buildOutputFile(sourceFile, extension);
            if (artifact.exists()) return artifact;
        }

        return null;
    }

    private File buildOutputFile(File sourceFile, String extension) {
        String filePath = sourceFile.getAbsolutePath();
        int dotIndex = filePath.lastIndexOf('.');
        String basePath = dotIndex >= 0 ? filePath.substring(0, dotIndex) : filePath;
        return new File(basePath + extension);
    }

    // Detecta fallos de arranque del emulador sin bloquear la interfaz.
    private void monitorEmulatorStartup(String emulatorName, Process process, Stage ownerStage, StyleClassedTextArea consoleOutputArea) {
        if (process == null) return;

        Thread watcher = new Thread(() -> {
            try {
                boolean finishedQuickly = process.waitFor(1200, TimeUnit.MILLISECONDS);

                if (finishedQuickly) {
                    int exitCode = process.exitValue();
                    Platform.runLater(() -> {
                        terminalController.appendConsoleOutput(
                            emulatorName + " se cerró durante el arranque. Código de salida: " + exitCode,
                            consoleOutputArea
                        );
                        if (ownerStage != null) {
                            try {
                                ownerStage.toFront();
                                ownerStage.requestFocus();
                            } catch (Exception ignored) {
                            }
                        }
                    });
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "emulator-startup-watch-" + emulatorName.replace(' ', '-').toLowerCase());

        watcher.setDaemon(true);
        watcher.start();
    }

    private void handoffFocusToEmulator(Stage ownerStage, StyleClassedTextArea consoleOutputArea) {
        if (ownerStage == null) return;

        Platform.runLater(() -> {
            try {
                // Evita enviar la app al fondo: en algunos entornos deja la UI aparentemente bloqueada.
                ownerStage.requestFocus();
            } catch (Exception ignored) {
            }
        });

        terminalController.appendConsoleOutput(
            "Emulador lanzado. Si no responde al teclado, haz clic dentro de su ventana.",
            consoleOutputArea
        );
    }

    private String resolveCompilerExecutable(String configuredBinDir, String baseName) {
        String cleanDir = normalizeConfiguredPath(configuredBinDir);
        File binDir = new File(cleanDir);

        for (String candidate : new String[] { baseName + ".exe", baseName + ".bat", baseName }) {
            File compiler = new File(binDir, candidate);
            if (compiler.exists() && compiler.isFile()) {
                return compiler.getAbsolutePath();
            }
        }

        // Fallback para entornos donde la resolución depende de PATHEXT.
        return new File(binDir, baseName).getAbsolutePath();
    }

    private String resolveZ88dkClibArgument(String clibOption) {
        if (clibOption == null || clibOption.isBlank()) return "-clib=" + ConfigModel.Z88DK_CLIB_NEW;

        String normalized = clibOption.trim().toLowerCase();

        if (ConfigModel.Z88DK_CLIB_NONE.equals(normalized)) {
            return null;
        }

        return ConfigModel.getSupportedZ88dkClibOptions().contains(normalized)
            ? "-clib=" + normalized
            : "-clib=" + ConfigModel.Z88DK_CLIB_NEW;
    }

    private String normalizeZ88dkTarget(String target) {
        if (target == null || target.isBlank()) return "+zx";
        String normalized = target.trim();
        return normalized.startsWith("+") ? normalized : "+" + normalized;
    }

    private void addMacroFlags(List<String> cmd, String macros) {
        for (String token : splitCsvOrWhitespace(macros)) {
            cmd.add("-D" + token);
        }
    }

    private void addIncludeFlags(List<String> cmd, String includes) {
        if (includes == null || includes.isBlank()) return;
        String normalized = includes.replace(';', ',');
        for (String raw : normalized.split(",")) {
            String include = raw != null ? raw.trim() : "";
            if (include.isEmpty()) continue;
            cmd.add("-I" + include);
        }
    }

    private void addExtraArgs(List<String> cmd, String extraArgs) {
        cmd.addAll(splitQuotedArgs(extraArgs));
    }

    private List<String> splitCsvOrWhitespace(String input) {
        if (input == null || input.isBlank()) return java.util.Collections.emptyList();
        String normalized = input.replace(',', ' ');
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token == null) continue;
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) tokens.add(trimmed);
        }
        return tokens;
    }

    // Split free-form args preserving quoted sections.
    private List<String> splitQuotedArgs(String input) {
        List<String> args = new ArrayList<>();
        if (input == null || input.isBlank()) return args;

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args;
    }

    private String normalizeConfiguredPath(String configuredPath) {
        if (configuredPath == null) return "";

        String normalized = configuredPath.trim();

        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return normalized;
    }

    private String formatCommandForDisplay(String[] cmd) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < cmd.length; i++) {
            if (i > 0) sb.append(' ');

            String part = cmd[i];
            boolean needsQuotes = part.contains(" ") || part.contains("\t");
            sb.append(needsQuotes ? '"' + part + '"' : part);
        }

        return sb.toString();
    }

    /**
     * Devuelve el archivo asociado al tab seleccionado actualmente.
     * @param tabPane
     * @param tabFileMap
     * @return Archivo asociado o null si no hay tab seleccionado o no tiene archivo.
     */
    private File getCurrentFile(TabPane tabPane, Map<Tab, File> tabFileMap) {
        Tab tab = fxUtils.getSelectedTab(tabPane);

        if (tab == null) return null;

        return tabFileMap.get(tab);
    }
}
