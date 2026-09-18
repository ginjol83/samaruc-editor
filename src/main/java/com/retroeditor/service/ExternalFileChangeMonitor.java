package com.retroeditor.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Monitor para el watcher en vivo de cambios externos. Se invoca de forma
 * periódica (desde el hilo de JavaFX, p. ej. con un {@code Timeline}) con el
 * mapa de huellas registradas de las pestañas abiertas y devuelve, en cada
 * llamada, únicamente los archivos cuya modificación externa es <em>nueva</em>:
 * un mismo estado de disco no se reporta dos veces hasta que no vuelve a
 * cambiar o la pestaña se vuelve a sincronizar (al recargar o guardar).
 *
 * <p>Los archivos de gran tamaño no se vigilan en vivo para no penalizar el
 * hilo de JavaFX; el chequeo puntual de compilar/guardar sigue cubriéndolos.</p>
 */
public final class ExternalFileChangeMonitor {

    /** Tamaño máximo (bytes) vigilado en vivo. */
    static final long MAX_WATCHED_FILE_SIZE = 1_048_576L;

    private final Map<File, String> lastReported = new HashMap<>();
    private final LinkedHashSet<File> currentChanges = new LinkedHashSet<>();
    private File lastNotifiedFile;

    /**
     * Actualiza el estado del monitor con las huellas registradas actuales y
     * devuelve las modificaciones externas nuevas (no reportadas previamente
     * para ese mismo contenido de disco).
     *
     * @param registrations huella registrada (última versión sincronizada) de
     *     cada archivo abierto en el editor
     * @return archivos con un cambio externo nuevo
     */
    public List<File> refresh(Map<File, String> registrations) {
        List<File> newlyDetected = new ArrayList<>();
        currentChanges.clear();

        if (registrations == null || registrations.isEmpty()) {
            lastReported.clear();
            lastNotifiedFile = null;
            return newlyDetected;
        }

        lastReported.keySet().retainAll(registrations.keySet());

        for (Map.Entry<File, String> entry : registrations.entrySet()) {
            File file = entry.getKey();
            String registered = entry.getValue();
            if (file == null || registered == null || registered.isEmpty()) {
                continue;
            }
            if (!isWatchable(file)) {
                lastReported.remove(file);
                continue;
            }
            if (!ExternalFileChangeService.hasExternalChange(file, registered)) {
                lastReported.remove(file);
                continue;
            }

            currentChanges.add(file);

            String diskFingerprint = ExternalFileChangeService.fingerprintOfDisk(file);
            if (diskFingerprint == null) {
                lastReported.remove(file);
                continue;
            }
            if (!Objects.equals(diskFingerprint, lastReported.get(file))) {
                lastReported.put(file, diskFingerprint);
                newlyDetected.add(file);
                lastNotifiedFile = file;
            }
        }

        return newlyDetected;
    }

    /**
     * @return archivos que tienen actualmente un cambio externo sin sincronizar
     */
    public Set<File> currentExternalChanges() {
        return Collections.unmodifiableSet(currentChanges);
    }

    /**
     * @return el archivo notificado más recientemente, o {@code null}
     */
    public File lastNotifiedFile() {
        return lastNotifiedFile;
    }

    private static boolean isWatchable(File file) {
        return file != null && file.isFile() && file.canRead()
            && file.length() > 0 && file.length() <= MAX_WATCHED_FILE_SIZE;
    }
}