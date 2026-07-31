package com.retroeditor.service.gameboy;

import java.util.Objects;
import java.util.Arrays;

/**
 * Representa un tile de 8x8 píxeles en formato 2bpp de Game Boy.
 * Almacena 16 bytes de datos del tile.
 */
public class GameBoyTile {
    private static final int TILE_SIZE_BYTES = 16;
    private final byte[] data;

    public GameBoyTile(byte[] data) {
        if (data == null || data.length != TILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Tile data must be exactly 16 bytes");
        }
        this.data = data.clone();
    }

    public byte[] getData() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameBoyTile)) return false;
        GameBoyTile tile = (GameBoyTile) o;
        return Arrays.equals(data, tile.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "Tile(" + Arrays.hashCode(data) + ")";
    }
}
