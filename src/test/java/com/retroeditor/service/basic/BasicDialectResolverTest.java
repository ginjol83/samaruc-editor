package com.retroeditor.service.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.retroeditor.service.ProjectPlatformDetectionService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasicDialectResolverTest {

    @TempDir
    Path tempDir;

    private static final String SPECTRUM_TARGET = "\"samaruc.target\": \"spectrum\"";

    private void writeTaggedSpectrum(Path dir) throws Exception {
        Files.writeString(dir.resolve("settings.json"), "{" + SPECTRUM_TARGET + "}");
    }

    @Test
    void nonBasicFileIsGeneric() {
        File cFile = tempDir.resolve("main.c").toFile();
        assertEquals(BasicDialect.GENERIC, new BasicDialectResolver().resolve(cFile, tempDir.toFile()));
    }

    @Test
    void nullFileIsGeneric() {
        assertEquals(BasicDialect.GENERIC, new BasicDialectResolver().resolve(null, tempDir.toFile()));
    }

    @Test
    void detectsAtariForBasOnlyProject() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/main.bas"), "10 PRINT \"HOLA\"\n");
        assertEquals(BasicDialect.ATARI,
                new BasicDialectResolver().resolve(tempDir.resolve("src/main.bas").toFile(), tempDir.toFile()));
    }

    @Test
    void platformSignalSelectsDialect() throws Exception {
        writeTaggedSpectrum(tempDir);
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/main.bas"), "10 PRINT \"HOLA\"\n");
        assertEquals(BasicDialect.ZX,
                new BasicDialectResolver().resolve(tempDir.resolve("src/main.bas").toFile(), tempDir.toFile()));
    }

    @Test
    void storedOverrideBeatsPlatformSignal() throws Exception {
        writeTaggedSpectrum(tempDir);
        Files.createDirectories(tempDir.resolve("src"));
        Path bas = tempDir.resolve("src/main.bas");
        Files.writeString(bas, "10 PRINT \"HOLA\"\n");
        new BasicDialectStore(tempDir).setDialect(
                BasicDialectStore.relativePathOf(tempDir, bas), BasicDialect.CPC);

        assertEquals(BasicDialect.CPC, new BasicDialectResolver().resolve(bas.toFile(), tempDir.toFile()));
    }

    @Test
    void platformDialectMapping() {
        assertEquals(BasicDialect.ATARI, BasicDialectResolver
                .dialectForPlatform(ProjectPlatformDetectionService.Platform.ATARI));
        assertEquals(BasicDialect.ZX, BasicDialectResolver
                .dialectForPlatform(ProjectPlatformDetectionService.Platform.SPECTRUM));
        assertEquals(BasicDialect.CPC, BasicDialectResolver
                .dialectForPlatform(ProjectPlatformDetectionService.Platform.CPC));
        assertEquals(BasicDialect.C64, BasicDialectResolver
                .dialectForPlatform(ProjectPlatformDetectionService.Platform.C64));
        assertEquals(BasicDialect.MSX, BasicDialectResolver
                .dialectForPlatform(ProjectPlatformDetectionService.Platform.MSX));
        assertNull(BasicDialectResolver
                .dialectForPlatform(ProjectPlatformDetectionService.Platform.GAMEBOY));
        assertNull(BasicDialectResolver
                .dialectForPlatform(ProjectPlatformDetectionService.Platform.UNKNOWN));
    }

    @Test
    void dialectFromNameIgnoresCaseAndUnknown() {
        assertEquals(BasicDialect.ZX, BasicDialect.fromName("zx"));
        assertEquals(BasicDialect.ATARI, BasicDialect.fromName("  Atari  "));
        assertEquals(BasicDialect.GENERIC, BasicDialect.fromName("desconocido"));
        assertEquals(BasicDialect.GENERIC, BasicDialect.fromName(null));
    }
}