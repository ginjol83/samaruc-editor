package com.retroeditor.service.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del ciclo de vida de {@code opencode serve} sin levantar un servidor real: se comprueban
 * los argumentos, la deteccion del binario y el fallo de arranque, que es el error que mas se va
 * a ver en la practica (opencode instalado pero sin autenticar).
 */
class OpenCodeServerBridgeTest {

    @TempDir
    Path tempDir;

    private static void touchExecutable(Path file, String body) throws Exception {
        Files.writeString(file, body);
        assertTrue(file.toFile().setExecutable(true), "no se pudo marcar como ejecutable");
    }

    private OpenCodeRuntime runtimeOver(List<Path> searchPath) {
        return new OpenCodeRuntime(null, searchPath, Duration.ofSeconds(2));
    }

    private OpenCodeServerBridge bridge(AiSettings settings, OpenCodeRuntime runtime, Duration timeout) {
        return new OpenCodeServerBridge(settings, runtime, timeout);
    }

    @Test
    void serveArgumentsPinLoopbackAndPort() {
        assertEquals(List.of("serve", "--port", "4096", "--hostname", "127.0.0.1"),
                OpenCodeServerBridge.serveArguments(4096));
    }

    @Test
    void notRunningBeforeStart() {
        OpenCodeServerBridge bridge = bridge(AiSettings.defaults(), runtimeOver(List.of(tempDir)),
                Duration.ofSeconds(1));

        assertFalse(bridge.isRunning());
        assertTrue(bridge.baseUri().isEmpty());
    }

    @Test
    void startFailsWhenBinaryIsMissing() {
        OpenCodeServerBridge bridge = bridge(AiSettings.defaults(), runtimeOver(List.of(tempDir)),
                Duration.ofSeconds(1));

        AiProvider.AiException failure = assertThrows(AiProvider.AiException.class,
                () -> bridge.start(tempDir));

        assertTrue(failure.getMessage().contains("opencode"));
        assertFalse(bridge.isRunning(), "un arranque fallido no debe dejar un proceso colgando");
    }

    @Test
    void startFailsWhenTheServerDiesImmediately() throws Exception {
        Path bin = tempDir.resolve("bin");
        Files.createDirectories(bin);
        touchExecutable(bin.resolve("opencode.cmd"), "@echo off\r\nexit /b 1\r\n");

        OpenCodeServerBridge bridge = bridge(AiSettings.defaults(), runtimeOver(List.of(bin)),
                Duration.ofSeconds(5));

        AiProvider.AiException failure = assertThrows(AiProvider.AiException.class,
                () -> bridge.start(tempDir));

        assertTrue(failure.getMessage().contains("termino antes de arrancar"),
                "un proceso que muere al arrancar debe decirse, no quedarse esperando al timeout");
        bridge.close();
    }

    @Test
    void closeIsIdempotentWhenNothingStarted() {
        OpenCodeServerBridge bridge = bridge(AiSettings.defaults(), runtimeOver(List.of(tempDir)),
                Duration.ofSeconds(1));

        bridge.close();
        bridge.close();

        assertFalse(bridge.isRunning());
    }

    @Test
    void usesTheConfiguredPort() {
        AiSettings settings = new AiSettings(null, 4999, AiSettings.Mode.SERVE, null, 64, 5, 40);

        assertEquals(4999, settings.port(), "el puerto configurado debe respetarse");
    }

    @Test
    void restartsOnlyWhenTheWorkspaceReallyChanges() {
        Path project = tempDir.resolve("juego");

        assertTrue(OpenCodeServerBridge.sameWorkspace(project, project),
                "el mismo proyecto no debe reiniciar el servidor");
        assertTrue(OpenCodeServerBridge.sameWorkspace(tempDir.resolve(".").resolve("juego"), project),
                "las rutas equivalentes con . o .. son el mismo workspace");
        assertTrue(OpenCodeServerBridge.sameWorkspace(null, null),
                "sin workspace explicito no hay nada que reiniciar");
        assertFalse(OpenCodeServerBridge.sameWorkspace(project, null),
                "abrir un proyecto con el servidor ya lanzado sin cwd obliga a reiniciarlo: "
                    + "si no, el agente leeria el directorio del proyecto anterior");
        assertFalse(OpenCodeServerBridge.sameWorkspace(project, tempDir.resolve("otro")));
    }
}