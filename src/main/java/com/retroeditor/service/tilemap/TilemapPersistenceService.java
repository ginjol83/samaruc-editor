package com.retroeditor.service.tilemap;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.retroeditor.model.tilemap.TilemapDocument;
import com.retroeditor.model.tilemap.TilemapTileData;

public class TilemapPersistenceService {
    private static final int MAX_MAP_DIMENSION = 1024;
    private static final int MAX_TILE_DIMENSION = 64;
    private final ObjectMapper objectMapper;

    public TilemapPersistenceService() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TilemapDocument load(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("El archivo no puede ser null.");
        }
        TilemapDocument document = objectMapper.readValue(file.toFile(), TilemapDocument.class);
        validate(document);
        populateNativeData(document);
        return document;
    }

    public void save(Path file, TilemapDocument document) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("El archivo no puede ser null.");
        }
        validate(document);
        populateNativeData(document);

        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("El archivo no tiene directorio padre.");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "samaruc-tilemap-", ".tmp");
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

    public static boolean isTilemapFile(java.io.File file) {
        return file != null && isTilemapFileName(file.getName());
    }

    public static boolean isTilemapFileName(String fileName) {
        return fileName != null
                && fileName.toLowerCase(java.util.Locale.ROOT).endsWith("." + TilemapDocument.FILE_EXTENSION);
    }

    private void validate(TilemapDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("El documento no puede ser null.");
        }
        if (!TilemapDocument.FORMAT.equals(document.getFormat())) {
            throw new IllegalArgumentException("Formato de tilemap no soportado.");
        }
        if (document.getVersion() != TilemapDocument.CURRENT_VERSION) {
            throw new IllegalArgumentException("Versión de tilemap no soportada: " + document.getVersion());
        }
        TilemapPlatform platform = parsePlatform(document.getPlatform());
        validateDimension(document.getMapWidth(), "ancho del mapa", MAX_MAP_DIMENSION);
        validateDimension(document.getMapHeight(), "alto del mapa", MAX_MAP_DIMENSION);
        validateDimension(document.getTileWidth(), "ancho del tile", MAX_TILE_DIMENSION);
        validateDimension(document.getTileHeight(), "alto del tile", MAX_TILE_DIMENSION);
        if (document.getSelectedTile() < 0 || document.getSelectedTile() > platform.getMaxTileIndex()) {
            throw new IllegalArgumentException("El tile seleccionado no está dentro del rango de la plataforma.");
        }

        int expectedCells = Math.multiplyExact(document.getMapWidth(), document.getMapHeight());
        List<Integer> map = document.getMap();
        List<Boolean> collisions = document.getCollisions();
        if (map == null || map.size() != expectedCells) {
            throw new IllegalArgumentException("El mapa no tiene las dimensiones declaradas.");
        }
        if (collisions == null || collisions.size() != expectedCells) {
            throw new IllegalArgumentException("La capa de colisiones no tiene las dimensiones declaradas.");
        }
        for (Integer tile : map) {
            if (tile == null || tile < 0 || tile > platform.getMaxTileIndex()) {
                throw new IllegalArgumentException("El mapa contiene un índice de tile inválido.");
            }
        }
        for (Boolean collision : collisions) {
            if (collision == null) {
                throw new IllegalArgumentException("La capa de colisiones contiene un valor nulo.");
            }
        }

        if (document.getTiles() == null) {
            throw new IllegalArgumentException("La lista de tiles no puede ser nula.");
        }
        for (TilemapTileData tile : document.getTiles()) {
            if (tile == null) {
                throw new IllegalArgumentException("El documento contiene un tile nulo.");
            }
            validateDimension(tile.getWidth(), "ancho del tile", MAX_TILE_DIMENSION);
            validateDimension(tile.getHeight(), "alto del tile", MAX_TILE_DIMENSION);
            if (tile.getWidth() != document.getTileWidth() || tile.getHeight() != document.getTileHeight()) {
                throw new IllegalArgumentException("Las dimensiones de los tiles no coinciden con el documento.");
            }
            if (tile.getPixels() == null || tile.getPixels().size() != Math.multiplyExact(tile.getWidth(), tile.getHeight())) {
                throw new IllegalArgumentException("Los píxeles del tile no tienen las dimensiones declaradas.");
            }
            for (Integer pixel : tile.getPixels()) {
                if (pixel == null) {
                    throw new IllegalArgumentException("El tile contiene un píxel nulo.");
                }
            }
        }
    }

    private void populateNativeData(TilemapDocument document) {
        TilemapPlatform platform = parsePlatform(document.getPlatform());
        for (TilemapTileData tile : document.getTiles()) {
            byte[] encoded = TilemapNativeDataCodec.encode(platform, tile.getWidth(), tile.getHeight(), tile.getPixels());
            tile.setNativeData(toUnsignedList(encoded));
        }
    }

    private List<Integer> toUnsignedList(byte[] values) {
        List<Integer> result = new java.util.ArrayList<>(values.length);
        for (byte value : values) {
            result.add(value & 0xFF);
        }
        return result;
    }

    private TilemapPlatform parsePlatform(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La plataforma del tilemap es obligatoria.");
        }
        try {
            return TilemapPlatform.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Plataforma de tilemap no soportada: " + value, ex);
        }
    }

    private void validateDimension(int value, String name, int maximum) {
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException("El " + name + " debe estar entre 1 y " + maximum + ".");
        }
    }
}
