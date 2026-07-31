package com.retroeditor.model;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.retroeditor.service.UserActionMonitor;

/**
 * Modelo para el editor de texto
 */
public class EditorModel {
    private static final int BINARY_SNIFF_BYTES = 8192;
    private static final List<Charset> TEXT_FALLBACK_CHARSETS = Arrays.asList(
        StandardCharsets.UTF_8,
        Charset.forName("windows-1252"),
        StandardCharsets.ISO_8859_1
    );

    private final Map<File, String> fileContents = new HashMap<>();

    /**
     * Abre un archivo y carga su contenido en el modelo.
     * @param file Archivo a abrir.
     * @throws IOException Si ocurre un error al leer el archivo.
     */
    public void openFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());

        if (looksLikeBinary(bytes)) {
            throw new IOException("El archivo parece binario y no puede abrirse como texto directamente.");
        }

        String content = decodeWithFallback(bytes);
        
        fileContents.put(file, content);
        UserActionMonitor.fileOpened(file.getName(), file.length());
    }

    /**
     * Fuerza la apertura del archivo como texto con codificación de un byte.
     * Se usa cuando el usuario decide abrir un archivo potencialmente binario.
     */
    public void openFileAsSingleByteText(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
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

    /**
     * Actualiza el mapa interno cuando un archivo abierto se renombra en disco.
     * @param previousFile Archivo anterior.
     * @param renamedFile Archivo renombrado.
     */
    public void renameOpenFile(File previousFile, File renamedFile) {
        if (previousFile == null || renamedFile == null) return;
        String content = fileContents.remove(previousFile);
        if (content != null) {
            fileContents.put(renamedFile, content);
        }
    }

    private String decodeWithFallback(byte[] bytes) throws IOException {
        for (Charset charset : TEXT_FALLBACK_CHARSETS) {
            try {
                return decodeStrict(bytes, charset);
            } catch (CharacterCodingException ignored) {
                // Probar siguiente charset del fallback
            }
        }
        throw new IOException("No se pudo decodificar el archivo con UTF-8, windows-1252 o ISO-8859-1.");
    }

    private String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer chars = decoder.decode(ByteBuffer.wrap(bytes));
        return chars.toString();
    }

    private boolean looksLikeBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;

        int sampleLength = Math.min(bytes.length, BINARY_SNIFF_BYTES);
        int controlCount = 0;

        for (int i = 0; i < sampleLength; i++) {
            int value = bytes[i] & 0xFF;
            if (value == 0) return true;

            boolean isControl = value < 32 && value != '\n' && value != '\r' && value != '\t' && value != '\f';
            if (isControl) controlCount++;
        }

        double controlRatio = (double) controlCount / (double) sampleLength;
        return controlRatio > 0.30d;
    }
}
