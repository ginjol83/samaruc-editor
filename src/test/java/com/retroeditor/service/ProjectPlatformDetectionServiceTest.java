package com.retroeditor.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectPlatformDetectionServiceTest {

    private final ProjectPlatformDetectionService service = new ProjectPlatformDetectionService();

    @TempDir
    Path tempDir;

    @Test
    void detectGameBoyByGbHeader() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <gb/gb.h>\nvoid main(void){ DISPLAY_ON; wait_vbl_done(); }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.GAMEBOY, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectSpectrumByConioAndBorder() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <conio.h>\nvoid main(){ bordercolor(1); cputs(\"ZX\"); }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.SPECTRUM, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectUnknownWhenNoSignals() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "int main(void){ return 0; }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.UNKNOWN, result.getPlatform());
    }
}

