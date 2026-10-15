package com.retroeditor.service.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenCodeRuntimeTest {

    @TempDir
    Path tempDir;

    private static void touchExecutable(Path file) throws Exception {
        Files.writeString(file, "@echo off\r\n");
        assertTrue(file.toFile().setExecutable(true), "no se pudo marcar como ejecutable");
    }

    @Test
    void findsExeInPath() throws Exception {
        Path bin = tempDir.resolve("bin");
        Files.createDirectories(bin);
        touchExecutable(bin.resolve("opencode.exe"));

        OpenCodeRuntime runtime = new OpenCodeRuntime(null, List.of(bin), Duration.ofSeconds(2));

        assertTrue(runtime.isAvailable());
        assertEquals(bin.resolve("opencode.exe"), runtime.resolveExecutable().orElseThrow());
    }

    @Test
    void prefersExeOverCmdShim() throws Exception {
        Path bin = tempDir.resolve("bin");
        Files.createDirectories(bin);
        touchExecutable(bin.resolve("opencode.exe"));
        touchExecutable(bin.resolve("opencode.cmd"));

        OpenCodeRuntime runtime = new OpenCodeRuntime(null, List.of(bin), Duration.ofSeconds(2));

        assertEquals(bin.resolve("opencode.exe"), runtime.resolveExecutable().orElseThrow());
    }

    @Test
    void fallsBackToCmdWhenNoExeExists() throws Exception {
        Path bin = tempDir.resolve("bin");
        Files.createDirectories(bin);
        touchExecutable(bin.resolve("opencode.cmd"));

        OpenCodeRuntime runtime = new OpenCodeRuntime(null, List.of(bin), Duration.ofSeconds(2));

        assertEquals(bin.resolve("opencode.cmd"), runtime.resolveExecutable().orElseThrow());
    }

    @Test
    void prefersExeOverExtensionlessFile() throws Exception {
        Path bin = tempDir.resolve("bin");
        Files.createDirectories(bin);
        touchExecutable(bin.resolve("opencode.exe"));
        touchExecutable(bin.resolve("opencode"));

        OpenCodeRuntime runtime = new OpenCodeRuntime(null, List.of(bin), Duration.ofSeconds(2));

        assertEquals(bin.resolve("opencode.exe"), runtime.resolveExecutable().orElseThrow());
    }

    @Test
    void resolvesExtensionlessFileWhenItIsTheOnlyCandidate() throws Exception {
        Path bin = tempDir.resolve("bin");
        Files.createDirectories(bin);
        touchExecutable(bin.resolve("opencode"));

        OpenCodeRuntime runtime = new OpenCodeRuntime(null, List.of(bin), Duration.ofSeconds(2));

        assertEquals(bin.resolve("opencode"), runtime.resolveExecutable().orElseThrow());
    }

    @Test
    void honoursFirstEntryOfPath() throws Exception {
        Path first = tempDir.resolve("first");
        Path second = tempDir.resolve("second");
        Files.createDirectories(first);
        Files.createDirectories(second);
        touchExecutable(first.resolve("opencode.exe"));
        touchExecutable(second.resolve("opencode.exe"));

        OpenCodeRuntime runtime = new OpenCodeRuntime(null, List.of(first, second), Duration.ofSeconds(2));

        assertEquals(first.resolve("opencode.exe"), runtime.resolveExecutable().orElseThrow());
    }

    @Test
    void acceptsExplicitConfiguredExecutable() throws Exception {
        Path custom = tempDir.resolve("tools").resolve("opencode.exe");
        Files.createDirectories(custom.getParent());
        touchExecutable(custom);

        OpenCodeRuntime runtime = new OpenCodeRuntime(custom.toString(), List.of(), Duration.ofSeconds(2));

        assertEquals(custom.toAbsolutePath().normalize(), runtime.resolveExecutable().orElseThrow());
        assertEquals(null, runtime.unavailableReason());
    }

    @Test
    void acceptsDirectoryAsConfiguredExecutable() throws Exception {
        Path customDir = tempDir.resolve("tools");
        Files.createDirectories(customDir);
        touchExecutable(customDir.resolve("opencode.exe"));

        OpenCodeRuntime runtime = new OpenCodeRuntime(customDir.toString(), List.of(), Duration.ofSeconds(2));

        assertEquals(customDir.resolve("opencode.exe").toAbsolutePath().normalize(),
                runtime.resolveExecutable().orElseThrow());
    }

    @Test
    void reportsUnavailableWhenBinaryMissing() {
        OpenCodeRuntime runtime = new OpenCodeRuntime(null, List.of(tempDir), Duration.ofSeconds(2));

        assertFalse(runtime.isAvailable());
        assertEquals(Optional.empty(), runtime.resolveExecutable());
        assertNotNull(runtime.unavailableReason());
        assertTrue(runtime.unavailableReason().contains("PATH"));
        assertEquals(Optional.empty(), runtime.version());
    }

    @Test
    void reportsConfiguredPathWhenMissing() {
        Path missing = tempDir.resolve("nope").resolve("opencode.exe");

        OpenCodeRuntime runtime = new OpenCodeRuntime(missing.toString(), List.of(), Duration.ofSeconds(2));

        assertFalse(runtime.isAvailable());
        assertTrue(runtime.unavailableReason().contains(missing.toString()));
    }

    

    @Test
    void installationHintsAreNonEmpty() {
        List<String> hints = OpenCodeRuntime.installationHints();

        assertFalse(hints.isEmpty());
        assertTrue(hints.stream().anyMatch(h -> h.contains("opencode")));
    }
}