package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.retroeditor.util.CompilerArgs;

/**
 * Construye la línea de comandos de GCC para compilar C normal (nativo) y recopila
 * los fuentes .c del proyecto, de modo que todos se enlacen juntos (como CMake/CLion).
 *
 * <p>Esta lógica está aislada del controlador para poder probarla en unit tests.</p>
 */
public class NativeGccBuildService {

    private static final List<String> DEFAULT_GCC_FLAGS = List.of("-Wall", "-Wextra", "-g");

    private final CompilerArgs compilerArgs = new CompilerArgs();

    /** Un paso de compilación: un fuente .c que necesita recompilarse a .o. */
    public record GccCompileStep(File source, File objectFile, List<String> command) {}

    /** El paso final de enlazado de todos los .o al binario. */
    public record GccLinkStep(File outputFile, List<String> command) {}

    /** Plan completo de build incremental: pasos de compilación + enlazado. */
    public record GccBuildPlan(List<GccCompileStep> compileSteps, GccLinkStep linkStep) {}

    /**
     * Construye la lista de argumentos de gcc.
     * @param compiler Ruta o nombre del ejecutable de GCC.
     * @param optLevel Nivel de optimización (none/-O0/-O1/-O2/-O3), vacío para omitirlo.
     * @param defines Defines (-D) separados por coma o espacio.
     * @param includes Includes (-I) separados por ; o coma.
     * @param extraArgs Argumentos libres (admite secciones entre comillas).
     * @param outputFile Ruta del binario de salida.
     * @param sources Fuentes .c a compilar y enlazar.
     * @return Lista de argumentos lista para pasar a gcc.
     */
    public List<String> buildCommand(String compiler,
                                     String optLevel,
                                     String defines,
                                     String includes,
                                     String extraArgs,
                                     File outputFile,
                                     List<File> sources) {
        List<String> cmd = new ArrayList<>();
        cmd.add(compiler);

        if (optLevel != null && !optLevel.isBlank() && !optLevel.equalsIgnoreCase("none")) {
            cmd.add(optLevel);
        }

        compilerArgs.addMacroFlags(cmd, defines);
        compilerArgs.addIncludeFlags(cmd, includes);
        compilerArgs.addExtraArgs(cmd, extraArgs);
        addDefaultGccFlags(cmd, extraArgs);

        cmd.add("-o");
        cmd.add(outputFile.getAbsolutePath());

        for (File source : sources) {
            cmd.add(source.getAbsolutePath());
        }
        return cmd;
    }

    /**
     * Añade los flags de aviso y depuración por defecto (-Wall -Wextra -g) solo cuando el
     * usuario no ha configurado argumentos extra propios: si configuró alguno, se respetan
     * sus opciones sin añadir nada.
     */
    private void addDefaultGccFlags(List<String> cmd, String extraArgs) {
        if (extraArgs != null && !extraArgs.isBlank()) {
            return;
        }
        for (String flag : DEFAULT_GCC_FLAGS) {
            if (!cmd.contains(flag)) {
                cmd.add(flag);
            }
        }
    }

    /**
     * Construye el plan de build incremental: un comando de compilación (-c) por cada fuente
     * cuyo .o esté desactualizado o falte, y el enlazado final de todos los .o al binario.
     * El enlazado siempre se incluye, aunque no haya nada que recompilar.
     *
     * @param compiler   Ruta o nombre del ejecutable de GCC.
     * @param optLevel   Nivel de optimización (none/-O0/-O1/-O2/-O3), vacío para omitirlo.
     * @param defines    Defines (-D) separados por coma o espacio.
     * @param includes   Includes (-I) separados por ; o coma.
     * @param extraArgs  Argumentos libres (admite secciones entre comillas).
     * @param outputFile Ruta del binario de salida.
     * @param sources    Fuentes .c a compilar y enlazar.
     * @return Plan con los pasos de compilación pendientes y el paso de enlazado.
     */
    public GccBuildPlan buildPlan(String compiler,
                                  String optLevel,
                                  String defines,
                                  String includes,
                                  String extraArgs,
                                  File outputFile,
                                  List<File> sources) {
        File outputDir = outputFile.getParentFile();
        List<GccCompileStep> compileSteps = new ArrayList<>();
        List<File> objectFiles = new ArrayList<>();

        for (File source : sources) {
            File objectFile = toObjectFile(source, outputDir);
            objectFiles.add(objectFile);
            if (needsRecompilation(source, objectFile)) {
                compileSteps.add(new GccCompileStep(
                    source,
                    objectFile,
                    buildCompileCommand(compiler, optLevel, defines, includes, extraArgs, source, objectFile)
                ));
            }
        }

        return new GccBuildPlan(
            compileSteps,
            new GccLinkStep(outputFile, buildLinkCommand(compiler, extraArgs, outputFile, objectFiles))
        );
    }

    private List<String> buildCompileCommand(String compiler,
                                             String optLevel,
                                             String defines,
                                             String includes,
                                             String extraArgs,
                                             File source,
                                             File objectFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add(compiler);

        if (optLevel != null && !optLevel.isBlank() && !optLevel.equalsIgnoreCase("none")) {
            cmd.add(optLevel);
        }

        compilerArgs.addMacroFlags(cmd, defines);
        compilerArgs.addIncludeFlags(cmd, includes);
        compilerArgs.addExtraArgs(cmd, extraArgs);
        addDefaultGccFlags(cmd, extraArgs);

        cmd.add("-c");
        cmd.add(source.getAbsolutePath());
        cmd.add("-o");
        cmd.add(objectFile.getAbsolutePath());
        return cmd;
    }

    private List<String> buildLinkCommand(String compiler,
                                          String extraArgs,
                                          File outputFile,
                                          List<File> objectFiles) {
        List<String> cmd = new ArrayList<>();
        cmd.add(compiler);
        compilerArgs.addExtraArgs(cmd, extraArgs);
        cmd.add("-o");
        cmd.add(outputFile.getAbsolutePath());
        for (File objectFile : objectFiles) {
            cmd.add(objectFile.getAbsolutePath());
        }
        return cmd;
    }

    private File toObjectFile(File source, File outputDir) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(outputDir, base + ".o");
    }

    private boolean needsRecompilation(File source, File objectFile) {
        if (source == null || objectFile == null) return false;
        if (!objectFile.exists()) return true;
        return source.lastModified() > objectFile.lastModified();
    }

    /**
     * Recopila todos los fuentes .c del proyecto (recursivo) para compilarlos y enlazarlos
     * juntos con GCC, igual que hacen CMake/CLion. Si no hay proyecto abierto, se limita
     * al archivo activo. Se excluyen el directorio de salida y los directorios de build.
     */
    public List<File> collectNativeSources(File projectRootDir, File outputDir, File sourceFile) {
        List<File> sources = new ArrayList<>();
        if (projectRootDir != null && projectRootDir.isDirectory()) {
            Set<String> excludedDirs = new HashSet<>();
            addExcludedDir(excludedDirs, outputDir);
            addExcludedDir(excludedDirs, new File(projectRootDir, "cmake-build-debug"));
            addExcludedDir(excludedDirs, new File(projectRootDir, "cmake-build-release"));
            addExcludedDir(excludedDirs, new File(projectRootDir, "build"));
            addExcludedDir(excludedDirs, new File(projectRootDir, "target"));

            try {
                Files.walk(projectRootDir.toPath())
                    .filter(path -> !isInsideExcludedDir(path.toFile(), excludedDirs))
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".c"))
                    .forEach(path -> sources.add(path.toFile()));
            } catch (Exception ignored) {
            }
        }

        if (sources.isEmpty() && sourceFile != null) {
            sources.add(sourceFile);
        }

        Map<String, File> uniqueByPath = new LinkedHashMap<>();
        for (File source : sources) {
            uniqueByPath.putIfAbsent(absolutePath(source), source);
        }
        List<File> result = new ArrayList<>(uniqueByPath.values());
        result.sort(Comparator.comparing(File::getAbsolutePath));
        return result;
    }

    private void addExcludedDir(Set<String> excludedDirs, File dir) {
        if (dir == null) return;
        try {
            excludedDirs.add(dir.getCanonicalPath());
        } catch (IOException ex) {
            excludedDirs.add(dir.getAbsolutePath());
        }
    }

    private boolean isInsideExcludedDir(File file, Set<String> excludedDirs) {
        if (excludedDirs.isEmpty()) return false;
        try {
            String path = file.getCanonicalPath();
            for (String excluded : excludedDirs) {
                if (path.equals(excluded) || path.startsWith(excluded + File.separator)) {
                    return true;
                }
            }
        } catch (IOException ex) {
            return false;
        }
        return false;
    }

    private String absolutePath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ex) {
            return file.getAbsolutePath();
        }
    }
}
