package com.retroeditor.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Servicio para calcular estadísticas del proyecto (líneas de código, tamaño ROM, tiles usados, etc.).
 */
public class ProjectStatisticsService {

    public static final class ProjectStats {
        private final int totalFiles;
        private final long totalLinesOfCode;
        private final long romSizeBytes;
        private final int tileFilesCount;

        public ProjectStats(int totalFiles, long totalLinesOfCode, long romSizeBytes, int tileFilesCount) {
            this.totalFiles = totalFiles;
            this.totalLinesOfCode = totalLinesOfCode;
            this.romSizeBytes = romSizeBytes;
            this.tileFilesCount = tileFilesCount;
        }

        public int getTotalFiles() { return totalFiles; }
        public long getTotalLinesOfCode() { return totalLinesOfCode; }
        public long getRomSizeBytes() { return romSizeBytes; }
        public int getTileFilesCount() { return tileFilesCount; }
    }

    public ProjectStats calculateStats(File projectDir) {
        if (projectDir == null || !projectDir.isDirectory()) {
            return new ProjectStats(0, 0, 0, 0);
        }

        int fileCount = 0;
        long totalLines = 0;
        long romSize = 0;
        int tileFiles = 0;

        try {
            List<Path> paths = Files.walk(projectDir.toPath())
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

            for (Path p : paths) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (p.toString().contains(".samarucws")) continue;

                fileCount++;

                if (name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".asm") || name.endsWith(".s") || name.endsWith(".txt") || name.endsWith(".md") || name.equals("makefile")) {
                    try {
                        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                        totalLines += lines.size();
                    } catch (Exception ignored) {}
                }

                if (name.endsWith(".gb") || name.endsWith(".rom") || name.endsWith(".bin") || name.endsWith(".hex") || name.endsWith(".tap") || name.endsWith(".z80") || name.endsWith(".slic")) {
                    try {
                        romSize += Files.size(p);
                    } catch (Exception ignored) {}
                }

                if (name.endsWith(".png") || name.contains("tile") || name.contains("sprite")) {
                    tileFiles++;
                }
            }
        } catch (Exception ignored) {}

        return new ProjectStats(fileCount, totalLines, romSize, tileFiles);
    }
}
