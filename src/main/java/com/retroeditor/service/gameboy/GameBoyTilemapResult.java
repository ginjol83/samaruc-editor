package com.retroeditor.service.gameboy;

/**
 * Resultado de la conversión de PNG a tilemap para Game Boy.
 */
public class GameBoyTilemapResult {
    private final byte[] mapData;      // índices de tiles (20x18 = 360 bytes)
    private final byte[] tileData;     // datos de tiles (num_tiles * 16 bytes)
    private final int tileCount;       // número de tiles únicos
    private final int mapWidth;        // ancho en tiles (20)
    private final int mapHeight;       // alto en tiles (18)

    public GameBoyTilemapResult(byte[] mapData, byte[] tileData, int tileCount, int mapWidth, int mapHeight) {
        this.mapData = mapData;
        this.tileData = tileData;
        this.tileCount = tileCount;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
    }

    public byte[] getMapData() {
        return mapData;
    }

    public byte[] getTileData() {
        return tileData;
    }

    public int getTileCount() {
        return tileCount;
    }

    public int getMapWidth() {
        return mapWidth;
    }

    public int getMapHeight() {
        return mapHeight;
    }

    public int getMapDataSize() {
        return mapWidth * mapHeight;
    }

    public int getTileDataSize() {
        return tileCount * 16;
    }
}
