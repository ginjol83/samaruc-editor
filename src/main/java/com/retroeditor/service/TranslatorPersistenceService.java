package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.retroeditor.model.TranslatorModel;

/**
 * Servicio de persistencia para archivos .smt (Samaruc Machine Translation).
 * Carga y guarda el estado de traducción en formato JSON con rutas relativas.
 */
public class TranslatorPersistenceService {

    private final ObjectMapper objectMapper;

    public TranslatorPersistenceService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Carga un archivo .smt desde disco.
     *
     * @param smtFile Archivo .smt a cargar.
     * @return El modelo cargado, o un modelo vacío si no existe.
     */
    public TranslatorModel load(File smtFile) {
        if (smtFile == null || !smtFile.exists()) {
            return new TranslatorModel();
        }

        try {
            return objectMapper.readValue(smtFile, TranslatorModel.class);
        } catch (IOException e) {
            System.err.println("Error al cargar archivo .smt: " + e.getMessage());
            return new TranslatorModel();
        }
    }

    /**
     * Guarda un modelo de traducción en un archivo .smt.
     *
     * @param smtFile Archivo destino.
     * @param model Modelo a guardar.
     * @throws IOException Si no se puede escribir el archivo.
     */
    public void save(File smtFile, TranslatorModel model) throws IOException {
        if (smtFile == null || model == null) {
            throw new IllegalArgumentException("El archivo y el modelo no pueden ser null");
        }
        objectMapper.writeValue(smtFile, model);
    }

    /**
     * Convierte una ruta absoluta del ROM a ruta relativa respecto al archivo .smt.
     *
     * @param romAbsolutePath Ruta absoluta del ROM.
     * @param smtFileDir Directorio donde está el archivo .smt.
     * @return Ruta relativa al directorio del .smt.
     */
    public String toRelativePath(Path romAbsolutePath, Path smtFileDir) {
        if (romAbsolutePath == null || smtFileDir == null) return null;
        try {
            return smtFileDir.relativize(romAbsolutePath).toString().replace('\\', '/');
        } catch (Exception e) {
            return romAbsolutePath.getFileName().toString();
        }
    }

    /**
     * Convierte una ruta relativa (almacenada en el .smt) a ruta absoluta
     * usando el directorio del archivo .smt como referencia.
     *
     * @param relativePath Ruta relativa almacenada.
     * @param smtFileDir Directorio donde está el archivo .smt.
     * @return Ruta absoluta resuelta, o null si no se puede resolver.
     */
    public Path toAbsolutePath(String relativePath, Path smtFileDir) {
        if (relativePath == null || relativePath.isBlank() || smtFileDir == null) return null;
        Path resolved = smtFileDir.resolve(relativePath).normalize();
        return resolved.toAbsolutePath();
    }

    /**
     * Comprueba si un archivo tiene extensión .smt.
     */
    public static boolean isSmtFile(File file) {
        return file != null && file.getName().toLowerCase().endsWith("." + TranslatorModel.FILE_EXTENSION);
    }

    /**
     * Comprueba si un nombre de archivo tiene extensión .smt.
     */
    public static boolean isSmtFileName(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith("." + TranslatorModel.FILE_EXTENSION);
    }
}
