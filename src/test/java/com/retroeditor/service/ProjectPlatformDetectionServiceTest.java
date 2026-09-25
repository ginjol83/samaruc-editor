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
    void detectCpcByWorkspaceTarget() throws IOException {
        Files.writeString(tempDir.resolve("settings.json"), "{\"samaruc.target\": \"cpc\"}");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.CPC, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectCpcByHeaderAndApi() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <conio.h>\n#include <cpc.h>\nvoid main(void){ cpc_ClrScr(); }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.CPC, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectCpcByZ88dkTargetWithoutSpectrumSignal() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <cpc.h>\n// z88dk +cpc\nvoid main(void){ cpc_ClrScr(); }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.CPC, result.getPlatform());
    }

    @Test
    void detectUnknownWhenNoSignals() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "int main(void){ return 0; }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.UNKNOWN, result.getPlatform());
    }

    @Test
    void detectAtariByCc65ConioAndCgetc() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <conio.h>\nvoid main(void){ clrscr(); cputs(\"Atari\"); cgetc(); }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.ATARI, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectAtariByAtariHeader() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <atari.h>\nvoid main(void){ POKEY.putsq(0); }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.ATARI, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectC64ByC64Header() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <c64.h>\nvoid main(void){ VIC.border_color = 0; }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.C64, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectC64BySidHeaderAndConio() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <conio.h>\n#include <sid.h>\nvoid main(void){ clrscr(); cputs(\"C64\"); sid.address = 0xD400; }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.C64, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectC64ByCl65TargetFlag() throws IOException {
        Files.writeString(tempDir.resolve("Makefile"), "cl65 -t c64 -o out/main.prg src/main.c");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.C64, result.getPlatform());
        Assertions.assertTrue(result.getConfidence() > 0);
    }

    @Test
    void detectAtariNotMisclassifiedAsC64() throws IOException {
        Files.writeString(tempDir.resolve("main.c"), "#include <atari.h>\nvoid main(void){ clrscr(); cputs(\"Atari\"); cgetc(); }");

        ProjectPlatformDetectionService.DetectionResult result = service.detectProjectPlatform(tempDir.toFile());

        Assertions.assertEquals(ProjectPlatformDetectionService.Platform.ATARI, result.getPlatform());
    }
}

