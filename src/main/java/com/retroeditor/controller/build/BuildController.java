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
import java.util.Comparator;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
    private static final String Z88DK_PROFILE_SMS = "sms";
    private static final String Z88DK_PROFILE_GAME_GEAR = "gg";
    private static final String Z88DK_PROFILE_MSX = "msx";
    /**
     * Target zcc del Master System. El Game Gear comparte toolchain y se
     * distingue con {@code -subtype=gamegear}: z88dk no tiene target {@code +gg}.
     */
    private static final String Z88DK_TARGET_SMS = "+sms";
    /**
     * Fuente de apoyo del target CPC. La copia integrada de cpc_clib.lib no
     * incluye el modulo fcntl/writebyte.c que invoca stdio/fputc_callee.c, asi
     * que cualquier llamada a printf falla al enlazar con
     * "undefined symbol: writebyte".
     */
    private static final String Z88DK_CPC_SUPPORT_SOURCE = "libsrc/target/cpc/fcntl/writebyte.c";
    private static final String EMULATOR_EMULICIOUS = "Emulicious";
    private static final String EMULATOR_JSPECCY    = "JSpeccy";
    private static final String EMULATOR_CAP32 = "Cap32";
    private static final String EMULATOR_CPCBOX_WEB_LEGACY = "CPCBoxWeb";
    private static final String EMULATOR_ALTIRRA    = "Altirra";
    private static final String EMULATOR_VICE       = "VICE";
    private static final String EMULATOR_OPENMSX    = "openMSX";
    private static final String EMULATOR_MESEN      = "Mesen";
    private static final String CC65_TARGET_ATARI   = "atari";
    private static final String CC65_TARGET_C64     = "c64";
    private static final String CC65_TARGET_NES     = "nes";
    private static final List<String> GAMEBOY_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("gb", "gbc"));
    private static final List<String> SPECTRUM_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("tap", "tzx", "sna", "z80"));
    private static final List<String> SMS_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("sms", "gg"));
    private static final List<String> MSX_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("rom", "bin", "dsk", "ds2"));
    private static final List<String> NES_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("nes"));
    private static final List<String> CPC_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("cdt", "wav", "dsk", "edsk", "voc", "cpr", "sna"));

    /** Formatos de medio que Caprice32 carga por extension. */
    private static final List<String> CPC_CAP32_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("dsk", "cdt", "voc", "cpr", "sna", "zip"));
    private static final List<String> ATARI_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("xex", "atr", "com", "exe"));
    private static final List<String> C64_ROM_EXTENSIONS = Collections.unmodifiableList(Arrays.asList("prg", "d64", "t64", "tap", "crt"));
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
            String z88dkBin = resolveEffectiveZ88dkBin(configModel, selectedCompiler);

            if (z88dkBin == null || z88dkBin.isBlank()) {
                String profileLabel = profileLabel(z88dkProfile);
                Alert alert = new Alert(AlertType.ERROR);

                alert.setTitle      ("Error de compilación");
                alert.setHeaderText ("No se ha configurado la ruta de Z88DK para " + profileLabel);
                alert.setContentText("No se ha configurado la ruta del bin de Z88DK para " + profileLabel + " y no se encuentra la copia integrada en libs/devkits/z88dk.");
                alert.showAndWait   ();

                terminalController.appendConsoleOutput(
                    "ERROR: No se ha configurado la ruta del bin de Z88DK para " + profileLabel + ".",
                    consoleOutputArea
                );

                return;
            }

            String compiler = resolveCompilerExecutable(z88dkBin, "zcc");
            String clibOption = configModel.getConfigProperty(
                z88dkProfileKey(z88dkProfile, "clib_option"),
                z88dkDefaultClib(z88dkProfile)
            );
            if (!Z88DK_PROFILE_SPECTRUM.equals(z88dkProfile)
                    && (clibOption == null || ConfigModel.Z88DK_CLIB_NEW.equalsIgnoreCase(clibOption.trim()))) {
                clibOption = ConfigModel.Z88DK_CLIB_DEFAULT;
            }
            boolean useCrtOrgCode = Boolean.parseBoolean(configModel.getConfigProperty(
                z88dkProfileKey(z88dkProfile, "use_pragma_crt_org_code_32768"),
                Z88DK_PROFILE_SPECTRUM.equals(z88dkProfile) ? "true" : "false"
            ));
            boolean useCreateApp = Boolean.parseBoolean(configModel.getConfigProperty(
                z88dkProfileKey(z88dkProfile, "create_app"),
                "true"
            ));
            String spectrumOutputBase = new File(
                outputDir,
                cpcProfile ? resolveCpcOutputBaseName(sourceFileName) : outputBaseName
            ).getAbsolutePath();
            String z88dkTarget = normalizeZ88dkTarget(configModel.getConfigProperty(
                z88dkProfileKey(z88dkProfile, "target"),
                z88dkDefaultTarget(z88dkProfile)
            ));
            String z88dkExtraArgs = configModel.getConfigProperty(z88dkProfileKey(z88dkProfile, "extra_args"), "");
            String subtype = z88dkDefaultSubtype(z88dkProfile);

            List<String> spectrumCmd = new ArrayList<>();
            spectrumCmd.add(compiler);
            spectrumCmd.add(z88dkTarget);
            if (subtype != null && !containsSubtypeArgument(z88dkExtraArgs)) {
                spectrumCmd.add("-subtype=" + subtype);
            }
            String clibArgument = resolveZ88dkClibArgument(clibOption);
            if (clibArgument != null) spectrumCmd.add(clibArgument);
            if (useCrtOrgCode) spectrumCmd.add("-pragma-define:CRT_ORG_CODE=32768");
            compilerArgs.addMacroFlags(spectrumCmd, configModel.getConfigProperty(z88dkProfileKey(z88dkProfile, "defines"), ""));
            compilerArgs.addIncludeFlags(spectrumCmd, configModel.getConfigProperty(z88dkProfileKey(z88dkProfile, "includes"), ""));
            compilerArgs.addExtraArgs(spectrumCmd, z88dkExtraArgs);
            spectrumCmd.add("-o");
            spectrumCmd.add(spectrumOutputBase);
            spectrumCmd.addAll(z88dkSupportSources(z88dkProfile, compiler));
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
            String cl65;
            if (cc65Bin != null && !cc65Bin.isBlank()) {
                cl65 = resolveCompilerExecutable(cc65Bin, "cl65");
            } else {
                File bundledCc65 = resolveBundledCc65Bin();
                if (bundledCc65 == null) {
                    Alert alert = new Alert(AlertType.ERROR);
                    alert.setTitle("Error de compilación");
                    alert.setHeaderText("No se encontró CC65 integrado");
                    alert.setContentText("No se ha encontrado la distribución de CC65 incluida con Samaruc en la carpeta libs/devkits/cc65/bin.");
                    alert.showAndWait();

                    terminalController.appendConsoleOutput("ERROR: No se encontró la distribución de CC65 integrada en libs/devkits/cc65/bin.", consoleOutputArea);

                    return;
                }
                cl65 = resolveCompilerExecutable(bundledCc65.getAbsolutePath(), "cl65");
            }

            String cc65Target = normalizeCc65Target(configModel.getConfigProperty("cc65_target", CC65_TARGET_ATARI));
            String cc65TargetFlag = cc65TargetActual(cc65Target);
            String cc65ArtifactExtension = cc65ArtifactExtension(cc65Target);
            String outputArtifact = new File(outputDir, outputBaseName + cc65ArtifactExtension).getAbsolutePath();
            List<String> cc65Cmd = new ArrayList<>();
            cc65Cmd.add(cl65);
            cc65Cmd.add("-t");
            cc65Cmd.add(cc65TargetFlag);
            String optLevel = configModel.getConfigProperty("cc65_opt_level", "none");
            if (!optLevel.equals("none")) {
                cc65Cmd.add(optLevel);
            }
            compilerArgs.addMacroFlags(cc65Cmd, configModel.getConfigProperty("cc65_defines", ""));
            compilerArgs.addIncludeFlags(cc65Cmd, configModel.getConfigProperty("cc65_includes", ""));
            compilerArgs.addExtraArgs(cc65Cmd, configModel.getConfigProperty("cc65_extra_args", ""));
            cc65Cmd.add("-o");
            cc65Cmd.add(outputArtifact);
            cc65Cmd.add(sourceFilePath);

            cmd = cc65Cmd.toArray(new String[0]);

        } else {
            File gbdkBinDir = resolveBundledGbdkBin();

            if (gbdkBinDir == null) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle      ("Error de compilación");
                alert.setHeaderText ("No se encontró GBDK integrado");
                alert.setContentText("No se ha encontrado la distribución de GBDK incluida con Samaruc en la carpeta libs/devkits/gbdk/bin.");
                alert.showAndWait   ();

                terminalController.appendConsoleOutput("ERROR: No se encontró la distribución de GBDK integrada en libs/devkits/gbdk/bin.", consoleOutputArea);

                return;
            }

            String compiler = resolveCompilerExecutable(gbdkBinDir.getAbsolutePath(), "lcc");
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
            prependToPath(pb.environment(), resolveEffectiveZ88dkBin(configModel, selectedCompiler));
        }

        if (isGccCompiler(selectedCompiler)) {
            prependToPath(pb.environment(), configModel.getConfigProperty("gcc_bin", ""));
        }

        if (isMakeCompiler(selectedCompiler)) {
            // Para Makefile añadimos bins de toolchains para que comandos como zcc/lcc se resuelvan.
            File gbdkBinDir = resolveBundledGbdkBin();
            if (gbdkBinDir != null) {
                prependToPath(pb.environment(), gbdkBinDir.getAbsolutePath());
            }
            prependToPath(pb.environment(), resolveEffectiveZ88dkBin(configModel, selectedCompiler));
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
        String selectedEmulator = ConfigModel.normalizeEmulatorSelection(configModel.getConfigProperty(
            "emulador_seleccionado",
            getEmulatorForCompiler(selectedCompiler, selectedProfile, configModel)
        ));

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

        if (SMS_ROM_EXTENSIONS.contains(ext)) {
            launchSmsRom(romFile, consoleOutputArea, ownerStage);
            return;
        }

        if (MSX_ROM_EXTENSIONS.contains(ext)) {
            launchMsxRom(romFile, consoleOutputArea, ownerStage);
            return;
        }

        if (NES_ROM_EXTENSIONS.contains(ext)) {
            launchNesRom(romFile, consoleOutputArea, ownerStage);
            return;
        }

        if (CPC_ROM_EXTENSIONS.contains(ext)) {
            launchCap32Rom(romFile, consoleOutputArea, ownerStage);
            return;
        }

        if (ATARI_ROM_EXTENSIONS.contains(ext)) {
            launchAltirraRom(romFile, consoleOutputArea, ownerStage, null);
            return;
        }

        if (C64_ROM_EXTENSIONS.contains(ext)) {
            launchViceRom(romFile, consoleOutputArea, ownerStage, null);
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
            || SMS_ROM_EXTENSIONS.contains(ext)
            || MSX_ROM_EXTENSIONS.contains(ext)
            || NES_ROM_EXTENSIONS.contains(ext)
            || ATARI_ROM_EXTENSIONS.contains(ext)
            || C64_ROM_EXTENSIONS.contains(ext);
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

        String configuredSpectrumBin = resolveEffectiveZ88dkBin(configModel, selectedCompiler);
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

    private void launchSms(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File smsArtifact = resolveSmsArtifact(sourceFile, projectRootDir);

        if (smsArtifact == null || !smsArtifact.exists()) {
            terminalController.appendConsoleOutput(
                "No se encontró un archivo SMS/Game Gear (.sms o .gg). Compila el proyecto antes de ejecutar.",
                consoleOutputArea
            );
            return;
        }

        launchSmsRom(smsArtifact, consoleOutputArea, ownerStage);
    }

    private void launchSmsRom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File emuliciousJar = resolveBundledJar("Emulicious");

        if (emuliciousJar == null) {
            terminalController.appendConsoleOutput(
                "No se encontró Emulicious.jar en la carpeta libs.",
                consoleOutputArea
            );
            return;
        }

        try {
            UserActionMonitor.emulatorLaunched(EMULATOR_EMULICIOUS, romFile.getName());
            EmulatorLauncher.LaunchResult launch = EmulatorLauncher.launchSmsEmulator(emuliciousJar, romFile, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning(EMULATOR_EMULICIOUS);
            monitorEmulatorStartup(EMULATOR_EMULICIOUS, launch.process, ownerStage, consoleOutputArea);
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando Emulicious con ROM SMS/Game Gear: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception e) {
            UserActionMonitor.executionError(EMULATOR_EMULICIOUS + ": " + e.getMessage());
            terminalController.appendConsoleOutput("Error al lanzar Emulicious: " + e.getMessage(), consoleOutputArea);
        }
    }

    private void launchMsx(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File msxArtifact = resolveMsxArtifact(sourceFile, projectRootDir);

        if (msxArtifact == null || !msxArtifact.exists()) {
            terminalController.appendConsoleOutput(
                "No se encontró un archivo MSX (.rom/.bin/.dsk). Compila el proyecto antes de ejecutar.",
                consoleOutputArea
            );
            return;
        }

        launchMsxRom(msxArtifact, consoleOutputArea, ownerStage);
    }

    private void launchMsxRom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        try {
            File openMsxExe = EmulatorLauncher.resolveBundledOpenMsxExecutable();
            if (openMsxExe == null) {
                terminalController.appendConsoleOutput(
                    "No se encontró openMSX en libs/emulators/openmsx/openmsx.exe.",
                    consoleOutputArea
                );
                return;
            }

            UserActionMonitor.emulatorLaunched(EMULATOR_OPENMSX, romFile.getName());
            EmulatorLauncher.LaunchResult launch = EmulatorLauncher.launchOpenMsx(openMsxExe, romFile, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning(EMULATOR_OPENMSX);
            monitorEmulatorStartup(EMULATOR_OPENMSX, launch.process, ownerStage, consoleOutputArea);
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando openMSX con: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError(EMULATOR_OPENMSX + ": " + ex.getMessage());
            terminalController.appendConsoleOutput(
                "Error al lanzar openMSX: " + ex.getMessage(),
                consoleOutputArea
            );
        }
    }

    private void launchNes(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File nesArtifact = resolveNesArtifact(sourceFile, projectRootDir);

        if (nesArtifact == null || !nesArtifact.exists()) {
            terminalController.appendConsoleOutput(
                "No se encontró un archivo NES (.nes). Compila el proyecto antes de ejecutar.",
                consoleOutputArea
            );
            return;
        }

        launchNesRom(nesArtifact, consoleOutputArea, ownerStage);
    }

    private void launchNesRom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        try {
            File mesenExe = EmulatorLauncher.resolveBundledMesenExecutable();
            if (mesenExe == null) {
                terminalController.appendConsoleOutput(
                    "No se encontró Mesen en libs/emulators/mesen/Mesen.exe.",
                    consoleOutputArea
                );
                return;
            }

            UserActionMonitor.emulatorLaunched(EMULATOR_MESEN, romFile.getName());
            EmulatorLauncher.LaunchResult launch = EmulatorLauncher.launchMesen(mesenExe, romFile, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning(EMULATOR_MESEN);
            monitorEmulatorStartup(EMULATOR_MESEN, launch.process, ownerStage, consoleOutputArea);
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando Mesen con: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError(EMULATOR_MESEN + ": " + ex.getMessage());
            terminalController.appendConsoleOutput(
                "Error al lanzar Mesen: " + ex.getMessage(),
                consoleOutputArea
            );
        }
    }

    private void launchCpc(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        File cpcArtifact = resolveCpcArtifact(sourceFile, projectRootDir);

        if (cpcArtifact == null) {
            File cpcContainer = buildOutputFile(sourceFile, ".cpc", projectRootDir);
            terminalController.appendConsoleOutput(
                "No se encontró un archivo compatible para Caprice32 (.dsk, .cdt, .voc, .cpr, .sna o .zip).",
                consoleOutputArea
            );
            if (cpcContainer.exists()) {
                terminalController.appendConsoleOutput(
                    "Se generó un .cpc, pero Caprice32 necesita .dsk/.cdt/.cpr. Activa -create-app y usa -subtype=dsk o -subtype=cpr.",
                    consoleOutputArea
                );
            }
            terminalController.appendConsoleOutput(
                "Compila el proyecto para CPC antes de ejecutar en el emulador.",
                consoleOutputArea
            );
            return;
        }

        launchCap32Rom(cpcArtifact, consoleOutputArea, ownerStage);
    }

    private void launchCap32Rom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage) {
        try {
            File cap32Exe = resolveCap32Executable();
            if (cap32Exe == null) {
                terminalController.appendConsoleOutput(
                    "No se encontró Caprice32 en libs/emulators/cap32/cap32.exe.",
                    consoleOutputArea
                );
                return;
            }

            String mediaName = romFile != null ? romFile.getName() : "";
            String extension = getExtension(romFile);
            File loadableMedium = romFile;

            if ("edsk".equals(extension)) {
                terminalController.appendConsoleOutput(
                    "Aviso: Caprice32 no admite .edsk. Se ha abierto el emulador; convierte el disco a .dsk para cargarlo automáticamente.",
                    consoleOutputArea
                );
            }

            if (!CPC_CAP32_EXTENSIONS.contains(extension)) {
                terminalController.appendConsoleOutput(
                    "Aviso: Caprice32 no admite ." + extension + " como medio (admitidos: .dsk, .cdt, .voc, .cpr, .sna, .zip). Se abrirá el emulador sin cargar el archivo.",
                    consoleOutputArea
                );
                loadableMedium = null;
            }

            UserActionMonitor.emulatorLaunched(EMULATOR_CAP32, mediaName);
            List<String> cap32Args = new ArrayList<>();
            String autoCommand = resolveCap32AutoCommand(loadableMedium);
            if (autoCommand != null) {
                cap32Args.add("--autocmd=" + autoCommand);
                terminalController.appendConsoleOutput(
                    "Caprice32 arrancará con: " + autoCommand,
                    consoleOutputArea
                );
            }
            EmulatorLauncher.LaunchResult launch = EmulatorLauncher.launchCap32(cap32Exe, loadableMedium, consoleOutputArea, cap32Args);
            UserActionMonitor.emulatorRunning(EMULATOR_CAP32);
            monitorEmulatorStartup(EMULATOR_CAP32, launch.process, ownerStage, consoleOutputArea);
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando Caprice32 con: " + mediaName,
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError(EMULATOR_CAP32 + ": " + ex.getMessage());
            terminalController.appendConsoleOutput(
                "Error al lanzar Caprice32: " + ex.getMessage(),
                consoleOutputArea
            );
        }
    }

    private String getEmulatorForCompiler(String compiler, String z88dkProfile, ConfigModel configModel) {
        if (isSpectrumCompiler(compiler)) {
            String z = normalizeZ88dkProfile(z88dkProfile);
            if (Z88DK_PROFILE_CPC.equalsIgnoreCase(z)) {
                return EMULATOR_CAP32;
            }
            if (Z88DK_PROFILE_MSX.equalsIgnoreCase(z)) {
                return EMULATOR_OPENMSX;
            }
            if (Z88DK_PROFILE_SMS.equalsIgnoreCase(z) || Z88DK_PROFILE_GAME_GEAR.equalsIgnoreCase(z)) {
                return EMULATOR_EMULICIOUS;
            }
            return EMULATOR_JSPECCY;
        }
        if (COMPILER_CC65.equalsIgnoreCase(compiler)) {
            String target = configModel != null
                ? normalizeCc65Target(configModel.getConfigProperty("cc65_target", CC65_TARGET_ATARI))
                : CC65_TARGET_ATARI;
            if (CC65_TARGET_C64.equals(target)) return EMULATOR_VICE;
            if (CC65_TARGET_NES.equals(target)) return EMULATOR_MESEN;
            return EMULATOR_ALTIRRA;
        }
        return EMULATOR_EMULICIOUS;
    }

    private String normalizeCc65Target(String target) {
        if (target != null && target.trim().equalsIgnoreCase(CC65_TARGET_C64)) return CC65_TARGET_C64;
        if (target != null && target.trim().equalsIgnoreCase(CC65_TARGET_NES)) return CC65_TARGET_NES;
        return CC65_TARGET_ATARI;
    }

    private String cc65TargetActual(String normalizedTarget) {
        if (CC65_TARGET_NES.equals(normalizedTarget)) return "nes";
        if (CC65_TARGET_C64.equals(normalizedTarget)) return "c64";
        return "atari";
    }

    private String cc65ArtifactExtension(String normalizedTarget) {
        if (CC65_TARGET_NES.equals(normalizedTarget)) return ".nes";
        if (CC65_TARGET_C64.equals(normalizedTarget)) return ".prg";
        return ".xex";
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
        String profileBinKey = null;
        if (Z88DK_PROFILE_CPC.equalsIgnoreCase(profile)) {
            profileBinKey = "cpc_bin";
        } else if (Z88DK_PROFILE_SMS.equalsIgnoreCase(profile)) {
            profileBinKey = "sms_bin";
        } else if (Z88DK_PROFILE_GAME_GEAR.equalsIgnoreCase(profile)) {
            profileBinKey = "gg_bin";
        } else if (Z88DK_PROFILE_MSX.equalsIgnoreCase(profile)) {
            profileBinKey = "msx_bin";
        }
        if (profileBinKey != null) {
            String profileBin = configModel.getConfigProperty(profileBinKey, "");
            if (profileBin != null && !profileBin.isBlank()) {
                return profileBin;
            }
        }
        return configModel.getConfigProperty("spectrum_bin", "");
    }


    private File resolveBundledJar(String prefix) {
        File libsDir = new File(System.getProperty("user.dir"), "libs/emulators");

        if (!libsDir.exists() || !libsDir.isDirectory()) return null;

        File[] matches = findJarsStartingWith(libsDir, prefix);
        if (matches == null || matches.length == 0) return null;

        Arrays.sort(matches);
        return matches[0];
    }

    private File[] findJarsStartingWith(File dir, String prefix) {
        File[] direct = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.startsWith(prefix.toLowerCase()) && lower.endsWith(".jar");
        });

        File[] nested = dir.listFiles(File::isDirectory);
        if (nested != null && nested.length > 0) {
            List<File> collected = new ArrayList<>();
            if (direct != null) Collections.addAll(collected, direct);

            String prefixLower = prefix.toLowerCase();
            for (File subDir : nested) {
                if (!subDir.getName().toLowerCase().startsWith(prefixLower)) continue;

                File[] inSubDir = subDir.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return lower.startsWith(prefixLower) && lower.endsWith(".jar");
                });

                if (inSubDir != null) Collections.addAll(collected, inSubDir);
            }

            return collected.toArray(new File[0]);
        }

        return direct;
    }

    private File resolveBundledGbdkBin() {
        File binDir = new File(System.getProperty("user.dir"), "libs/devkits/gbdk/bin");
        if (!binDir.isDirectory()) return null;
        String lccName = isWindows() ? "lcc.exe" : "lcc";
        File lcc = new File(binDir, lccName);
        return lcc.isFile() ? binDir : null;
    }

    private File resolveBundledZ88dkBin() {
        File binDir = new File(System.getProperty("user.dir"), "libs/devkits/z88dk/bin");
        if (!binDir.isDirectory()) return null;
        String zccName = isWindows() ? "zcc.exe" : "zcc";
        return new File(binDir, zccName).isFile() ? binDir : null;
    }

    private File resolveBundledCc65Bin() {
        File binDir = new File(System.getProperty("user.dir"), "libs/devkits/cc65/bin");
        if (!binDir.isDirectory()) return null;
        String cl65Name = isWindows() ? "cl65.exe" : "cl65";
        return new File(binDir, cl65Name).isFile() ? binDir : null;
    }

    private boolean hasZccExecutable(String binDir) {
        String clean = normalizeConfiguredPath(binDir);
        if (clean.isBlank()) return false;

        File base = new File(clean);
        File[] candidates = {
            new File(base, "zcc.exe"),
            new File(base, "zcc"),
            new File(base, "bin" + File.separator + "zcc.exe"),
            new File(base, "bin" + File.separator + "zcc")
        };

        for (File candidate : candidates) {
            if (candidate.isFile()) return true;
        }

        return false;
    }

    private String resolveEffectiveZ88dkBin(ConfigModel configModel, String selectedCompiler) {
        String configured = resolveZ88dkBinForProfile(configModel, selectedCompiler);
        if (hasZccExecutable(configured)) return normalizeConfiguredPath(configured);

        File bundled = resolveBundledZ88dkBin();
        if (bundled != null) return bundled.getAbsolutePath();

        return normalizeConfiguredPath(configured);
    }

    private File resolveSpectrumArtifact(File sourceFile, File projectRootDir) {
        for (String extension : new String[] { ".tap", ".tzx", ".sna", ".z80" }) {
            File artifact = buildOutputFile(sourceFile, extension, projectRootDir);
            if (artifact.exists()) return artifact;
        }

        return null;
    }

    /**
     * Caprice32 identifica el medio por extension y solo admite .dsk, .cdt, .voc,
     * .cpr, .sna y .zip. La lista se ordena poniendo primero el cartucho .cpr
     * (que es lo que produce z88dk con -subtype=cpr y lo que mejor reproduce un
     * programa ya enlazado) y despues el resto.
     */
    private File resolveCpcArtifact(File sourceFile, File projectRootDir) {
        for (String extension : new String[] { ".cpr", ".dsk", ".cdt", ".voc", ".sna" }) {
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

    private File resolveCap32Executable() {
        return EmulatorLauncher.resolveBundledCap32Executable();
    }

    /**
     * Caprice32 carga el medio por extension y ejecuta el comando de arranque con
     * {@code -a/--autocmd}. El comando se deduce del contenido del disco leyendo su
     * directorio, pero sin salto de linea final: cap32 lo pasa a la BASIC del CPC,
     * no a un emulador de consola.
     */
    private String resolveCap32AutoCommand(File romFile) {
        String ext = getExtension(romFile);
        if (!"dsk".equals(ext) && !"edsk".equals(ext)) return null;

        try {
            byte[] data = Files.readAllBytes(romFile.toPath());
            String command = resolveCpcDiskAutoCommand(romFile, data);
            if (command == null) return null;
            String cleaned = command.replace("\r", "").replace("\n", "").trim();
            return cleaned.isEmpty() || "CAT".equalsIgnoreCase(cleaned) ? null : cleaned;
        } catch (Exception ex) {
            return null;
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

    /**
     * Devuelve los artefactos compilados (ROMs/ejecutables) encontrados en la
     * carpeta out/ del proyecto, ordenados por fecha de modificación (más reciente
     * primero). Se usa para publicar releases en GitHub.
     */
    public List<File> resolveBuildArtifacts(File projectRootDir) {
        File outputDir = resolveOutputDirectory(null, projectRootDir);
        if (outputDir == null || !outputDir.isDirectory()) {
            return List.of();
        }

        List<File> artifacts = new ArrayList<>();
        for (File file : outputDir.listFiles()) {
            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                int dot = name.lastIndexOf('.');
                if (dot >= 0) {
                    String ext = name.substring(dot + 1);
                    if (GAMEBOY_ROM_EXTENSIONS.contains(ext)
                        || SPECTRUM_ROM_EXTENSIONS.contains(ext)
                        || CPC_ROM_EXTENSIONS.contains(ext)
                        || ATARI_ROM_EXTENSIONS.contains(ext)
                        || C64_ROM_EXTENSIONS.contains(ext)
                        || "rom".equals(ext)) {
                        artifacts.add(file);
                    }
                }
            }
        }

        artifacts.sort(Comparator.comparingLong(File::lastModified).reversed());
        return artifacts;
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

        if (EMULATOR_CPCBOX_WEB_LEGACY.equalsIgnoreCase(selectedEmulator)) {
            selectedEmulator = EMULATOR_CAP32;
        }

        String z88dkProfileForRun = Z88DK_PROFILE_SPECTRUM;
        if (configModel != null && isSpectrumCompiler(configModel.getConfigProperty("compilador_seleccionado", ""))) {
            z88dkProfileForRun = normalizeZ88dkProfile(configModel.getConfigProperty("z88dk_profile", Z88DK_PROFILE_SPECTRUM));
        }

        if (EMULATOR_EMULICIOUS.equalsIgnoreCase(selectedEmulator)) {
            if (Z88DK_PROFILE_SMS.equals(z88dkProfileForRun) || Z88DK_PROFILE_GAME_GEAR.equals(z88dkProfileForRun)) {
                launchSms(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            } else {
                launchCoffeeGb(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            }
            return;
        }

        if (EMULATOR_OPENMSX.equalsIgnoreCase(selectedEmulator)) {
            launchMsx(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            return;
        }

        if (EMULATOR_MESEN.equalsIgnoreCase(selectedEmulator)) {
            launchNes(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            return;
        }

        if (EMULATOR_JSPECCY.equalsIgnoreCase(selectedEmulator)) {
            launchJSpeccy(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            return;
        }

        if (EMULATOR_CAP32.equalsIgnoreCase(selectedEmulator)) {
            launchCpc(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            return;
        }

        boolean compilerIsCc65 = configModel != null && "cc65".equalsIgnoreCase(configModel.getConfigProperty("compilador_seleccionado", ""));
        String forcedCc65Target = configModel != null
            ? normalizeCc65Target(configModel.getConfigProperty("cc65_target", CC65_TARGET_ATARI))
            : CC65_TARGET_ATARI;

        if (EMULATOR_VICE.equalsIgnoreCase(selectedEmulator)
                || (compilerIsCc65 && CC65_TARGET_C64.equals(forcedCc65Target))) {
            launchVice(sourceFile, projectRootDir, consoleOutputArea, ownerStage, configModel);
            return;
        }

        if (EMULATOR_ALTIRRA.equalsIgnoreCase(selectedEmulator)
                || (compilerIsCc65 && CC65_TARGET_ATARI.equals(forcedCc65Target))) {
            launchAltirra(sourceFile, projectRootDir, consoleOutputArea, ownerStage, configModel);
            return;
        }

        if (compilerIsCc65 && CC65_TARGET_NES.equals(forcedCc65Target)) {
            launchNes(sourceFile, projectRootDir, consoleOutputArea, ownerStage);
            return;
        }

        terminalController.appendConsoleOutput(
            "Emulador no soportado o no configurado: " + selectedEmulator,
            consoleOutputArea
        );
    }

    private File resolveAtariArtifact(File sourceFile, File projectRootDir) {
        return resolveCc65Artifact(sourceFile, projectRootDir, CC65_TARGET_ATARI);
    }

    private File resolveCc65Artifact(File sourceFile, File projectRootDir, String cc65Target) {
        List<String> extensions;
        if (CC65_TARGET_NES.equals(cc65Target)) {
            extensions = Arrays.asList(".nes");
        } else if (CC65_TARGET_C64.equals(cc65Target)) {
            extensions = Arrays.asList(".prg", ".d64");
        } else {
            extensions = Arrays.asList(".xex", ".com", ".atr", ".exe");
        }

        for (String extension : extensions) {
            File artifact = buildOutputFile(sourceFile, extension, projectRootDir);
            if (artifact.exists()) return artifact;
        }

        File outputDir = resolveOutputDirectory(sourceFile, projectRootDir);
        File fallbackArtifact = findNewestArtifact(outputDir, extensions.stream().map(ext -> ext.replaceFirst("^\\.", "")).toList());
        if (fallbackArtifact != null) {
            return fallbackArtifact;
        }

        return null;
    }

    private File resolveC64Artifact(File sourceFile, File projectRootDir) {
        return resolveCc65Artifact(sourceFile, projectRootDir, CC65_TARGET_C64);
    }

    private File resolveNesArtifact(File sourceFile, File projectRootDir) {
        return resolveCc65Artifact(sourceFile, projectRootDir, CC65_TARGET_NES);
    }

    /** Localiza el artefacto SMS/Game Gear (.sms/.gg) generado por zcc en la carpeta de salida. */
    private File resolveSmsArtifact(File sourceFile, File projectRootDir) {
        for (String extension : new String[] { ".sms", ".gg" }) {
            File artifact = buildOutputFile(sourceFile, extension, projectRootDir);
            if (artifact.exists()) return artifact;
        }

        File outputDir = resolveOutputDirectory(sourceFile, projectRootDir);
        File fallbackArtifact = findNewestArtifact(outputDir, SMS_ROM_EXTENSIONS);
        if (fallbackArtifact != null) {
            return fallbackArtifact;
        }

        return null;
    }

    /** Localiza el artefacto MSX (.rom/.bin/.dsk) generado por zcc en la carpeta de salida. */
    private File resolveMsxArtifact(File sourceFile, File projectRootDir) {
        for (String extension : new String[] { ".rom", ".dsk" }) {
            File artifact = buildOutputFile(sourceFile, extension, projectRootDir);
            if (artifact.exists()) return artifact;
        }

        File outputDir = resolveOutputDirectory(sourceFile, projectRootDir);
        File fallbackArtifact = findNewestArtifact(outputDir, MSX_ROM_EXTENSIONS);
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
                File bundledDir = new File(System.getProperty("user.dir"), "libs/emulators/Altirra");
                File bundledExe = new File(bundledDir, isWindows() ? "Altirra64.exe" : "Altirra");
                if (!bundledExe.exists()) bundledExe = new File(bundledDir, "Altirra.exe");
                if (bundledExe.exists()) altirraExe = bundledExe;
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

    private void launchVice(File sourceFile, File projectRootDir, StyleClassedTextArea consoleOutputArea, Stage ownerStage, ConfigModel configModel) {
        File c64Artifact = resolveC64Artifact(sourceFile, projectRootDir);
        if (c64Artifact == null || !c64Artifact.exists()) {
            terminalController.appendConsoleOutput("No se encontró el archivo ejecutable para C64 (.prg/.d64). Compila el proyecto antes de ejecutar.", consoleOutputArea);
            return;
        }
        launchViceRom(c64Artifact, consoleOutputArea, ownerStage, configModel);
    }

    private void launchViceRom(File romFile, StyleClassedTextArea consoleOutputArea, Stage ownerStage, ConfigModel configModel) {
        try {
            String vicePath = configModel != null ? configModel.getConfigProperty("vice_bin", "") : "";
            File viceExe = vicePath.isBlank() ? null : new File(vicePath);
            if (viceExe == null || (viceExe.isDirectory() ? !new File(viceExe, isWindows() ? "x64sc.exe" : "x64sc").exists() : !viceExe.exists())) {
                viceExe = new File(vicePath, isWindows() ? "x64sc.exe" : "x64sc");
            }
            if (viceExe == null || !viceExe.exists()) {
                File bundledDir = new File(System.getProperty("user.dir"), "libs/emulators");
                File viceRoot = findViceRoot(bundledDir);
                if (viceRoot != null) {
                    File bundledExe = new File(viceRoot, isWindows() ? "x64sc.exe" : "x64sc");
                    if (!bundledExe.exists()) bundledExe = new File(viceRoot, "x64.exe");
                    if (bundledExe.exists()) viceExe = bundledExe;
                }
            }

            if (viceExe == null || !viceExe.exists()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Error al ejecutar emulador VICE");
                alert.setHeaderText("No se encontró el ejecutable de VICE");
                alert.setContentText("Por favor, configura la ruta de VICE (x64sc.exe) en las opciones de configuración.");
                alert.showAndWait();
                terminalController.appendConsoleOutput("ERROR: No se encontró el ejecutable de VICE en: " + vicePath, consoleOutputArea);
                return;
            }

            UserActionMonitor.emulatorLaunched("VICE", romFile.getName());
            EmulatorLauncher.launchVice(viceExe, romFile, consoleOutputArea, null);
            UserActionMonitor.emulatorRunning("VICE");
            handoffFocusToEmulator(ownerStage, consoleOutputArea);
            terminalController.appendConsoleOutput(
                "Lanzando VICE con archivo: " + romFile.getName(),
                consoleOutputArea
            );
        } catch (Exception ex) {
            UserActionMonitor.executionError("VICE: " + ex.getMessage());
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error al ejecutar VICE");
            alert.setHeaderText("No se pudo iniciar VICE");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
            terminalController.appendConsoleOutput("Error al lanzar VICE: " + ex.getMessage(), consoleOutputArea);
        }
    }

    private File findViceRoot(File parentDir) {
        File[] children = parentDir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String name = child.getName().toLowerCase();
            if (name.contains("vice")) {
                File candidate = new File(child, isWindows() ? "x64sc.exe" : "x64sc");
                if (candidate.exists()) return child;
                File sub = findViceRoot(child);
                if (sub != null) return sub;
            }
        }
        return null;
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
        if (Z88DK_PROFILE_CPC.equalsIgnoreCase(profile)) return Z88DK_PROFILE_CPC;
        if (Z88DK_PROFILE_SMS.equalsIgnoreCase(profile)) return Z88DK_PROFILE_SMS;
        if (Z88DK_PROFILE_GAME_GEAR.equalsIgnoreCase(profile)) return Z88DK_PROFILE_GAME_GEAR;
        if (Z88DK_PROFILE_MSX.equalsIgnoreCase(profile)) return Z88DK_PROFILE_MSX;
        return Z88DK_PROFILE_SPECTRUM;
    }

    private String profileLabel(String z88dkProfile) {
        String z = normalizeZ88dkProfile(z88dkProfile);
        if (Z88DK_PROFILE_CPC.equals(z)) return "CPC";
        if (Z88DK_PROFILE_SMS.equals(z)) return "SMS";
        if (Z88DK_PROFILE_GAME_GEAR.equals(z)) return "Game Gear";
        if (Z88DK_PROFILE_MSX.equals(z)) return "MSX";
        return "Spectrum";
    }

    /**
     * Devuelve la clave de configuracion del perfil z88dk para un sufijo dado.
     * Los perfiles usan prefijo propio (z88dk_cpc_*, z88dk_sms_*, ...) salvo el de
     * Spectrum, que es el legado sin prefijo (z88dk_target, z88dk_defines, ...).
     */
    private String z88dkProfileKey(String z88dkProfile, String suffix) {
        String z = normalizeZ88dkProfile(z88dkProfile);
        if (Z88DK_PROFILE_SPECTRUM.equals(z)) {
            return "z88dk_" + suffix;
        }
        return "z88dk_" + z + "_" + suffix;
    }

    private String z88dkDefaultTarget(String z88dkProfile) {
        String z = normalizeZ88dkProfile(z88dkProfile);
        if (Z88DK_PROFILE_CPC.equals(z)) return "+cpc";
        if (Z88DK_PROFILE_SMS.equals(z)) return "+sms";
        if (Z88DK_PROFILE_GAME_GEAR.equals(z)) return Z88DK_TARGET_SMS;
        if (Z88DK_PROFILE_MSX.equals(z)) return "+msx";
        return "+zx";
    }

    /**
     * Fuentes de apoyo que hay que compilar junto al proyecto porque la libreria
     * de C del toolchain va incompleta.
     *
     * Para CPC, la copia integrada de cpc_clib.lib no trae el modulo
     * fcntl/writebyte.c que usa stdio/fputc_callee.c, de modo que cualquier
     * llamada a printf deja el enlace con "undefined symbol: writebyte". El propio
     * z88dk incluye ese fuente en libsrc/target/cpc/fcntl/writebyte.c y
     * compilarlo junto al proyecto lo resuelve. No hay riesgo de simbolo
     * duplicado: el enlazador solo extrae un miembro del archivo si el simbolo
     * sigue sin definir cuando llega a el.
     *
     * Si el fuente no esta disponible se omite y el build fallara con el error
     * original de la libreria.
     */
    private List<String> z88dkSupportSources(String z88dkProfile, String compilerExecutable) {
        if (!Z88DK_PROFILE_CPC.equals(normalizeZ88dkProfile(z88dkProfile))) return Collections.emptyList();

        File support = findZ88dkSupportSource(compilerExecutable, Z88DK_CPC_SUPPORT_SOURCE);

        return support != null ? List.of(support.getAbsolutePath()) : Collections.emptyList();
    }

    /** Busca un fuente del toolchain subiendo hasta tres niveles desde zcc (bin -> raiz). */
    private File findZ88dkSupportSource(String compilerExecutable, String relativePath) {
        String configured = normalizeConfiguredPath(compilerExecutable);
        if (configured.isEmpty()) return null;

        File dir = new File(configured).getParentFile();

        for (int level = 0; level < 3 && dir != null; level++) {
            File candidate = new File(dir, relativePath);
            if (candidate.isFile()) return candidate;
            dir = dir.getParentFile();
        }

        return null;
    }

    private String z88dkDefaultClib(String z88dkProfile) {
        return Z88DK_PROFILE_SPECTRUM.equals(normalizeZ88dkProfile(z88dkProfile))
            ? ConfigModel.Z88DK_CLIB_NEW
            : ConfigModel.Z88DK_CLIB_DEFAULT;
    }

    /** Subtype por defecto que obliga zcc para generar el artefacto adecuado. */
    private String z88dkDefaultSubtype(String z88dkProfile) {
        String z = normalizeZ88dkProfile(z88dkProfile);
        if (Z88DK_PROFILE_CPC.equals(z)) return "dsk";
        if (Z88DK_PROFILE_MSX.equals(z)) return "rom";
        if (Z88DK_PROFILE_GAME_GEAR.equals(z)) return "gamegear";
        return null;
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
