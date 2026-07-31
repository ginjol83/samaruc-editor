package com.retroeditor.service.gameboy;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Deduplicador de tiles.
 * Mantiene un registro de tiles únicos y mapea índices de posición a índices de tile.
 */
public class TileDeduplicator {
    private final Map<GameBoyTile, Integer> tileToIndex = new HashMap<>();
    private final List<GameBoyTile> uniqueTiles = new ArrayList<>();

    /**
     * Registra un tile y retorna su índice (reutilizando si existe).
     */
    public int registerTile(GameBoyTile tile) {
        if (tileToIndex.containsKey(tile)) {
            return tileToIndex.get(tile);
        }

        int index = uniqueTiles.size();
        uniqueTiles.add(tile);
        tileToIndex.put(tile, index);
        return index;
    }

    /**
     * Retorna la lista de tiles únicos.
     */
    public List<GameBoyTile> getUniqueTiles() {
        return new ArrayList<>(uniqueTiles);
    }

    /**
     * Retorna el número de tiles únicos.
     */
    public int getTileCount() {
        return uniqueTiles.size();
    }

    /**
     * Valida que no haya más de 256 tiles únicos.
     */
    public void validate() {
        if (uniqueTiles.size() > 256) {
            throw new IllegalArgumentException(
                "Too many unique tiles: " + uniqueTiles.size() + " (max 256)"
            );
        }
    }
}
