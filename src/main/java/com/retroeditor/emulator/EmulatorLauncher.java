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
import javafx.scene.control.TextArea;

/**
 * Clase utilitaria para lanzar emuladores externos (JSpeccy, coffee-gb, etc.) como procesos externos
 */
public class EmulatorLauncher {

    /** Resultado del lanzamiento del emulador */
    public static class LaunchResult {
        public final Process process;
        
        public LaunchResult(Process p) { this.process = p; }
    }

    /**
     * Lanza JSpeccy con la ROM especificada y transmite su salida a un área de texto JavaFX.
     * @param jspeccyPath Ruta al ejecutable o JAR de JSpe
     * @param romFile Archivo ROM a cargar en el emulador.
     * @param console Área de texto JavaFX donde se transmitirá la salida del emulador (puede ser null).
     * @param extraArgs Argumentos adicionales para pasar a JSpeccy (puede ser null).
     * @return Resultado del lanzamiento que incluye el proceso.
     * @throws IOException Si ocurre un error al iniciar el emulador.
     */
    public static LaunchResult launchJSpeccy(File jspeccyPath, File romFile, TextArea console, List<String> extraArgs) throws IOException {
        if (jspeccyPath == null || !jspeccyPath.exists()) throw new IOException("Ruta de JSpeccy no válida: " + jspeccyPath);
        if (romFile == null     || !romFile.exists())     throw new IOException("ROM no encontrada: " + romFile);

        List<String> cmd = new ArrayList<>();
        String nameLower = jspeccyPath.getName().toLowerCase();
        
        if (nameLower.endsWith(".jar")) {
            cmd.add("java");
            cmd.add("-jar");
            cmd.add(jspeccyPath.getAbsolutePath());
            cmd.add(romFile.getAbsolutePath());
        } else {
            cmd.add(jspeccyPath.getAbsolutePath());
            cmd.add(romFile.getAbsolutePath());
        }

        if (extraArgs != null) cmd.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc      = pb.start();

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
