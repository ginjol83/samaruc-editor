package com.retroeditor.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.retroeditor.util.CompilerArgs;

/**
 * Diagnóstico al guardar (item 14): ejecuta un parseo de sintaxis con GCC
 * ({@code -fsyntax-only}) solo sobre el archivo guardado, sin compilar el
 * proyecto, para subrayar errores en la pestaña sin abrir la consola.
 *
 * <p>Soportado únicamente para GCC: {@code sdcc} ignora
 * {@code -fsyntax-only} y {@code zcc} lo rechaza, por lo que los demás
 * toolchains quedan fuera de alcance de esta comprobación.</p>
 */
public class GccSyntaxCheckService {

    private static final String COMPILER_GCC = "gcc";

    private final CompilerArgs compilerArgs = new CompilerArgs();

    /** True solo si el compilador seleccionado admite {@code -fsyntax-only}. */
    public boolean isApplicable(String selectedCompiler) {
        return COMPILER_GCC.equalsIgnoreCase(selectedCompiler == null ? "" : selectedCompiler.trim());
    }

    /**
     * Construye la línea de comandos de parseo de sintaxis (sin generar salida).
     *
     * @param gccBin   Ruta o nombre del ejecutable GCC (vacío usa {@code gcc}).
     * @param optLevel Nivel de optimización (none/-O0/-O1/-O2/-O3), vacío para omitirlo.
     * @param defines  Defines (-D) separados por coma o espacio.
     * @param includes Includes (-I) separados por ; o coma.
     * @param extraArgs Argumentos libres (admite secciones entre comillas).
     * @param source   Archivo de código a analizar.
     * @return Lista de argumentos lista para pasar a {@code ProcessBuilder}.
     */
    public List<String> buildSyntaxCheckCommand(String gccBin,
                                                 String optLevel,
                                                 String defines,
                                                 String includes,
                                                 String extraArgs,
                                                 File source) {
        List<String> cmd = new ArrayList<>();
        String compiler = gccBin == null || gccBin.isBlank() ? COMPILER_GCC : gccBin.trim();
        cmd.add(compiler);

        if (optLevel != null && !optLevel.isBlank() && !optLevel.equalsIgnoreCase("none")) {
            cmd.add(optLevel);
        }

        compilerArgs.addMacroFlags(cmd, defines);
        compilerArgs.addIncludeFlags(cmd, includes);
        compilerArgs.addExtraArgs(cmd, extraArgs);

        cmd.add("-fsyntax-only");
        cmd.add(source.getAbsolutePath());
        return cmd;
    }

    /**
     * Ejecuta el parseo en la línea de comandos actual pasando cada línea de
     * salida (stdout y stderr combinados) al consumidor dado. Método bloqueante:
     * debe invocarse desde un hilo de fondo.
     *
     * @param command  Comando y argumentos.
     * @param workDir  Directorio de trabajo del proceso (o {@code null}).
     * @param lineConsumer Destino de cada línea de salida del compilador.
     */
    public void run(List<String> command, File workDir, Consumer<String> lineConsumer) {
        if (command == null || command.isEmpty() || lineConsumer == null) return;

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (workDir != null && workDir.isDirectory()) {
            pb.directory(workDir);
        }

        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            }
            process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // GCC no instalado o ruta inválida: sin diagnóstico, no se reporta en consola.
        }
    }
}
