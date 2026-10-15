package com.retroeditor.service.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Descubre y consulta el ejecutable de opencode.
 *
 * Detalle de Windows que condiciona toda la clase: {@code new ProcessBuilder("opencode")}
 * falla con {@code CreateProcess error=2} porque Java no resuelve el shim sin extension.
 * npm instala un {@code opencode.cmd} y Scoop/choco usan {@code opencode.exe}, asi que
 * siempre se pasa la ruta concreta del fichero, nunca el nombre desnudo.
 *
 * Esta clase no lanza procesos largos ni habla con el servidor: solo localiza el binario
 * y lee su version. El arranque de {@code opencode serve} es del proveedor.
 */
public class OpenCodeRuntime {

    public static final String EXECUTABLE_NAME = "opencode";

    /** Orden de preferencia: el nativo antes que el shim de npm. */
    static final List<String> CANDIDATE_SUFFIXES = List.of(".exe", ".cmd", ".bat", "");

    private final Path configuredExecutable;
    private final List<Path> searchPath;
    private final Duration versionTimeout;

    public OpenCodeRuntime(AiSettings settings) {
        this(settings == null ? null : settings.cliPath(),
             pathFromEnvironment(),
             Duration.ofSeconds(5));
    }

    /** Visible para tests: permite simular un PATH concreto sin tocar el entorno. */
    OpenCodeRuntime(String cliPath, List<Path> searchPath, Duration versionTimeout) {
        this.configuredExecutable = cliPath != null && !cliPath.isBlank() ? Path.of(cliPath.trim()) : null;
        this.searchPath = searchPath != null ? List.copyOf(searchPath) : List.of();
        this.versionTimeout = versionTimeout != null ? versionTimeout : Duration.ofSeconds(5);
    }

    /** Localiza el ejecutable. Vacio si opencode no esta instalado. */
    public Optional<Path> resolveExecutable() {
        if (configuredExecutable != null) {
            Path found = probeDirectory(configuredExecutable.toAbsolutePath().normalize());
            return Optional.ofNullable(found);
        }
        for (Path directory : searchPath) {
            Path found = probeDirectory(directory);
            if (found != null) {
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }

    public boolean isAvailable() {
        return resolveExecutable().isPresent();
    }

    /** Explicacion para la UI cuando no esta disponible. Sin claves ni rutas de usuario. */
    public String unavailableReason() {
        if (isAvailable()) return null;
        if (!searchPath.isEmpty() && configuredExecutable == null) {
            return "opencode no esta en el PATH";
        }
        if (configuredExecutable != null) {
            return "no existe el ejecutable configurado: " + configuredExecutable;
        }
        return "opencode no esta instalado";
    }

    /**
     * Como instalarlo si el usuario decide instalarlo el mismo.
     * Los textos definitivos salen de los bundles de i18n; esto es solo el fallback.
     */
    public static List<String> installationHints() {
        return List.of(
                "npm install -g opencode-ai",
                "scoop install opencode",
                "winget install SST.opencode");
    }

    /** Ejecuta {@code --version}. Vacio si no se puede leer (no instalado o timeout). */
    public Optional<String> version() {
        Optional<Path> executable = resolveExecutable();
        if (executable.isEmpty()) {
            return Optional.empty();
        }
        Process started = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(executable.get().toString(), "--version");
            builder.redirectErrorStream(true);
            started = builder.start();
            Process process = started;
            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> drain(process, output), "opencode-version-reader");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(versionTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            reader.join(Math.min(versionTimeout.toMillis(), 500));
            return parseVersion(output.toString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        } finally {
            if (started != null && started.isAlive()) {
                started.destroyForcibly();
            }
        }
    }

    /** Devuelve la primera linea con contenido: "1.18.34" o el nombre corto. */
    private static Optional<String> parseVersion(String raw) {
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return Optional.of(trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed);
            }
        }
        return Optional.empty();
    }

    private static void drain(Process process, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // el proceso ha muerto o el stream se ha cerrado: nada que hacer
        }
    }

    /** Si {@code candidate} es un directorio, busca dentro; si es un fichero, lo valida. */
    private Path probeDirectory(Path candidate) {
        if (candidate == null) return null;
        Path directory = Files.isDirectory(candidate) ? candidate : candidate.getParent();
        if (directory == null || !Files.isDirectory(directory)) return null;
        if (!Files.isDirectory(candidate) && isExecutableFile(candidate)) {
            return candidate.toAbsolutePath().normalize();
        }
        for (String suffix : CANDIDATE_SUFFIXES) {
            Path file = directory.resolve(EXECUTABLE_NAME + suffix);
            if (isExecutableFile(file)) {
                return file.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private static boolean isExecutableFile(Path file) {
        return Files.isRegularFile(file) && Files.isExecutable(file);
    }

    private static List<Path> pathFromEnvironment() {
        String raw = System.getenv("PATH");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Path> entries = new ArrayList<>();
        for (String entry : raw.split(System.getProperty("path.separator", ";"))) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(Path.of(trimmed));
            }
        }
        return entries;
    }
}