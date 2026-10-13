package com.retroeditor.service.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasicDialectStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void absentOverrideReturnsEmpty() {
        BasicDialectStore store = new BasicDialectStore(tempDir);
        assertEquals(Optional.empty(), store.dialectFor("src/main.bas"));
    }

    @Test
    void setAndReadRoundTrip() throws Exception {
        BasicDialectStore store = new BasicDialectStore(tempDir);
        store.setDialect("src/truco.bas", BasicDialect.ZX);

        BasicDialectStore reloaded = new BasicDialectStore(tempDir);
        assertEquals(Optional.of(BasicDialect.ZX), reloaded.dialectFor("src/truco.bas"));
        assertEquals(Optional.empty(), reloaded.dialectFor("src/otro.bas"));

        Path sidecar = tempDir.resolve(".samaruc").resolve("basic-dialects.properties");
        assertTrue(Files.isRegularFile(sidecar), "Se debe escribir el sidecar del proyecto");
    }

    @Test
    void clearRemovesOverride() throws Exception {
        BasicDialectStore store = new BasicDialectStore(tempDir);
        store.setDialect("main.bas", BasicDialect.C64);
        store.clearDialect("main.bas");

        BasicDialectStore reloaded = new BasicDialectStore(tempDir);
        assertEquals(Optional.empty(), reloaded.dialectFor("main.bas"));
    }

    @Test
    void genericIsNotPersistedAsOverride() throws Exception {
        BasicDialectStore store = new BasicDialectStore(tempDir);
        store.setDialect("main.bas", BasicDialect.GENERIC);
        assertEquals(Optional.empty(), new BasicDialectStore(tempDir).dialectFor("main.bas"));
    }

    @Test
    void relativePathNormalizesSeparators() {
        String rel = BasicDialectStore.relativePathOf(
                Path.of("C:/repo/proyecto"), Path.of("C:\\repo\\proyecto\\src\\main.bas"));
        assertEquals("src/main.bas", rel);
    }

    @Test
    void unreadableSidecarFallsBackToNoOverrides() throws Exception {
        Path sidecar = tempDir.resolve(".samaruc").resolve("basic-dialects.properties");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, "no es un properties valido \u00e1\u00e9\u00ed");
        assertEquals(Optional.empty(), new BasicDialectStore(tempDir).dialectFor("main.bas"));
    }
}