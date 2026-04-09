package com.retroeditor.emulator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * Clase utilitaria para lanzar emuladores externos (Emulicious, JSpeccy, etc.) como procesos externos
 */
public class EmulatorLauncher {

    private static final String JSPECCY_MAIN_CLASS = "gui.JSpeccy";

    /** Resultado del lanzamiento del emulador */
    public static class LaunchResult {
        public final Process process;
        
        public LaunchResult(Process p) { this.process = p; }
    }

    /**
     * Lanza JSpeccy usando el classpath de la propia aplicación (sin configuración XML externa).
     * @param romFile Archivo ROM a cargar en el emulador.
     * @param console Área de texto JavaFX donde se transmitirá la salida del emulador (puede ser null).
     * @param extraArgs Argumentos adicionales para pasar a JSpeccy (puede ser null).
     * @return Resultado del lanzamiento que incluye el proceso.
     * @throws IOException Si ocurre un error al iniciar el emulador.
     */
    public static LaunchResult launchJSpeccy(File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        if (romFile == null || !romFile.exists()) throw new IOException("ROM no encontrada: " + romFile);

        String appClasspath = System.getProperty("java.class.path", "");
        if (appClasspath == null || appClasspath.isBlank()) {
            throw new IOException("Classpath de la aplicación vacío. No se puede iniciar JSpeccy integrado.");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-cp");
        cmd.add(appClasspath);
        cmd.add(JSPECCY_MAIN_CLASS);
        cmd.add(romFile.getAbsolutePath());

        if (extraArgs != null) cmd.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // Salida del emulador en un hilo separado
        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jspeccy-output");
            t.setDaemon(true);
            return t;
        });

        es.submit(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    final String out = line;
                    if (console != null) {
                        Platform.runLater(() -> console.appendText(out + System.lineSeparator()));
                    } else {
                        System.out.println(out);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        es.shutdown();
        
        return new LaunchResult(proc);
    }


    /**
     * Lanza Emulicious con la ROM especificada y transmite su salida a un área de texto JavaFX.
     * @param emuliciousJar Ruta al JAR de Emulicious.
     * @param romFile Archivo ROM a cargar en el emulador.
     * @param console Área de texto JavaFX donde se transmitirá la salida del emulador (puede ser null).
     * @param extraArgs Argumentos adicionales para pasar a Emulicious (puede ser null).
     * @return Resultado del lanzamiento que incluye el proceso.
     * @throws IOException Si ocurre un error al iniciar el emulador.
     */
    public static LaunchResult launchEmulicious(File emuliciousJar, File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        if (emuliciousJar == null || !emuliciousJar.exists()) throw new IOException("Ruta de Emulicious no válida: " + emuliciousJar);
        if (romFile == null || !romFile.exists()) throw new IOException("ROM no encontrada: " + romFile);

        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(emuliciousJar.getAbsolutePath());
        cmd.add(romFile.getAbsolutePath());

        if (extraArgs != null) cmd.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "emulicious-output");
            t.setDaemon(true);
            return t;
        });

        es.submit(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    final String out = line;
                    if (console != null) {
                        Platform.runLater(() -> console.appendText(out + System.lineSeparator()));
                    } else {
                        System.out.println(out);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        es.shutdown();

        return new LaunchResult(proc);
    }

    /** @deprecated Usar {@link #launchEmulicious} en su lugar. */
    @Deprecated
    public static LaunchResult launchCoffeeGb(File jar, File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        return launchEmulicious(jar, romFile, console, extraArgs);
    }

    /**
     * Detiene el emulador lanzado.
     * @param p Proceso del emulador a detener.
     */
    public static void stopEmulator(Process p) {
        if (p == null) return;
        
        p.destroy();
        
        try {
            if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
