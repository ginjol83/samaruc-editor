package com.retroeditor.service.zxspectrum;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.retroeditor.model.zxscreen.ZxScreenDocument;

/**
 * Guardado y carga del fichero .zxscreen. Mismo criterio que el tilemap: se escribe en un
 * temporal del mismo directorio y se mueve encima del destino, para que un fallo a mitad
 * de escritura no deje el proyecto del usuario corrupto.
 */
public class ZxScreenPersistenceService {
    private final ObjectMapper objectMapper;

    public ZxScreenPersistenceService() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ZxScreenDocument load(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("El archivo no puede ser null.");
        }
        ZxScreenDocument document = objectMapper.readValue(file.toFile(), ZxScreenDocument.class);
        validate(document);
        return document;
    }

    public void save(Path file, ZxScreenDocument document) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("El archivo no puede ser null.");
        }
        validate(document);

        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("El archivo no tiene directorio padre.");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "samaruc-zxscreen-", ".tmp");
        try {
            objectMapper.writeValue(temporary.toFile(), document);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static boolean isZxScreenFile(File file) {
        return file != null && isZxScreenFileName(file.getName());
    }

    public static boolean isZxScreenFileName(String fileName) {
        return fileName != null
                && fileName.toLowerCase(Locale.ROOT).endsWith("." + ZxScreenDocument.FILE_EXTENSION);
    }

    private void validate(ZxScreenDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("El documento no puede ser null.");
        }
        if (!ZxScreenDocument.FORMAT.equals(document.getFormat())) {
            throw new IllegalArgumentException("Formato de pantalla ZX no soportado: " + document.getFormat());
        }
        if (document.getVersion() != ZxScreenDocument.CURRENT_VERSION) {
            throw new IllegalArgumentException("Versión de pantalla ZX no soportada: " + document.getVersion());
        }
        if (document.getAttrCols() != ZxScreenEditorModel.ATTR_COLS
                || document.getAttrRows() != ZxScreenEditorModel.ATTR_ROWS) {
            throw new IllegalArgumentException("La pantalla del ZX Spectrum siempre es de 32x24 atributos.");
        }
        validateColor(document.getSelectedInk(), "tinta seleccionada");
        validateColor(document.getSelectedPaper(), "fondo seleccionado");

        int expected = Math.multiplyExact(document.getAttrCols(), document.getAttrRows());
        List<Integer> attributes = document.getAttributes();
        if (attributes == null || attributes.size() != expected) {
            throw new IllegalArgumentException("La matriz de atributos no tiene las dimensiones declaradas.");
        }
        for (Integer attribute : attributes) {
            if (attribute == null || attribute < 0 || attribute > 0xFF) {
                throw new IllegalArgumentException("La matriz de atributos contiene un valor inválido.");
            }
        }
    }

    private void validateColor(int value, String name) {
        if (value < ZxScreenAttribute.BLACK || value > ZxScreenAttribute.WHITE) {
            throw new IllegalArgumentException("El color de " + name + " no está en la paleta del ZX Spectrum.");
        }
    }
}