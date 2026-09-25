package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Servicio de autosave tolerante: vuelca periódicamente el contenido de las
 * pestañas modificadas a una carpeta temporal y permite recuperarlo en el
 * siguiente arranque. Se limpia el snapshot al guardar el archivo.
 */
public class AutoSaveService {

    private static final Path DEFAULT_AUTOSAVE_DIR = Path.of(
            System.getProperty("user.home"), ".samaruc-editor", "autosave");
    private static Path autosaveDir = DEFAULT_AUTOSAVE_DIR;

    /** Permite redirigir la carpeta (útil en tests). */
    public static void setAutosaveDir(Path dir) {
        autosaveDir = dir != null ? dir : DEFAULT_AUTOSAVE_DIR;
    }

    public static Path getAutosaveDir() {
        return autosaveDir;
    }

    public static final class RecoverableFile {
        private final File originalFile;
        private final Path snapshotFile;
        private final long lastModified;

        RecoverableFile(File originalFile, Path snapshotFile, long lastModified) {
            this.originalFile = originalFile;
            this.snapshotFile = snapshotFile;
            this.lastModified = lastModified;
        }

        public File getOriginalFile() { return originalFile; }
        public Path getSnapshotFile() { return snapshotFile; }
        public long getLastModified() { return lastModified; }
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String baseName(File originalFile) {
        return Math.abs(originalFile.getAbsolutePath().hashCode()) + "_" + safeName(originalFile.getName());
    }

    /** Vuelca el contenido de un archivo modificado a la carpeta de autosave. */
    public static void saveSnapshot(File originalFile, String content) {
        if (originalFile == null || content == null) return;
        try {
            Files.createDirectories(autosaveDir);
            String base = baseName(originalFile);
            Path snapshotPath = autosaveDir.resolve(base + ".autosave");
            Path metaPath = autosaveDir.resolve(base + ".path");
            Files.writeString(snapshotPath, content, StandardCharsets.UTF_8);
            Files.writeString(metaPath, originalFile.getAbsolutePath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /** Elimina el snapshot de autosave de un archivo (al guardarlo). */
    public static void clearSnapshot(File originalFile) {
        if (originalFile == null) return;
        String base = baseName(originalFile);
        try {
            Files.deleteIfExists(autosaveDir.resolve(base + ".autosave"));
            Files.deleteIfExists(autosaveDir.resolve(base + ".path"));
        } catch (IOException ignored) {
        }
    }

    /** Elimina todos los snapshots guardados. */
    public static void clearAll() {
        try (Stream<Path> stream = Files.list(autosaveDir)) {
            for (Path p : stream.toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
        }
    }

    /** Devuelve los archivos con snapshot de autosave pendiente, ordenados por fecha. */
    public static List<RecoverableFile> listRecoverable() {
        List<RecoverableFile> result = new ArrayList<>();
        if (!Files.isDirectory(autosaveDir)) return result;
        try (Stream<Path> stream = Files.list(autosaveDir)) {
            List<Path> metas = stream.filter(p -> p.toString().endsWith(".path")).sorted().toList();
            for (Path meta : metas) {
                try {
                    String absPath = Files.readString(meta, StandardCharsets.UTF_8).trim();
                    File original = new File(absPath).getAbsoluteFile();
                    String base = meta.getFileName().toString().replace(".path", "");
                    Path snapshot = autosaveDir.resolve(base + ".autosave");
                    if (Files.isRegularFile(snapshot)) {
                        result.add(new RecoverableFile(original, snapshot, Files.getLastModifiedTime(snapshot).toMillis()));
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return result;
    }

    public static String loadSnapshotContent(Path snapshotFile) {
        if (snapshotFile == null || !Files.isRegularFile(snapshotFile)) return "";
        try {
            return Files.readString(snapshotFile, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
