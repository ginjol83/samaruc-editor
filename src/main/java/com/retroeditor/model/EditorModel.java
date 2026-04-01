package com.retroeditor.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.retroeditor.service.UserActionMonitor;

/**
 * Modelo para el editor de texto
 */
public class EditorModel {
    private final Map<File, String> fileContents = new HashMap<>();

    /**
     * Abre un archivo y carga su contenido en el modelo.
     * @param file Archivo a abrir.
     * @throws IOException Si ocurre un error al leer el archivo.
     */
    public void openFile(File file) throws IOException {
        String content = Files.readString(file.toPath());
        
        fileContents.put(file, content);
        UserActionMonitor.fileOpened(file.getName(), file.length());
    }

    /**
     * Crea un nuevo archivo en blanco en el modelo.
     * @param file Archivo a crear.
     */
    public void newFile(File file) {
        fileContents.put(file, "");
        UserActionMonitor.fileCreated(file.getAbsolutePath());
    }

    /**
     * Guarda el contenido en el archivo especificado.
     * @param file Archivo donde guardar.
     * @param content Contenido a guardar.
     * @throws IOException Si ocurre un error al escribir el archivo.
     */
    public void saveFile(File file, String content) throws IOException {
        Files.writeString(file.toPath(), content);
        
        fileContents.put(file, content);
        UserActionMonitor.fileSaved(file.getName(), content.length());
    }

    /**
     * Obtiene el contenido del archivo especificado.
     * @param file Archivo cuyo contenido se desea obtener.
     * @return Contenido del archivo.
     */
    public String getFileContent(File file) {
        return fileContents.getOrDefault(file, "");
    }

    /**
     * Obtiene el conjunto de archivos actualmente abiertos en el modelo.
     * @return Conjunto de archivos abiertos.
     */
    public Set<File> getOpenFiles() {
        return fileContents.keySet();
    }

    /**
     * Cierra el archivo especificado en el modelo.
     * @param file Archivo a cerrar.
     */
    public void closeFile(File file) {
        fileContents.remove(file);
        UserActionMonitor.fileClosed(file.getName());
    }
}
