package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Servicio de Historial Local que almacena snapshots automáticos de archivos editados.
 */
public class LocalHistoryService {
    private static final Path HISTORY_DIR = Path.of(System.getProperty("user.home"), ".samaruc-editor", "history");

    public record HistoryEntry(String timestampLabel, File snapshotFile, LocalDateTime timestamp) {
        @Override
        public String toString() {
            return timestampLabel;
        }
    }

    public static void recordSnapshot(File originalFile, String content) {
        if (originalFile == null || content == null) return;
        try {
            Path fileHistoryDir = HISTORY_DIR.resolve(Math.abs(originalFile.getAbsolutePath().hashCode()) + "_" + originalFile.getName());
            Files.createDirectories(fileHistoryDir);

            String timeStampStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
            Path snapshotPath = fileHistoryDir.resolve(timeStampStr + ".bak");
            Files.writeString(snapshotPath, content, StandardCharsets.UTF_8);

            try (Stream<Path> stream = Files.list(fileHistoryDir)) {
                List<Path> snaps = stream.sorted().toList();
                if (snaps.size() > 50) {
                    for (int i = 0; i < snaps.size() - 50; i++) {
                        Files.deleteIfExists(snaps.get(i));
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static List<HistoryEntry> getSnapshots(File originalFile) {
        List<HistoryEntry> entries = new ArrayList<>();
        if (originalFile == null) return entries;
        try {
            Path fileHistoryDir = HISTORY_DIR.resolve(Math.abs(originalFile.getAbsolutePath().hashCode()) + "_" + originalFile.getName());
            if (!Files.isDirectory(fileHistoryDir)) return entries;

            try (Stream<Path> stream = Files.list(fileHistoryDir)) {
                List<Path> snaps = stream.filter(p -> p.toString().endsWith(".bak")).sorted().toList();
                for (Path snap : snaps) {
                    String fileName = snap.getFileName().toString();
                    String label = fileName.replace(".bak", "").replace("_", " ");
                    entries.add(new HistoryEntry(label, snap.toFile(), LocalDateTime.now()));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return entries;
    }

    public static String loadSnapshotContent(File snapshotFile) {
        if (snapshotFile == null || !snapshotFile.exists()) return "";
        try {
            return Files.readString(snapshotFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
