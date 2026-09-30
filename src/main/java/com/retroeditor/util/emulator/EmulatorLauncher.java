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

    /**
     * Modelo por defecto de JSpeccy: Spectrum 128K. Es el modelo que generan los targets z88dk
     * (+zx) con AY-3-8912, por lo que Samaruc siempre arranca el emulador en 128K ("-m sp128k"),
     * en lugar del 48K que es el valor por defecto de JSpeccy.
     */
    private static final String JSPECCY_DEFAULT_MODEL = "sp128k";

    /** Resultado del lanzamiento del emulador */
    public static class LaunchResult {
        public final Process process;
        
        public LaunchResult(Process p) { this.process = p; }
    }

    /**
     * Lanza JSpeccy usando el classpath de la propia aplicación (sin configuración XML externa).
     * Fuerza el modelo Spectrum 128K por defecto (véase {@link #JSPECCY_DEFAULT_MODEL}).
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
        cmd.add("-m");
        cmd.add(JSPECCY_DEFAULT_MODEL);
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
     * Lanza el emulador Altirra con la ROM/programa Atari especificado.
     * @param altirraExe Ruta al ejecutable de Altirra (altirra.exe / altirra64.exe).
     * @param romFile Archivo binario/xex/atr a cargar.
     * @param console Área de texto de consola.
     * @param extraArgs Argumentos adicionales.
     * @return LaunchResult con el proceso.
     */
    public static LaunchResult launchAltirra(File altirraExe, File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        if (altirraExe == null || !altirraExe.exists()) throw new IOException("Ruta de Altirra no válida: " + altirraExe);
        if (romFile == null || !romFile.exists()) throw new IOException("Archivo Atari no encontrado: " + romFile);

        List<String> cmd = new ArrayList<>();
        cmd.add(altirraExe.getAbsolutePath());
        cmd.add(romFile.getAbsolutePath());

        if (extraArgs != null) cmd.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "altirra-output");
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
     * Lanza VICE (x64sc/x64) con un archivo de Commodore 64 (.prg, .d64, .t64,
     * .tap, .crt). Usa autostart por sistema de ficheros virtuales (mode 0) para
     * los .prg con cabecera correcta.
     * @param viceExe Ruta al ejecutable de VICE (x64sc.exe / x64.exe).
     * @param romFile Archivo a ejecutar.
     * @param console Área de texto de consola.
     * @param extraArgs Argumentos adicionales.
     * @return LaunchResult con el proceso.
     */
    public static LaunchResult launchVice(File viceExe, File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        if (viceExe == null || !viceExe.exists()) throw new IOException("Ruta de VICE no válida: " + viceExe);
        if (romFile == null || !romFile.exists()) throw new IOException("Archivo C64 no encontrado: " + romFile);

        List<String> cmd = new ArrayList<>();
        cmd.add(viceExe.getAbsolutePath());
        cmd.add("-autostartprgmode");
        cmd.add("0");
        cmd.add("-autostart");
        cmd.add(romFile.getAbsolutePath());

        if (extraArgs != null) cmd.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vice-output");
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
     * Localiza el ejecutable de Caprice32 incluido en {@code libs/emulators/cap32}.
     *
     * @return El ejecutable, o null si no esta disponible.
     */
    public static File resolveBundledCap32Executable() {
        return resolveBundledCap32Executable(new File(System.getProperty("user.dir"), "libs/emulators/cap32"));
    }

    static File resolveBundledCap32Executable(File cap32Dir) {
        if (cap32Dir == null || !cap32Dir.isDirectory()) return null;

        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean windows = osName.contains("win");
        File exe = new File(cap32Dir, windows ? "cap32.exe" : "cap32");
        return exe.isFile() ? exe : null;
    }

    /**
     * Construye la linea de comandos de cap32.
     *
     * cap32 parsea las opciones antes de los ficheros de ranura, por lo que
     * {@code extraArgs} debe preceder al medio; si se colocasen despues, cap32
     * las interpretaria como ficheros de ranura adicionales.
     *
     * @param cap32Exe Ruta al ejecutable de Caprice32.
     * @param romFile Medio a cargar; puede ser null.
     * @param extraArgs Opciones adicionales (p.ej. {@code --autocmd=run}); puede ser null.
     * @return Lista con el comando completo.
     */
    public static List<String> buildCap32Command(File cap32Exe, File romFile, List<String> extraArgs) {
        List<String> cmd = new ArrayList<>();
        cmd.add(cap32Exe.getAbsolutePath());

        if (extraArgs != null) {
            for (String arg : extraArgs) {
                if (arg != null && !arg.isBlank()) cmd.add(arg);
            }
        }

        if (romFile != null) cmd.add(romFile.getAbsolutePath());

        return cmd;
    }

    /**
     * Lanza Caprice32 (cap32.exe) con el medio CPC especificado.
     *
     * <p>Caprice32 no resuelve sus recursos (cap32.cfg, carpeta rom/, soundtracks)
     * a partir del directorio de trabajo, sino a partir de la ubicación del propio
     * ejecutable, y ademas escribe alli su configuracion. Por eso el proceso se
     * arranca con el directorio del exe como working directory: si se heredase el
     * del editor, cap32 no encontraria ni la configuracion ni las ROM de sistema.
     *
     * @param cap32Exe Ruta al ejecutable de Caprice32.
     * @param romFile Medio a cargar (.dsk, .cdt, .voc, .cpr, .sna, .zip); puede ser null para abrir el emulador vacio.
     * @param console Área de texto de consola.
     * @param extraArgs Opciones adicionales, sin el ejecutable (p.ej. --autocmd=run).
     * @return LaunchResult con el proceso.
     */
    public static LaunchResult launchCap32(File cap32Exe, File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        if (cap32Exe == null || !cap32Exe.exists()) throw new IOException("Ruta de Caprice32 no válida: " + cap32Exe);
        if (romFile != null && !romFile.exists()) throw new IOException("Medio CPC no encontrado: " + romFile);

        ProcessBuilder pb = new ProcessBuilder(buildCap32Command(cap32Exe, romFile, extraArgs));
        pb.redirectErrorStream(true);
        File home = cap32Exe.getParentFile();
        if (home != null) {
            pb.directory(home);
        }
        Process proc = pb.start();

        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cap32-output");
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
     * Lanza Emulicious (que soporta nativamente Sega Master System y Game Gear además de Game Boy) con la ROM especificada.
     * @param emuliciousJar Ruta al JAR de Emulicious.
     * @param romFile Archivo ROM (.sms o .gg).
     * @param console Área de texto de consola.
     * @param extraArgs Argumentos adicionales.
     * @return LaunchResult con el proceso.
     */
    public static LaunchResult launchSmsEmulator(File emuliciousJar, File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        return launchEmulicious(emuliciousJar, romFile, console, extraArgs);
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


    /**
     * Localiza el ejecutable de openMSX incluido en {@code libs/emulators/openmsx}.
     *
     * @return El ejecutable, o null si no esta disponible.
     */
    public static File resolveBundledOpenMsxExecutable() {
        File openMsxDir = new File(System.getProperty("user.dir"), "libs/emulators/openmsx");
        if (openMsxDir == null || !openMsxDir.isDirectory()) return null;
        File exe = new File(openMsxDir, "openmsx.exe");
        return exe.isFile() ? exe : null;
    }

    /**
     * Localiza el ejecutable de Mesen incluido en {@code libs/emulators/mesen}.
     *
     * @return El ejecutable, o null si no esta disponible.
     */
    public static File resolveBundledMesenExecutable() {
        File mesenDir = new File(System.getProperty("user.dir"), "libs/emulators/mesen");
        if (mesenDir == null || !mesenDir.isDirectory()) return null;
        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean windows = osName.contains("win");
        File exe = new File(mesenDir, windows ? "Mesen.exe" : "Mesen");
        if (!exe.isFile()) exe = new File(mesenDir, "Mesen.exe");
        return exe.isFile() ? exe : null;
    }

    /**
     * Lanza openMSX (openmsx.exe) con la ROM MSX (.rom, .bin) o medio (.dsk) indicado.
     *
     * <p>openMSX recibe el fichero como argumento posicional; para discos flexibles se
     * usa {@code -diska} porque openMSX no arranca un .dsk posicional como medio de arranque.
     *
     * @param openMsxExe Ruta al ejecutable de openMSX.
     * @param romFile ROM/MSX o disco a cargar.
     * @param console Área de texto de consola.
     * @param extraArgs Argumentos adicionales (puede ser null).
     * @return LaunchResult con el proceso.
     */
    public static LaunchResult launchOpenMsx(File openMsxExe, File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        if (openMsxExe == null || !openMsxExe.exists()) throw new IOException("Ruta de openMSX no válida: " + openMsxExe);
        if (romFile == null || !romFile.exists()) throw new IOException("Medio MSX no encontrado: " + romFile);

        List<String> cmd = new ArrayList<>();
        cmd.add(openMsxExe.getAbsolutePath());

        boolean isDisk = romFile.getName().toLowerCase().endsWith(".dsk")
            || romFile.getName().toLowerCase().endsWith(".ds2");
        if (isDisk) {
            cmd.add("-diska");
            cmd.add(romFile.getAbsolutePath());
        } else {
            cmd.add(romFile.getAbsolutePath());
        }

        if (extraArgs != null) cmd.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "openmsx-output");
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
     * Lanza Mesen (Mesen.exe) con la ROM de NES indicada.
     *
     * @param mesenExe Ruta al ejecutable de Mesen.
     * @param romFile Archivo ROM NES (.nes) a cargar.
     * @param console Área de texto de consola.
     * @param extraArgs Argumentos adicionales (puede ser null).
     * @return LaunchResult con el proceso.
     */
    public static LaunchResult launchMesen(File mesenExe, File romFile, StyleClassedTextArea console, List<String> extraArgs) throws IOException {
        if (mesenExe == null || !mesenExe.exists()) throw new IOException("Ruta de Mesen no válida: " + mesenExe);
        if (romFile == null || !romFile.exists()) throw new IOException("ROM NES no encontrada: " + romFile);

        List<String> cmd = new ArrayList<>();
        cmd.add(mesenExe.getAbsolutePath());
        cmd.add(romFile.getAbsolutePath());

        if (extraArgs != null) cmd.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mesen-output");
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
