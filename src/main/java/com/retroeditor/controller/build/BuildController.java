package com.retroeditor.controller.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import com.retroeditor.controller.terminal.TerminalController;
import com.retroeditor.emulator.EmulatorLauncher;
import com.retroeditor.model.ConfigModel;
import com.retroeditor.service.NativeGccBuildService;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.util.CompilerArgs;
import com.retroeditor.util.FXUtils;

import javafx.event.ActionEvent;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.fxmisc.richtext.StyleClassedTextArea;

public class BuildController {

    private static final String COMPILER_GBDK = "GBDK";
    private static final String COMPILER_Z88DK = "z88dk";
    private static final String COMPILER_GCC = "gcc";
    private static final String COMPILER_CC65 = "cc65";
    private static final String COMPILER_MAKEFILE = "Makefile";
    private static final String LEGACY_SPECTRUM = "Spectrum";
    private static final String Z88DK_PROFILE_SPECTRUM = "spectrum";
    private static final String Z88DK_PROFILE_CPC = "cpc";
    private static final String EMULATOR_EMULICIOUS = "Emulicious";
    private static final String EMULATOR_JSPECCY    = "JSpeccy";
    private static final String EMULATOR_CPCBOX_WEB = "CPCBoxWeb";
    private static final String EMULATOR_ALTIRRA    = "Altirra";
    private static final List<String> GAMEBOY_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("gb", "gbc"));
    private static final List<String> SPECTRUM_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("tap", "tzx", "sna", "z80"));
    private static final List<String> CPC_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("cdt", "wav", "dsk", "edsk"));
    private static final List<String> ATARI_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("xex", "atr", "com", "exe"));
    private static final List<String> CPC_AUTORUN_PRIORITY = Collections.unmodifiableList(Arrays.asList(
        "DISC", "DISK", "MENU", "START", "RUN", "RUNME", "LOADER", "BOOT", "GAME"
    ));
    private static final int CPC_OUTPUT_NAME_LIMIT = 4;
    private static final Pattern CPC_Z88DK_EXECUTABLE_PATTERN = Pattern.compile("\\b([A-Z0-9_]{1,8})\\.(B0|BAS|BIN|COM)\\b");
    private static final Set<String> MAKE_OUTPUT_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "gb", "gbc", "tap", "tzx", "sna", "z80", "cdt", "wav", "dsk", "edsk", "rom", "bin", "ihx", "map", "sym", "noi"
    )));

    FXUtils            fxUtils            = new FXUtils();
    TerminalController terminalController = new TerminalController();
    private TerminalController embeddedTerminalController;
    private final CompilerArgs compilerArgs = new CompilerArgs();
    private final NativeGccBuildService nativeGccBuildService = new NativeGccBuildService();
    private final AtomicReference<Process> activeCompilationProcess = new AtomicReference<>();
    private final AtomicReference<Thread> activeCompilationThread = new AtomicReference<>();
    private final BooleanProperty compilationRunning = new SimpleBooleanProperty(false);

    /**
     * Conecta el controlador de la terminal embebida (ConPTY/PTY) para poder lanzar
     * programas de consola compilados en C dentro del terminal real.
     */
    public void setEmbeddedTerminalController(TerminalController controller) {
        this.embeddedTerminalController = controller;
    }

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
                          File projectRootDir,
                          BiConsumer<File, String> compilerOutputListener) {

        if (isCompilationRunning()) {
            terminalController.appendConsoleOutput("Ya hay una compilación en curso. Espera a que termine antes de iniciar otra.", consoleOutputArea);
            return;
        }

        String selectedCompiler = configModel.getConfigProperty("compilador_seleccionado", COMPILER_GBDK);

        File selectedFile = getCurrentFile(tabPane, tabFileMap);
        if (shouldUseMakeBuild(selectedCompiler, selectedFile, projectRootDir)) {
            runMakeTarget(
                selectedFile,
                projectRootDir,
                configModel,
                selectedCompiler,
                configModel.getConfigProperty("make_build_target", ""),
                "COMPILE",
                consoleOutputArea,
                compilerOutputListener
            );
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

        UserActionMonitor.compilationStarted(selectedCompiler, file.getName());

        String   sourceFileName   = file.getName();
        String   sourceFilePath   = file.getAbsolutePath();
        int      dotIndex         = sourceFileName.lastIndexOf('.');
        String   outputBaseName   = dotIndex >= 0 ? sourceFileName.substring(0, dotIndex) : sourceFileName;
        File     outputDir        = resolveOutputDirectory(file, projectRootDir);
        File     workingDirectory = file.getParentFile();
        String[] cmd;

        if (!ensureOutputDirectory(outputDir, consoleOutputArea)) {
            return;
        }

        if (isSpectrumCompiler(selectedCompiler)) {
            String z88dkProfile = normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
            boolean cpcProfile = Z88DK_PROFILE_CPC.equalsIgnoreCase(z88dkProfile);
            String z88dkBin = resolveZ88dkBinForProfile(configModel, selectedCompiler);

            if (z88dkBin == null || z88dkBin.isBlank()) {
                Alert alert = new Alert(AlertType.ERROR);

                alert.setTitle      ("Error de compilación");
                alert.setHeaderText (cpcProfile ? "No se ha configurado la ruta de Z88DK para CPC" : "No se ha configurado la ruta de Z88DK");
                alert.setContentText(cpcProfile
                    ? "Por favor, configura la ruta del bin de Z88DK para CPC en las opciones de configuracion."
                    : "Por favor, configura la ruta del bin de Z88DK en las opciones de configuracion.");
                alert.showAndWait   ();

                terminalController.appendConsoleOutput(
                    cpcProfile
                        ? "ERROR: No se ha configurado la ruta del bin de Z88DK para CPC."
                        : "ERROR: No se ha configurado la ruta del bin de Z88DK.",
                    consoleOutputArea
                );

                return;
            }

            String compiler = resolveCompilerExecutable(z88dkBin, "zcc");
            String clibOption = configModel.getConfigProperty(
                cpcProfile ? "z88dk_cpc_clib_option" : "z88dk_clib_option",
                ConfigModel.Z88DK_CLIB_NEW
            );
            boolean useCrtOrgCode = Boolean.parseBoolean(configModel.getConfigProperty(
                cpcProfile ? "z88dk_cpc_use_pragma_crt_org_code_32768" : "z88dk_use_pragma_crt_org_code_32768",
                cpcProfile ? "false" : "true"
            ));
            boolean useCreateApp = Boolean.parseBoolean(configModel.getConfigProperty(
                cpcProfile ? "z88dk_cpc_create_app" : "z88dk_create_app",
                "true"
            ));
            String spectrumOutputBase = new File(
                outputDir,
                cpcProfile ? resolveCpcOutputBaseName(sourceFileName) : outputBaseName
            ).getAbsolutePath();
            String z88dkTarget = normalizeZ88dkTarget(configModel.getConfigProperty(
                cpcProfile ? "z88dk_cpc_target" : "z88dk_target",
                cpcProfile ? "+cpc" : "+zx"
            ));
            String z88dkExtraArgs = configModel.getConfigProperty(cpcProfile ? "z88dk_cpc_extra_args" : "z88dk_extra_args", "");

            List<String> spectrumCmd = new ArrayList<>();
            spectrumCmd.add(compiler);
            spectrumCmd.add(z88dkTarget);
            if (cpcProfile && !containsSubtypeArgument(z88dkExtraArgs)) {
                spectrumCmd.add("-subtype=dsk");
            }
            String clibArgument = resolveZ88dkClibArgument(clibOption);
            if (clibArgument != null) spectrumCmd.add(clibArgument);
            if (useCrtOrgCode) spectrumCmd.add("-pragma-define:CRT_ORG_CODE=32768");
            compilerArgs.addMacroFlags(spectrumCmd, configModel.getConfigProperty(cpcProfile ? "z88dk_cpc_defines" : "z88dk_defines", ""));
            compilerArgs.addIncludeFlags(spectrumCmd, configModel.getConfigProperty(cpcProfile ? "z88dk_cpc_includes" : "z88dk_includes", ""));
            compilerArgs.addExtraArgs(spectrumCmd, z88dkExtraArgs);
            spectrumCmd.add("-o");
            spectrumCmd.add(spectrumOutputBase);
            spectrumCmd.add(sourceFilePath);
            if (useCreateApp) spectrumCmd.add("-create-app");
            cmd = spectrumCmd.toArray(new String[0]);

        } else if (isGccCompiler(selectedCompiler)) {
            String gccBin = configModel.getConfigProperty("gcc_bin", "");
            String compiler;
            if (gccBin == null || gccBin.isBlank()) {
                if (!isCommandAvailable("gcc")) {
                    Alert alert = new Alert(AlertType.ERROR);
                    alert.setTitle      ("Error de compilación");
                    alert.setHeaderText ("No se encontró el compilador GCC");
                    alert.setContentText("GCC no está disponible en el PATH. Configura la ruta del bin de GCC en las opciones de configuración.");
                    alert.showAndWait   ();

                    terminalController.appendConsoleOutput(
                        "ERROR: No se encontró GCC. Configura la ruta del bin de GCC o añádelo al PATH.",
                        consoleOutputArea
                    );
                    return;
                }
                compiler = "gcc";
            } else {
                compiler = resolveCompilerExecutable(gccBin, "gcc");
            }

            String gccOptLevel = configModel.getConfigProperty("gcc_opt_level", "none");
            String gccDefines = configModel.getConfigProperty("gcc_defines", "");
            String gccIncludes = configModel.getConfigProperty("gcc_includes", "");
            String gccExtraArgs = configModel.getConfigProperty("gcc_extra_args", "");

            String nativeOutputName = isWindows() ? outputBaseName + ".exe" : outputBaseName;
            File outputFile = new File(outputDir, nativeOutputName);
            List<File> gccSources = nativeGccBuildService.collectNativeSources(projectRootDir, outputDir, file);
            NativeGccBuildService.GccBuildPlan plan = nativeGccBuildService.buildPlan(
                compiler,
                gccOptLevel,
                gccDefines,
                gccIncludes,
                gccExtraArgs,
                outputFile,
                gccSources
            );

            terminalController.appendConsoleOutput("Compilando con: " + compiler, consoleOutputArea);
            if (workingDirectory != null) {
                terminalController.appendConsoleOutput("Directorio de compilacion: " + workingDirectory.getAbsolutePath(), consoleOutputArea);
            }
            terminalController.appendConsoleOutput("Salida de compilacion: " + outputDir.getAbsolutePath(), consoleOutputArea);
            terminalController.appendConsoleOutput("Salida del binario: " + outputFile.getAbsolutePath(), consoleOutputArea);

            List<NativeGccBuildService.GccCompileStep> compileSteps = plan.compileSteps();
            if (compileSteps.isEmpty()) {
                terminalController.appendConsoleOutput("Nada que recompilar: todos los .o están actualizados.", consoleOutputArea);
            }
            List<String[]> commands = new ArrayList<>();
            for (NativeGccBuildService.GccCompileStep step : compileSteps) {
                terminalController.appendConsoleOutput("Compilando: " + step.source().getName(), consoleOutputArea);
                commands.add(step.command().toArray(new String[0]));
            }
            terminalController.appendConsoleOutput("Enlazando: " + outputFile.getName(), consoleOutputArea);
            commands.add(plan.linkStep().command().toArray(new String[0]));

            startCompilationStepsInBackground(
                file,
                commands,
                workingDirectory,
                selectedCompiler,
                configModel,
                consoleOutputArea,
                compilerOutputListener,
                null,
                null
            );
            return;

        } else if ("cc65".equalsIgnoreCase(selectedCompiler)) {
            String cc65Bin = configModel.getConfigProperty("cc65_bin", "");
            String cl65 = resolveCompilerExecutable(cc65Bin, "cl65");

            String outputXex = new File(outputDir, outputBaseName + ".xex").getAbsolutePath();
            List<String> cc65Cmd = new ArrayList<>();
            cc65Cmd.add(cl65);
            cc65Cmd.add("-t");
            cc65Cmd.add("atari");
            String optLevel = configModel.getConfigProperty("cc65_opt_level", "none");
            if (!optLevel.equals("none")) {
                cc65Cmd.add(optLevel);
            }
            compilerArgs.addMacroFlags(cc65Cmd, configModel.getConfigProperty("cc65_defines", ""));
            compilerArgs.addIncludeFlags(cc65Cmd, configModel.getConfigProperty("cc65_includes", ""));
            compilerArgs.addExtraArgs(cc65Cmd, configModel.getConfigProperty("cc65_extra_args", ""));
            cc65Cmd.add("-o");
            cc65Cmd.add(outputXex);
            cc65Cmd.add(sourceFilePath);

            cmd = cc65Cmd.toArray(new String[0]);

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

            compilerArgs.addMacroFlags(gbdkCmd, configModel.getConfigProperty("gbdk_defines", ""));
            compilerArgs.addIncludeFlags(gbdkCmd, configModel.getConfigProperty("gbdk_includes", ""));
            compilerArgs.addExtraArgs(gbdkCmd, configModel.getConfigProperty("gbdk_extra_args", ""));

            gbdkCmd.add("-o");
            gbdkCmd.add(new File(outputDir, outputBaseName + ".gb").getAbsolutePath());
            gbdkCmd.add(sourceFilePath);
            cmd = gbdkCmd.toArray(new String[0]);
        }

        terminalController.appendConsoleOutput("Compilando con: " + formatCommandForDisplay(cmd),consoleOutputArea);
        if (workingDirectory != null) {
            terminalController.appendConsoleOutput("Directorio de compilacion: " + workingDirectory.getAbsolutePath(), consoleOutputArea);
        }
        terminalController.appendConsoleOutput("Salida de compilacion: " + outputDir.getAbsolutePath(), consoleOutputArea);

        startCompilationInBackground(file, cmd, workingDirectory, selectedCompiler, configModel, consoleOutputArea, compilerOutputListener, null, null);
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
                                              BiConsumer<File, String> compilerOutputListener,
                                              Map<String, String> extraEnvironment,
                                              IntConsumer onFinish) {
        startCompilationStepsInBackground(
            file,
            List.<String[]>of(cmd),
            workingDirectory,
            selectedCompiler,
            configModel,
            consoleOutputArea,
            compilerOutputListener,
            extraEnvironment,
            onFinish
        );
    }

    private void startCompilationStepsInBackground(File file,
                                                   List<String[]> commands,
                                                   File workingDirectory,
                                                   String selectedCompiler,
                                                   ConfigModel configModel,
                                                   StyleClassedTextArea consoleOutputArea,
                                                   BiConsumer<File, String> compilerOutputListener,
                                                   Map<String, String> extraEnvironment,
                                                   IntConsumer onFinish) {
        updateCompilationRunning(true);

        final File buildSubject = file != null ? file : new File("build");

        Thread compileThread = new Thread(() -> {
            try {
                int exitCode = 0;
                for (String[] command : commands) {
                    exitCode = runCompilerCommand(
                        command,
                        workingDirectory,
                        selectedCompiler,
                        configModel,
                        consoleOutputArea,
                        compilerOutputListener,
                        buildSubject,
                        extraEnvironment
                    );
                    if (exitCode != 0) {
                        break;
                    }
                }

                String compilationEndLine = "Compilación finalizada. Código de salida: " + exitCode;
                terminalController.appendConsoleOutput(compilationEndLine, consoleOutputArea);
                if (compilerOutputListener != null) {
                    compilerOutputListener.accept(buildSubject, compilationEndLine);
                }
                UserActionMonitor.compilationFinished(exitCode, buildSubject.getName());
                if (onFinish != null) {
                    onFinish.accept(exitCode);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                terminalController.appendConsoleOutput("Compilación interrumpida.", consoleOutputArea);
                UserActionMonitor.compilationError("Compilación interrumpida");
            } catch (Exception e) {
                UserActionMonitor.compilationError(e.getMessage());
                terminalController.appendConsoleOutput("Error al ejecutar el compilador: " + e.getMessage(), consoleOutputArea);
            } finally {
                activeCompilationProcess.set(null);
                activeCompilationThread.compareAndSet(Thread.currentThread(), null);
                updateCompilationRunning(false);
            }
        }, "compiler-" + buildSubject.getName());

        compileThread.setDaemon(true);
        activeCompilationThread.set(compileThread);
        compileThread.start();
    }

    private int runCompilerCommand(String[] cmd,
                                   File workingDirectory,
                                   String selectedCompiler,
                                   ConfigModel configModel,
                                   StyleClassedTextArea consoleOutputArea,
                                   BiConsumer<File, String> compilerOutputListener,
                                   File buildSubject,
                                   Map<String, String> extraEnvironment) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workingDirectory != null) {
            pb.directory(workingDirectory);
        }

        pb.redirectErrorStream(true);

        if (isSpectrumCompiler(selectedCompiler)) {
            prependToPath(pb.environment(), resolveZ88dkBinForProfile(configModel, selectedCompiler));
        }

        if (isGccCompiler(selectedCompiler)) {
            prependToPath(pb.environment(), configModel.getConfigProperty("gcc_bin", ""));
        }

        if (isMakeCompiler(selectedCompiler)) {
            // Para Makefile añadimos bins de toolchains para que comandos como zcc/lcc se resuelvan.
            prependToPath(pb.environment(), configModel.getConfigProperty("gbdk_bin", ""));
            prependToPath(pb.environment(), resolveZ88dkBinForProfile(configModel, selectedCompiler));
        }

        if (extraEnvironment != null && !extraEnvironment.isEmpty()) {
            pb.environment().putAll(extraEnvironment);
        }

        Process proc = pb.start();
        activeCompilationProcess.set(proc);

        boolean missingMainHintShown = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                terminalController.appendConsoleOutput(line, consoleOutputArea);
                if (!missingMainHintShown && line.contains("undefined symbol: _main")) {
                    terminalController.appendConsoleOutput(
                        "Pista: no se detecta la función main en el .c compilado. Revisa que el archivo no esté vacío y que defina 'void main(void)' o 'int main(void)'.",
                        consoleOutputArea
                    );
                    missingMainHintShown = true;
                }
                if (compilerOutputListener != null) {
                    compilerOutputListener.accept(buildSubject, line);
                }
            }
        }

        int exitCode = proc.waitFor();
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
        activeCompilationProcess.compareAndSet(proc, null);
        return exitCode;
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
                             File projectRootDir,
                             Stage ownerStage) {

        terminalController.appendConsoleOutput("Compilando y ejecutando...", consoleOutputArea);

        String selectedCompiler = configModel.getConfigProperty("compilador_seleccionado", COMPILER_GBDK);
        File selectedFile = getCurrentFile(tabPane, tabFileMap);

        if (shouldUseMakeBuild(selectedCompiler, selectedFile, projectRootDir)) {
            runMakeTarget(
                selectedFile,
                projectRootDir,
                configModel,
                selectedCompiler,
                configModel.getConfigProperty("make_run_target", "run"),
                "EXECUTION",
                consoleOutputArea,
                null
            );
            return;
        }

        File file = selectedFile;

        if (file == null) {
            UserActionMonitor.errorOccurred("EXECUTION", "Sin archivo para ejecutar");
            terminalController.appendConsoleOutput("No hay archivo abierto para ejecutar.", consoleOutputArea);
            return;
        }

        String selectedProfile = normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
        String selectedEmulator = configModel.getConfigProperty(
            "emulador_seleccionado",
            getEmulatorForCompiler(selectedCompiler, selectedProfile)
        );

        // F6/Play compila primero y, si termina OK, lanza el emulador o el ejecutable nativo.
        onCompilar(
            event,
            consoleOutputArea,
            tabPane,
            tabFileMap,
            configModel,
            projectRootDir,
            (buildSubject, line) -> {
                if (!isSuccessfulCompilationLine(line)) return;
                if (isGccCompiler(selectedCompiler)) {
                    Platform.runLater(() -> runNativeExecutable(
                        file,
                        projectRootDir,
                        consoleOutputArea,
                        configModel.getConfigProperty("terminal_shell", "auto")
                    ));
                } else {
                    Platform.runLater(() -> launchWithEmulator(selectedEmulator, file, projectRootDir, consoleOutputArea, ownerStage, configModel));
                }
            }
        );
    }

    /**
     * Ejecuta una ROM ya generada/existente, eligiendo emulador por extensión.
     */
    public void runRomFile(File romFile,
                           StyleClassedTextArea consoleOutputArea,
                           Stage ownerStage) {
        if (romFile == null || !romFile.isFile()) {
            terminalController.appendConsoleOutput("No se puede ejecutar ROM: archivo no válido.", consoleOutputArea);
            return;
        }

        String ext = getExtension(romFile);

        if (GAMEBOY_ROM_EXTENSIONS.contains(ext)) {
            launchCoffeeGbRom(romFile, consoleOutputArea, ownerStage);
            return;
        }

        if (SPECTRUM_ROM_EXTENSIONS.contains(ext)) {
            launchJSpeccyRom(romFile, consoleOutputArea, ownerStage);
            return;
        }

        if (CPC_ROM_EXTENSIONS.contains(ext)) {
            launchCpcBoxRom(romFile, consoleOutputArea, ownerStage);
            return;
        }

        if (ATARI_ROM_EXTENSIONS.contains(ext)) {
            launchAltirraRom(romFile, consoleOutputArea, ownerStage, null);
            return;
        }

        terminalController.appendConsoleOutput(
            "Extensión de ROM no soportada para ejecución directa: ." + ext,
            consoleOutputArea
        );
    }

    public boolean isSupportedRomFile(File file) {
        if (file == null || !file.isFile()) return false;
        String ext = getExtension(file);
        return GAMEBOY_ROM_EXTENSIONS.contains(ext)
            || SPECTRUM_ROM_EXTENSIONS.contains(ext)
            || CPC_ROM_EXTENSIONS.contains(ext)
            || ATARI_ROM_EXTENSIONS.contains(ext);
    }

    private boolean shouldUseMakeBuild(String selectedCompiler, File selectedFile, File projectRootDir) {
        return isMakeCompiler(selectedCompiler)
            || isMakefileFile(selectedFile)
            || hasMakefile(projectRootDir);
    }

    private boolean hasMakefile(File directory) {
        return findMakefile(directory) != null;
    }

    private boolean isMakeCompiler(String compiler) {
        return compiler != null && COMPILER_MAKEFILE.equalsIgnoreCase(compiler);
    }

    private boolean isMakefileFile(File file) {
        if (file == null || !file.isFile()) return false;

        String name = file.getName();
        return "makefile".equalsIgnoreCase(name)
            || "gnumakefile".equalsIgnoreCase(name)
            || "makefile.mk".equalsIgnoreCase(name)
            || "makefile.mak".equalsIgnoreCase(name);
    }


    private void runMakeTarget(File selectedFile,
                               File projectRootDir,
                               ConfigModel configModel,
                               String selectedCompiler,
                               String target,
                               String monitorAction,
                               StyleClassedTextArea consoleOutputArea,
                               BiConsumer<File, String> compilerOutputListener) {
        File workingDirectory = resolveMakeWorkingDirectory(selectedFile, projectRootDir);
        File makefile = findMakefile(workingDirectory);

        if (makefile == null) {
            terminalController.appendConsoleOutput(
                "No se encontró Makefile en: " + (workingDirectory != null ? workingDirectory.getAbsolutePath() : "(sin directorio)"),
                consoleOutputArea
            );
            UserActionMonitor.errorOccurred(monitorAction, "Makefile no encontrado");
            return;
        }

        String makeExecutable = resolveMakeExecutable(configModel.getConfigProperty("make_executable", ""));
        if (makeExecutable == null || makeExecutable.isBlank()) {
            terminalController.appendConsoleOutput(
                "No se encontró una herramienta make. Configura 'make_executable' (por ejemplo: mingw32-make).",
                consoleOutputArea
            );
            UserActionMonitor.errorOccurred(monitorAction, "make no encontrado");
            return;
        }

        File outputDir = resolveOutputDirectory(makefile, projectRootDir != null ? projectRootDir : workingDirectory);
        if (!ensureOutputDirectory(outputDir, consoleOutputArea)) {
            return;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(makeExecutable);
        cmd.add("-f");
        cmd.add(makefile.getAbsolutePath());
        if (target != null && !target.isBlank()) {
            cmd.add(target.trim());
        }
        compilerArgs.addExtraArgs(cmd, configModel.getConfigProperty("make_extra_args", ""));
        // Variables de salida inyectadas por CLI: útil cuando el Makefile las usa para rutas de artefactos.
        cmd.add("OUT_DIR=" + outputDir.getAbsolutePath());
        cmd.add("OUTPUT_DIR=" + outputDir.getAbsolutePath());
        cmd.add("BUILD_DIR=" + outputDir.getAbsolutePath());

        Map<String, String> extraEnvironment = new HashMap<>();
        extraEnvironment.put("OUT_DIR", outputDir.getAbsolutePath());

        String configuredSpectrumBin = resolveZ88dkBinForProfile(configModel, selectedCompiler);
        File zccCfgDir = resolveZ88dkConfigDir(configuredSpectrumBin);
        if (zccCfgDir != null) {
            extraEnvironment.put("ZCCCFG", zccCfgDir.getAbsolutePath());

            File z88dkRoot = zccCfgDir.getParentFile() != null ? zccCfgDir.getParentFile().getParentFile() : null;
            if (z88dkRoot != null && z88dkRoot.isDirectory()) {
                extraEnvironment.put("Z88DK_PATH", z88dkRoot.getAbsolutePath());
            }
        } else if (configuredSpectrumBin != null && !configuredSpectrumBin.isBlank()) {
            terminalController.appendConsoleOutput(
                "Aviso: no se pudo resolver ZCCCFG desde spectrum_bin. Revisa la ruta de Z88DK (bin o raíz).",
                consoleOutputArea
            );
        }

        UserActionMonitor.compilationStarted("Makefile", makefile.getName());
        terminalController.appendConsoleOutput("Ejecutando Makefile con: " + formatCommandForDisplay(cmd.toArray(new String[0])), consoleOutputArea);
        terminalController.appendConsoleOutput("Directorio de trabajo: " + workingDirectory.getAbsolutePath(), consoleOutputArea);
        terminalController.appendConsoleOutput("OUT_DIR: " + outputDir.getAbsolutePath(), consoleOutputArea);
        if (extraEnvironment.containsKey("ZCCCFG")) {
            terminalController.appendConsoleOutput("ZCCCFG: " + extraEnvironment.get("ZCCCFG"), consoleOutputArea);
        }

        startCompilationInBackground(
            makefile,
            cmd.toArray(new String[0]),
            workingDirectory,
            // Forzamos modo Makefile para que se inyecten rutas de toolchains (z88dk/gbdk) en PATH.
            COMPILER_MAKEFILE,
            configModel,
            consoleOutputArea,
            compilerOutputListener,
            extraEnvironment,
            exitCode -> {
                if (exitCode == 0) {
                    relocateMakeArtifactsToOut(workingDirectory, outputDir, consoleOutputArea);
                }
            }
        );
    }

    private void relocateMakeArtifactsToOut(File workingDirectory, File outputDir, StyleClassedTextArea consoleOutputArea) {
        if (workingDirectory == null || outputDir == null || !workingDirectory.isDirectory() || !outputDir.isDirectory()) {
            return;
        }

        File[] files = workingDirectory.listFiles();
        if (files == null || files.length == 0) return;

        int moved = 0;
        for (File candidate : files) {
            if (candidate == null || !candidate.isFile()) continue;
            if (candidate.getParentFile() != null && candidate.getParentFile().equals(outputDir)) continue;

            String ext = getExtension(candidate);
            if (!MAKE_OUTPUT_EXTENSIONS.contains(ext)) continue;

            File target = new File(outputDir, candidate.getName());
            if (target.equals(candidate)) continue;

            try {
                Files.move(candidate.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                moved++;
            } catch (Exception ex) {
                terminalController.appendConsoleOutput(
                    "Aviso: no se pudo mover artefacto a out/: " + candidate.getName() + " (" + ex.getMessage() + ")",
                    consoleOutputArea
                );
            }
        }

        if (moved > 0) {
            terminalController.appendConsoleOutput(
                "Artefactos de Makefile movidos a out/: " + moved,
                consoleOutputArea
            );
        }
    }

    private File resolveMakeWorkingDirectory(File selectedFile, File projectRootDir) {
        if (projectRootDir != null && projectRootDir.isDirectory()) return projectRootDir;
        if (selectedFile != null && selectedFile.getParentFile() != null) return selectedFile.getParentFile();
        return new File(System.getProperty("user.dir"));
    }

    private File findMakefile(File directory) {
        if (directory == null || !directory.isDirectory()) return null;

        for (String candidate : new String[] { "Makefile", "makefile", "GNUmakefile" }) {
            File makefile = new File(directory, candidate);
            if (makefile.exists() && makefile.isFile()) return makefile;
        }

        return null;
    }

    private String resolveMakeExecutable(String configuredExecutable) {
        String configured = normalizeConfiguredPath(configuredExecutable);
        if (!configured.isBlank()) {
            return configured;
        }

        List<String> candidates = isWindows()
            ? Arrays.asList("mingw32-make", "make")
            : Arrays.asList("make");

        for (String candidate : candidates) {
            if (isCommandAvailable(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private File resolveZ88dkConfigDir(String configuredSpectrumBin) {
        String clean = normalizeConfiguredPath(configuredSpectrumBin);
        if (clean.isBlank()) return null;

        File spectrumPath = new File(clean);
        List<File> candidates = new ArrayList<>();

        // Caso 1: spectrum_bin apunta a la raíz de z88dk.
        candidates.add(new File(spectrumPath, "lib/config"));

        // Caso 2: spectrum_bin apunta a bin.
        File parent = spectrumPath.getParentFile();
        if (parent != null) {
            candidates.add(new File(parent, "lib/config"));
        }

        // Caso 3: instalación con subcarpeta adicional de bin (por ejemplo win32/bin).
        File grandParent = parent != null ? parent.getParentFile() : null;
        if (grandParent != null) {
            candidates.add(new File(grandParent, "lib/config"));
        }

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isDirectory()) {
                return candidate;
            }
        }

        return null;
    }

    private boolean isCommandAvailable(String command) {
        if (command == null || command.isBlank()) return false;

        if (command.contains("/") || command.contains("\\")) {
            File direct = new File(command);
            return direct.exists() && direct.isFile();
        }

        Process probe = null;
        try {
            ProcessBuilder pb = isWindows()
                ? new ProcessBuilder("cmd", "/c", "where", command)
                : new ProcessBuilder("sh", "-c", "command -v " + command);
            pb.redirectErrorStream(true);
            probe = pb.start();
            return probe.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (probe != null) {
                try { probe.getInputStream().close(); } catch (Exception ignored) {}
                try { probe.getErrorStream().close(); } catch (Exception ignored) {}
                try { probe.getOutputStream().close(); } catch (Exception ignored) {}
                probe.destroy();
            }
        }
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private void launchCoffeeGb(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File romFile = buildOutputFile(sourceFile, ".gb", projectRootDir);

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

    private void launchCoffeeGbRom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
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
                "Lanzando Emulicious con ROM: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception e) {
            UserActionMonitor.executionError("Emulicious: " + e.getMessage());
            terminalController.appendConsoleOutput("Error al lanzar Emulicious: " + e.getMessage(), consoleOutputArea);
        }
    }

    private void launchJSpeccy(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File spectrumArtifact = resolveSpectrumArtifact(sourceFile, projectRootDir);

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

        try {
            UserActionMonitor.emulatorLaunched("JSpeccy", spectrumArtifact.getName());
            EmulatorLauncher.launchJSpeccy(spectrumArtifact, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning("JSpeccy");
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando JSpeccy con: " + spectrumArtifact.getName(),
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError("JSpeccy: " + ex.getMessage());
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error al ejecutar emulador Spectrum");
            alert.setHeaderText("No se pudo iniciar JSpeccy integrado");
            alert.setContentText("Revisa que JSpeccy esté disponible en el classpath de la aplicación.");
            alert.showAndWait();
            terminalController.appendConsoleOutput("Error al lanzar JSpeccy: " + ex.getMessage(), consoleOutputArea);
        }
    }

    private void launchJSpeccyRom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        try {
            UserActionMonitor.emulatorLaunched("JSpeccy", romFile.getName());
            EmulatorLauncher.launchJSpeccy(romFile, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning("JSpeccy");
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando JSpeccy con ROM: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError("JSpeccy: " + ex.getMessage());
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error al ejecutar emulador Spectrum");
            alert.setHeaderText("No se pudo iniciar JSpeccy integrado");
            alert.setContentText("Revisa que JSpeccy esté disponible en el classpath de la aplicación.");
            alert.showAndWait();
            terminalController.appendConsoleOutput("Error al lanzar JSpeccy: " + ex.getMessage(), consoleOutputArea);
        }
    }

    private void launchCpc(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File cpcArtifact = resolveCpcArtifact(sourceFile, projectRootDir);

        if (cpcArtifact == null) {
            File cpcContainer = buildOutputFile(sourceFile, ".cpc", projectRootDir);
            terminalController.appendConsoleOutput(
                "No se encontró un archivo compatible para CPCBox (.dsk, .edsk, .wav o .cdt).",
                consoleOutputArea
            );
            if (cpcContainer.exists()) {
                terminalController.appendConsoleOutput(
                    "Se generó un .cpc, pero CPCBox necesita .dsk/.edsk/.wav/.cdt. Activa -create-app y usa -subtype=dsk o -subtype=wav.",
                    consoleOutputArea
                );
            }
            terminalController.appendConsoleOutput(
                "Compila el proyecto para CPC antes de ejecutar en el emulador web.",
                consoleOutputArea
            );
            return;
        }

        launchCpcBoxRom(cpcArtifact, consoleOutputArea, ownerStage);
    }

    private void launchCpcBoxRom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File cpcBoxIndex = resolveCpcBoxIndex();
        if (cpcBoxIndex == null) {
            terminalController.appendConsoleOutput(
                "No se encontró CPCBox en libs/cpcbox-main/cpcbox-main/index.html.",
                consoleOutputArea
            );
            return;
        }

        try {
            UserActionMonitor.emulatorLaunched(EMULATOR_CPCBOX_WEB, romFile.getName());

            WebView webView = new WebView();
            WebEngine engine = webView.getEngine();

            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState != Worker.State.SUCCEEDED) return;

                Platform.runLater(() -> {
                    try {
                        injectCpcMedia(engine, romFile, consoleOutputArea);
                    } catch (Exception ex) {
                        terminalController.appendConsoleOutput(
                            "Error al preparar medio CPC en el emulador web: " + ex.getMessage(),
                            consoleOutputArea
                        );
                    }
                });
            });

            Stage stage = new Stage();
            if (ownerStage != null) {
                stage.initOwner(ownerStage);
            }
            stage.setTitle("CPCBox Web - " + romFile.getName());
            stage.setScene(new Scene(webView, 1280, 900));
            stage.setOnShown(event -> Platform.runLater(() -> {
                try {
                    stage.toFront();
                    stage.requestFocus();
                    webView.requestFocus();
                } catch (Exception ignored) {
                }
            }));
            stage.show();

            engine.load(cpcBoxIndex.toURI().toString() + "?samaruc_autostart=0");

            UserActionMonitor.emulatorRunning(EMULATOR_CPCBOX_WEB);
            terminalController.appendConsoleOutput(
                "Emulador lanzado. Si no responde al teclado, haz clic dentro de su ventana.",
                consoleOutputArea
            );
            terminalController.appendConsoleOutput(
                "Lanzando CPCBox Web con ROM: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError(EMULATOR_CPCBOX_WEB + ": " + ex.getMessage());
            terminalController.appendConsoleOutput(
                "Error al lanzar CPCBox Web: " + ex.getMessage(),
                consoleOutputArea
            );
        }
    }

    private String getEmulatorForCompiler(String compiler, String z88dkProfile) {
        if (isSpectrumCompiler(compiler)) {
            return Z88DK_PROFILE_CPC.equalsIgnoreCase(normalizeZ88dkProfile(z88dkProfile))
                ? EMULATOR_CPCBOX_WEB
                : EMULATOR_JSPECCY;
        }
        return EMULATOR_EMULICIOUS;
    }

    private boolean isSpectrumCompiler(String compiler) {
        return COMPILER_Z88DK.equalsIgnoreCase(compiler) || LEGACY_SPECTRUM.equalsIgnoreCase(compiler);
    }

    private boolean isGccCompiler(String compiler) {
        return compiler != null && COMPILER_GCC.equalsIgnoreCase(compiler);
    }

    private String resolveZ88dkBinForProfile(ConfigModel configModel, String selectedCompiler) {
        if (!isSpectrumCompiler(selectedCompiler)) {
            return configModel.getConfigProperty("spectrum_bin", "");
        }

        String profile = normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
        if (Z88DK_PROFILE_CPC.equalsIgnoreCase(profile)) {
            String cpcBin = configModel.getConfigProperty("cpc_bin", "");
            if (cpcBin != null && !cpcBin.isBlank()) {
                return cpcBin;
            }
        }
        return configModel.getConfigProperty("spectrum_bin", "");
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

    private File resolveSpectrumArtifact(File sourceFile, File projectRootDir) {
        for (String extension : new String[] { ".tap", ".tzx", ".sna", ".z80" }) {
            File artifact = buildOutputFile(sourceFile, extension, projectRootDir);
            if (artifact.exists()) return artifact;
        }

        return null;
    }

    private File resolveCpcArtifact(File sourceFile, File projectRootDir) {
        for (String extension : new String[] { ".dsk", ".edsk", ".wav", ".cdt" }) {
            File artifact = buildOutputFile(sourceFile, extension, projectRootDir);
            if (artifact.exists()) return artifact;
        }

        File outputDir = resolveOutputDirectory(sourceFile, projectRootDir);
        File fallbackArtifact = findNewestArtifact(outputDir, CPC_ROM_EXTENSIONS);
        if (fallbackArtifact != null) {
            return fallbackArtifact;
        }

        return null;
    }

    private File resolveCpcBoxIndex() {
        File index = new File(System.getProperty("user.dir"), "libs/cpcbox-main/cpcbox-main/index.html");
        return index.isFile() ? index : null;
    }

    private void injectCpcMedia(WebEngine engine, File romFile, StyleClassedTextArea consoleOutputArea) throws Exception {
        String ext = getExtension(romFile);

        if ("cdt".equals(ext)) {
            engine.executeScript("$('#status').text('CPCBox does not support CDT auto-loading.');");
            terminalController.appendConsoleOutput(
                "Aviso: CPCBox integrado no soporta carga directa de .cdt. Se ha abierto el emulador CPC, pero para carga automática necesitas .wav.",
                consoleOutputArea
            );
            return;
        }

        byte[] data = Files.readAllBytes(romFile.toPath());
        String bytesJs = toJsArrayLiteral(data);
        String safeName = escapeJs(romFile.getName());

        if ("wav".equals(ext)) {
            terminalController.appendConsoleOutput(
                "Preparando cinta CPC en CPCBox: " + romFile.getName(),
                consoleOutputArea
            );
            engine.executeScript("window.samarucLoadTapeWav('" + safeName + "', " + bytesJs + ");");
            return;
        }

        if ("dsk".equals(ext) || "edsk".equals(ext)) {
            String resolvedAutoCommand = resolveCpcDiskAutoCommand(romFile, data);
            terminalController.appendConsoleOutput(
                "Preparando disco CPC en CPCBox: " + romFile.getName(),
                consoleOutputArea
            );
            terminalController.appendConsoleOutput(
                "CPC autorun command: " + resolvedAutoCommand.replace("\n", "\\n"),
                consoleOutputArea
            );
            String autoCommand = escapeJs(resolvedAutoCommand);
            engine.executeScript("window.samarucLoadDisk('" + safeName + "', " + bytesJs + ", '" + autoCommand + "');");
        }
    }

    private String resolveCpcDiskAutoCommand(File romFile, byte[] data) {
        List<CpcDirectoryEntry> entries = extractCpcDirectoryEntries(data);
        if (entries.isEmpty()) {
            String fallback = extractCpcLoaderCommand(romFile, data);
            return fallback != null ? fallback : "CAT\n";
        }

        boolean hasBasicLoader = entries.stream().anyMatch(entry -> "BAS".equals(entry.extension) || "BIN".equals(entry.extension));
        boolean looksLikeCpmDisk = !hasBasicLoader && entries.stream().anyMatch(entry -> "COM".equals(entry.extension));
        if (looksLikeCpmDisk) {
            return "|CPM\n";
        }

        String diskStem = romFile != null ? normalizeCpcName(stripExtension(romFile.getName())) : "";

        for (CpcDirectoryEntry entry : entries) {
            if (!entry.isRunnable()) continue;
            String normalizedName = normalizeCpcName(entry.baseName);
            if (!diskStem.isBlank() && (diskStem.contains(normalizedName) || normalizedName.contains(diskStem))) {
                return "RUN\"" + entry.baseName + "\n";
            }
        }

        for (String preferred : CPC_AUTORUN_PRIORITY) {
            for (CpcDirectoryEntry entry : entries) {
                if (entry.isRunnable() && preferred.equals(entry.baseName)) {
                    return "RUN\"" + entry.baseName + "\n";
                }
            }
        }

        for (CpcDirectoryEntry entry : entries) {
            if ("BAS".equals(entry.extension)) {
                return "RUN\"" + entry.baseName + "\n";
            }
        }

        for (CpcDirectoryEntry entry : entries) {
            if ("BIN".equals(entry.extension)) {
                return "RUN\"" + entry.baseName + "\n";
            }
        }

        String fallback = extractCpcLoaderCommand(romFile, data);
        if (fallback != null) {
            return fallback;
        }

        return "CAT\n";
    }

    private List<CpcDirectoryEntry> extractCpcDirectoryEntries(byte[] data) {
        if (data == null || data.length < 0x200) {
            return Collections.emptyList();
        }

        boolean extended = startsWith(data, "EXTENDED CPC DSK File") || startsWith(data, "EXTENDED");
        if (!extended && !startsWith(data, "MV - CPC")) {
            return Collections.emptyList();
        }

        int trackCount = Byte.toUnsignedInt(data[0x30]);
        int sideCount = Math.max(1, Byte.toUnsignedInt(data[0x31]));
        if (trackCount <= 0) {
            return Collections.emptyList();
        }

        int trackIndex = 0; // Track 0 / Side 0
        int offset = 0x100;
        for (int i = 0; i < trackIndex; i++) {
            int trackSize = readTrackSize(data, extended, i);
            if (trackSize <= 0 || offset + trackSize > data.length) {
                return Collections.emptyList();
            }
            offset += trackSize;
        }

        int trackSize = readTrackSize(data, extended, trackIndex);
        if (trackSize <= 0 || offset + Math.min(trackSize, 0x100) > data.length) {
            return Collections.emptyList();
        }

        int trackHeaderOffset = offset;
        if (!startsWith(data, trackHeaderOffset, "Track-Info")) {
            return Collections.emptyList();
        }

        int sectorCount = Byte.toUnsignedInt(data[trackHeaderOffset + 0x15]);
        int sectorInfoOffset = trackHeaderOffset + 0x18;
        int sectorDataOffset = trackHeaderOffset + 0x100;
        List<byte[]> directorySectors = new ArrayList<>();

        for (int i = 0; i < sectorCount; i++) {
            int infoOffset = sectorInfoOffset + (i * 8);
            if (infoOffset + 8 > data.length) break;

            int sectorId = Byte.toUnsignedInt(data[infoOffset + 2]);
            int sectorSizeCode = Byte.toUnsignedInt(data[infoOffset + 3]);
            int actualSize = extended
                ? (Byte.toUnsignedInt(data[infoOffset + 6]) | (Byte.toUnsignedInt(data[infoOffset + 7]) << 8))
                : (128 << sectorSizeCode);
            if (actualSize <= 0) {
                actualSize = 128 << sectorSizeCode;
            }

            if (actualSize <= 0 || sectorDataOffset + actualSize > data.length) {
                break;
            }

            boolean isDirectorySector = sectorId >= 0xC1 && sectorId <= 0xC4;
            if (isDirectorySector || directorySectors.size() < 4) {
                directorySectors.add(Arrays.copyOfRange(data, sectorDataOffset, sectorDataOffset + actualSize));
            }

            sectorDataOffset += actualSize;
        }

        if (directorySectors.isEmpty()) {
            return Collections.emptyList();
        }

        List<CpcDirectoryEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (byte[] sector : directorySectors) {
            for (int offsetInSector = 0; offsetInSector + 32 <= sector.length; offsetInSector += 32) {
                int user = Byte.toUnsignedInt(sector[offsetInSector]);
                if (user == 0xE5 || user > 0x1F) {
                    continue;
                }

                String baseName = decodeCpcDirectoryName(sector, offsetInSector + 1, 8);
                String extension = decodeCpcDirectoryName(sector, offsetInSector + 9, 3);
                if (baseName.isBlank()) {
                    continue;
                }

                String key = baseName + "." + extension;
                if (seen.add(key)) {
                    entries.add(new CpcDirectoryEntry(baseName, extension));
                }
            }
        }

        return entries;
    }

    private int readTrackSize(byte[] data, boolean extended, int trackIndex) {
        if (extended) {
            int sizeIndex = 0x34 + trackIndex;
            if (sizeIndex >= data.length) return -1;
            return Byte.toUnsignedInt(data[sizeIndex]) * 256;
        }
        return Byte.toUnsignedInt(data[0x32]) * 256;
    }

    private boolean startsWith(byte[] data, String text) {
        return startsWith(data, 0, text);
    }

    private boolean startsWith(byte[] data, int offset, String text) {
        byte[] expected = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        if (data == null || offset < 0 || offset + expected.length > data.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private String extractCpcLoaderCommand(File romFile, byte[] data) {
        String fromImage = extractCpcBasicLoaderFromImage(data);
        if (fromImage != null) {
            return fromImage;
        }

        String sourceName = romFile != null ? normalizeCpcName(stripExtension(romFile.getName())) : "";
        if (!sourceName.isBlank()) {
            return "RUN\"" + sourceName + "\n";
        }

        return null;
    }

    private String extractCpcBasicLoaderFromImage(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        String ascii = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
        Matcher matcher = CPC_Z88DK_EXECUTABLE_PATTERN.matcher(ascii);
        if (matcher.find()) {
            if ("B0".equals(matcher.group(2))) {
                return "RUN\"" + matcher.group(1) + "\n";
            }
            return "RUN\"" + matcher.group(1) + "." + matcher.group(2) + "\n";
        }

        return null;
    }

    private String decodeCpcDirectoryName(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length && offset + i < data.length; i++) {
            int value = Byte.toUnsignedInt(data[offset + i]) & 0x7F;
            if (value == 0 || value == ' ') {
                continue;
            }
            sb.append((char) value);
        }
        return sb.toString().trim().toUpperCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String normalizeCpcName(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return normalized.length() > CPC_OUTPUT_NAME_LIMIT ? normalized.substring(0, CPC_OUTPUT_NAME_LIMIT) : normalized;
    }

    private String resolveCpcOutputBaseName(String sourceFileName) {
        if (sourceFileName == null || sourceFileName.isBlank()) {
            return "CPC";
        }

        return normalizeCpcName(stripExtension(sourceFileName));
    }

    private static final class CpcDirectoryEntry {
        private final String baseName;
        private final String extension;

        private CpcDirectoryEntry(String baseName, String extension) {
            this.baseName = baseName;
            this.extension = extension != null ? extension : "";
        }

        private boolean isRunnable() {
            return extension == null
                || extension.isBlank()
                || "BAS".equals(extension)
                || "BIN".equals(extension);
        }
    }

    private String toJsArrayLiteral(byte[] data) {
        StringBuilder sb = new StringBuilder(Math.max(2, data.length * 4 + 2));
        sb.append('[');
        for (int i = 0; i < data.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Byte.toUnsignedInt(data[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    private String escapeJs(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", " ");
    }

    private File buildOutputFile(File sourceFile, String extension, File projectRootDir) {
        String sourceName = sourceFile != null ? sourceFile.getName() : "program.c";
        int dotIndex = sourceName.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? sourceName.substring(0, dotIndex) : sourceName;
        if (CPC_ROM_EXTENSIONS.contains(getExtensionName(extension))) {
            baseName = resolveCpcOutputBaseName(sourceName);
        }
        File outputDir = resolveOutputDirectory(sourceFile, projectRootDir);
        return new File(outputDir, baseName + extension);
    }

    private String getExtensionName(String extension) {
        if (extension == null) return "";
        String cleaned = extension.startsWith(".") ? extension.substring(1) : extension;
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private File resolveOutputDirectory(File sourceFile, File projectRootDir) {
        if (projectRootDir != null && projectRootDir.isDirectory()) {
            return new File(projectRootDir, "out");
        }

        File sourceParent = sourceFile != null ? sourceFile.getParentFile() : null;
        if (sourceParent != null) {
            return new File(sourceParent, "out");
        }

        return new File(new File(System.getProperty("user.dir")), "out");
    }

    private boolean isSuccessfulCompilationLine(String line) {
        return line != null && line.startsWith("Compilación finalizada. Código de salida: 0");
    }

    private void launchWithEmulator(String selectedEmulator,
                                    File sourceFile,
                                    File projectRootDir,
                                    StyleClassedTextArea consoleOutputArea,
                                    Stage ownerStage,
                                    ConfigModel configModel) {
        UserActionMonitor.executionStarted(selectedEmulator, sourceFile != null ? sourceFile.getName() : "(sin archivo)");

        if (EMULATOR_EMULICIOUS.equalsIgnoreCase(selectedEmulator)) {
            launchCoffeeGb(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            return;
        }

        if (EMULATOR_JSPECCY.equalsIgnoreCase(selectedEmulator)) {
            launchJSpeccy(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            return;
        }

        if (EMULATOR_CPCBOX_WEB.equalsIgnoreCase(selectedEmulator)) {
            launchCpc(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            return;
        }

        if (EMULATOR_ALTIRRA.equalsIgnoreCase(selectedEmulator) || "cc65".equalsIgnoreCase(configModel.getConfigProperty("compilador_seleccionado", ""))) {
            launchAltirra(sourceFile, projectRootDir, consoleOutputArea, ownerStage, configModel);
            return;
        }

        terminalController.appendConsoleOutput(
            "Emulador no soportado o no configurado: " + selectedEmulator,
            consoleOutputArea
        );
    }

    private File resolveAtariArtifact(File sourceFile, File projectRootDir) {
        for (String extension : new String[] { ".xex", ".com", ".atr", ".exe" }) {
            File artifact = buildOutputFile(sourceFile, extension, projectRootDir);
            if (artifact.exists()) return artifact;
        }

        File outputDir = resolveOutputDirectory(sourceFile, projectRootDir);
        File fallbackArtifact = findNewestArtifact(outputDir, Arrays.asList("xex", "com", "atr", "exe"));
        if (fallbackArtifact != null) {
            return fallbackArtifact;
        }

        return null;
    }

    private void launchAltirra(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage, ConfigModel configModel) {
        File atariArtifact = resolveAtariArtifact(sourceFile, projectRootDir);
        if (atariArtifact == null || !atariArtifact.exists()) {
            terminalController.appendConsoleOutput("No se encontró el archivo ejecutable para Atari (.xex/.atr/.com). Compila el proyecto antes de ejecutar.", consoleOutputArea);
            return;
        }
        launchAltirraRom(atariArtifact, consoleOutputArea, ownerStage, configModel);
    }

    private void launchAltirraRom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage, ConfigModel configModel) {
        try {
            String altirraPath = configModel != null ? configModel.getConfigProperty("altirra_bin", "") : "";
            File altirraExe = altirraPath.isBlank() ? new File("altirra64.exe") : new File(altirraPath);
            if (!altirraExe.exists()) {
                altirraExe = new File("altirra.exe");
            }
            if (!altirraExe.exists() && !altirraPath.isBlank()) {
                altirraExe = new File(altirraPath, "altirra64.exe");
            }
            if (!altirraExe.exists() && !altirraPath.isBlank()) {
                altirraExe = new File(altirraPath, "altirra.exe");
            }

            if (!altirraExe.exists()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Error al ejecutar emulador Altirra");
                alert.setHeaderText("No se encontró el ejecutable de Altirra");
                alert.setContentText("Por favor, configura la ruta de Altirra en las opciones de configuración.");
                alert.showAndWait();
                terminalController.appendConsoleOutput("ERROR: No se encontró el ejecutable de Altirra en: " + altirraPath, consoleOutputArea);
                return;
            }

            UserActionMonitor.emulatorLaunched("Altirra", romFile.getName());
            EmulatorLauncher.launchAltirra(altirraExe, romFile, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning("Altirra");
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando Altirra con archivo: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError("Altirra: " + ex.getMessage());
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error al ejecutar Altirra");
            alert.setHeaderText("No se pudo iniciar Altirra");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
            terminalController.appendConsoleOutput("Error al lanzar Altirra: " + ex.getMessage(), consoleOutputArea);
        }
    }

    /**
     * Ejecuta el binario nativo generado por el compilador GCC (C no retro).
     */
    private void runNativeExecutable(File sourceFile,
                                     File projectRootDir,
                                     StyleClassedTextArea consoleOutputArea,
                                     String terminalShell) {
        if (sourceFile == null) {
            terminalController.appendConsoleOutput("No hay archivo abierto para ejecutar.", consoleOutputArea);
            return;
        }

        String baseName = stripExtension(sourceFile.getName());
        File outputDir = resolveOutputDirectory(sourceFile, projectRootDir);
        File executable = new File(outputDir, isWindows() ? baseName + ".exe" : baseName);

        if (!executable.exists() || !executable.isFile()) {
            terminalController.appendConsoleOutput(
                "No se encontró el ejecutable generado: " + executable.getAbsolutePath(),
                consoleOutputArea
            );
            terminalController.appendConsoleOutput(
                "Compila el archivo con GCC antes de ejecutar.",
                consoleOutputArea
            );
            return;
        }

        terminalController.appendConsoleOutput("Ejecutando: " + executable.getAbsolutePath(), consoleOutputArea);
        UserActionMonitor.executionStarted("Native", executable.getName());

        if (embeddedTerminalController != null
                && embeddedTerminalController.executeCommandInTerminal(buildTerminalCommand(executable, outputDir, terminalShell))) {
            terminalController.appendConsoleOutput(
                "Programa lanzado en la terminal embebida. Para salir usa Ctrl+C (Windows) o Ctrl+C (Linux).",
                consoleOutputArea
            );
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath());
            pb.directory(outputDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            Thread runThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        terminalController.appendConsoleOutput(line, consoleOutputArea);
                    }
                } catch (Exception ignored) {
                }

                try {
                    int exitCode = proc.waitFor();
                    terminalController.appendConsoleOutput(
                        "Programa finalizado. Código de salida: " + exitCode,
                        consoleOutputArea
                    );
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }, "native-exec-" + executable.getName());
            runThread.setDaemon(true);
            runThread.start();
        } catch (Exception e) {
            UserActionMonitor.executionError("Native: " + e.getMessage());
            terminalController.appendConsoleOutput("Error al ejecutar el programa: " + e.getMessage(), consoleOutputArea);
        }
    }

    /**
     * Construye el comando que se escribe en el shell de la terminal embebida para
     * ejecutar el binario nativo en el directorio de salida.
     */
    private String buildTerminalCommand(File executable, File outputDir, String shell) {
        if ("powershell".equalsIgnoreCase(shell) || "pwsh".equalsIgnoreCase(shell)) {
            String dir = outputDir.getAbsolutePath().replace("'", "''");
            String exe = executable.getAbsolutePath().replace("'", "''");
            return "Set-Location -LiteralPath '" + dir + "'; & '" + exe + "'";
        }
        if (isWindows()) {
            return "cd /d \"" + outputDir.getAbsolutePath() + "\" && \"" + executable.getAbsolutePath() + "\"";
        }
        return "cd \"" + outputDir.getAbsolutePath() + "\" && \"./" + executable.getName() + "\"";
    }

    private File findNewestArtifact(File outputDir, List<String> extensions) {
        if (outputDir == null || !outputDir.isDirectory()) {
            return null;
        }

        File[] candidates = outputDir.listFiles(file -> {
            if (file == null || !file.isFile()) return false;
            String ext = getExtension(file);
            return extensions.contains(ext);
        });

        if (candidates == null || candidates.length == 0) {
            return null;
        }

        File newest = candidates[0];
        for (int i = 1; i < candidates.length; i++) {
            if (candidates[i].lastModified() > newest.lastModified()) {
                newest = candidates[i];
            }
        }
        return newest;
    }

    private boolean ensureOutputDirectory(File outputDir, StyleClassedTextArea consoleOutputArea) {
        if (outputDir.exists() && outputDir.isDirectory()) return true;
        if (outputDir.mkdirs()) return true;

        terminalController.appendConsoleOutput(
            "No se pudo crear la carpeta de salida: " + outputDir.getAbsolutePath(),
            consoleOutputArea
        );
        return false;
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

    private boolean containsSubtypeArgument(String extraArgs) {
        for (String arg : compilerArgs.splitQuotedArgs(extraArgs)) {
            if (arg != null && arg.toLowerCase(Locale.ROOT).startsWith("-subtype")) {
                return true;
            }
        }
        return false;
    }

    private String normalizeZ88dkProfile(String profile) {
        return Z88DK_PROFILE_CPC.equalsIgnoreCase(profile) ? Z88DK_PROFILE_CPC : Z88DK_PROFILE_SPECTRUM;
    }

    private String normalizeConfiguredPath(String configuredPath) {
        if (configuredPath == null) return "";

        String normalized = configuredPath.trim();

        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return normalized;
    }

    private void prependToPath(Map<String, String> environment, String configuredPath) {
        if (environment == null) return;

        String cleanPath = normalizeConfiguredPath(configuredPath);
        if (cleanPath.isBlank()) return;

        File pathDir = new File(cleanPath);
        if (!pathDir.exists() || !pathDir.isDirectory()) return;

        String currentPath = environment.get("PATH");
        if (currentPath == null || currentPath.isBlank()) {
            environment.put("PATH", cleanPath);
            return;
        }

        String[] parts = currentPath.split(java.util.regex.Pattern.quote(File.pathSeparator));
        for (String part : parts) {
            if (cleanPath.equalsIgnoreCase(part)) {
                return;
            }
        }

        environment.put("PATH", cleanPath + File.pathSeparator + currentPath);
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

    private String getExtension(File file) {
        if (file == null) return "";

        String name = file.getName();
        int idx = name.lastIndexOf('.');

        if (idx < 0 || idx == name.length() - 1) return "";

        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
