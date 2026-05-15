package com.retroeditor.service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;
import java.util.List;

import com.retroeditor.plugin.PluginInfo;
import com.retroeditor.plugin.PluginManager;

import javafx.application.Platform;

/**
 * Casos de uso de plugins desacoplados de la UI.
 */
public class PluginApplicationService {

    public void ensurePluginsDir(File pluginsDir) {
        if (pluginsDir == null) return;
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }
    }

    public List<PluginInfo> discoverPlugins(PluginManager pluginManager) {
        if (pluginManager == null) return Collections.emptyList();
        return pluginManager.discoverPlugins();
    }

    public void reloadPlugins(PluginManager pluginManager) {
        if (pluginManager == null) return;
        runOnFxThreadAndWait(pluginManager::reloadPlugins);
    }

    public File installPluginJar(File sourceJar, File pluginsDir) throws Exception {
        return installPluginJar(sourceJar, pluginsDir, sourceJar != null ? sourceJar.getName() : null);
    }

    public File installPluginJar(File sourceJar, File pluginsDir, String destinationJarName) throws Exception {
        if (sourceJar == null || pluginsDir == null) {
            throw new IllegalArgumentException("Parametros invalidos para instalar plugin");
        }

        ensurePluginsDir(pluginsDir);

        String safeName = destinationJarName != null && !destinationJarName.isBlank()
            ? destinationJarName.trim()
            : sourceJar.getName();
        File destination = new File(pluginsDir, safeName);
        Files.copy(sourceJar.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    public File installPluginJar(Path sourceJar, File pluginsDir, String destinationJarName) throws Exception {
        if (sourceJar == null) {
            throw new IllegalArgumentException("Ruta de plugin invalida");
        }
        return installPluginJar(sourceJar.toFile(), pluginsDir, destinationJarName);
    }

    public boolean deletePluginJar(File pluginsDir, String jarName) {
        if (pluginsDir == null || jarName == null || jarName.isBlank()) {
            return false;
        }

        File target = new File(pluginsDir, jarName.trim());
        if (!target.exists() || !target.isFile()) {
            return false;
        }

        return target.delete();
    }

    public void setPluginEnabledAndReload(PluginManager pluginManager, String jarName, boolean enabled) {
        if (pluginManager == null || jarName == null || jarName.isBlank()) return;
        runOnFxThreadAndWait(() -> {
            pluginManager.setJarEnabled(jarName, enabled);
            pluginManager.reloadPlugins();
        });
    }

    private void runOnFxThreadAndWait(Runnable action) {
        if (action == null) return;

        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        try {
            Platform.runLater(() -> {
                try {
                    action.run();
                } catch (Throwable t) {
                    errorRef.set(t);
                } finally {
                    latch.countDown();
                }
            });
        } catch (IllegalStateException toolkitNotInitialized) {
            // Entornos de test sin toolkit JavaFX inicializado.
            action.run();
            return;
        }

        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupcion esperando al hilo JavaFX", ie);
        }

        Throwable error = errorRef.get();
        if (error == null) return;
        if (error instanceof RuntimeException re) throw re;
        throw new RuntimeException(error);
    }
}

