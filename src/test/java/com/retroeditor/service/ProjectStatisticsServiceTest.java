package com.retroeditor.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectStatisticsServiceTest {

    private final ProjectStatisticsService service = new ProjectStatisticsService();

    @TempDir
    Path tempDir;

    @Test
    void calculatesStatsCorrectly() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("main.c"), "int main() {\n    return 0;\n}\n");
        Files.writeString(tempDir.resolve("main.gb"), "ROM_DATA_BYTES");

        ProjectStatisticsService.ProjectStats stats = service.calculateStats(tempDir.toFile());

        Assertions.assertTrue(stats.getTotalFiles() >= 2);
        Assertions.assertTrue(stats.getTotalLinesOfCode() >= 3);
        Assertions.assertTrue(stats.getRomSizeBytes() > 0);
    }
}
