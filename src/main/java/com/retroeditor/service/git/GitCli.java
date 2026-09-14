package com.retroeditor.service.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper fino de {@link ProcessBuilder} para invocar el ejecutable {@code git} del sistema.
 *
 * <p>Lee stdout y stderr como UTF-8, impone un timeout (con destrucción forzosa del proceso)
 * y empaqueta el resultado en {@link GitResult}. No lanza excepciones por una salida no cero:
 * quien invoque decide interpretar el código de retorno.</p>
 */
public class GitCli {

    /** Salida de un comando git: stdout, stderr y código de retorno. */
    public record GitResult(List<String> output, List<String> errorOutput, int exitCode) {

        public boolean isSuccess() {
            return exitCode == 0;
        }

        public String outputLines() {
            return String.join(System.lineSeparator(), output);
        }

        public String errorLines() {
            return String.join(System.lineSeparator(), errorOutput);
        }
    }

    private final String gitExecutable;
    private final Duration timeout;

    public GitCli() {
        this("git", Duration.ofSeconds(30));
    }

    GitCli(String gitExecutable, Duration timeout) {
        this.gitExecutable = gitExecutable;
        this.timeout = timeout;
    }

    /**
     * Comprueba si el ejecutable {@code git} está disponible en el PATH.
     */
    public static boolean isGitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").start();
            drain(process.getInputStream());
            drain(process.getErrorStream());
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drain(InputStream stream) {
        try (stream) {
            stream.readAllBytes();
        } catch (IOException e) {
            // ignorar: el comando puede cerrar el flujo antes de tiempo
        }
    }

    /**
     * Ejecuta {@code git} en el directorio de trabajo indicado.
     *
     * @param workDir directorio de trabajo (puede ser null para usar el del proceso)
     * @param args    argumentos del comando git, sin el binario
     * @return resultado del proceso
     * @throws GitOperationsException si el binario no puede ejecutarse o el comando agota el timeout
     */
    public GitResult execute(Path workDir, String... args) throws GitOperationsException {
        List<String> command = new ArrayList<>();
        command.add(gitExecutable);
        command.addAll(List.of(args));

        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            process = pb.start();
        } catch (IOException e) {
            throw new GitOperationsException(
                "No se pudo ejecutar '" + gitExecutable + "'. ¿Está instalado y en el PATH?", e);
        }

        try {
            CompletableFuture<byte[]> stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            CompletableFuture<byte[]> stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitOperationsException("El comando git agotó el tiempo de espera: " + String.join(" ", command));
            }

            byte[] outBytes;
            byte[] errBytes;
            try {
                outBytes = stdoutFuture.get();
                errBytes = stderrFuture.get();
            } catch (Exception e) {
                outBytes = new byte[0];
                errBytes = new byte[0];
            }

            return new GitResult(
                splitLines(new String(outBytes, StandardCharsets.UTF_8)),
                splitLines(new String(errBytes, StandardCharsets.UTF_8)),
                process.exitValue());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new GitOperationsException("Interrumpido mientras se ejecutaba git", e);
        }
    }

    private static byte[] readAll(InputStream stream) {
        try (InputStream s = stream) {
            return s.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** Divide texto por saltos de línea ignorando líneas vacías internas. */
    static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }
}