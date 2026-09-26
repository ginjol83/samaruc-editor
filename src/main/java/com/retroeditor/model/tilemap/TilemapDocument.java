package com.retroeditor.model.tilemap;

import java.util.ArrayList;
import java.util.List;

import com.retroeditor.service.tilemap.TilemapPlatform;

public class TilemapDocument {
    public static final String FORMAT = "samaruc.tilemap";
    public static final int CURRENT_VERSION = 1;
    public static final String FILE_EXTENSION = "tilemap";

    private String format = FORMAT;
    private int version = CURRENT_VERSION;
    private String platform = TilemapPlatform.GAMEBOY.name();
    private int mapWidth = 32;
    private int mapHeight = 32;
    private int tileWidth = 8;
    private int tileHeight = 8;
    private int selectedTile;
    private String sourceTilesetPath;
    private List<Integer> map = new ArrayList<>();
    private List<Boolean> collisions = new ArrayList<>();
    private List<TilemapTileData> tiles = new ArrayList<>();

    public TilemapDocument() {
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public int getMapWidth() {
        return mapWidth;
    }

    public void setMapWidth(int mapWidth) {
        this.mapWidth = mapWidth;
    }

    public int getMapHeight() {
        return mapHeight;
    }

    public void setMapHeight(int mapHeight) {
        this.mapHeight = mapHeight;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public void setTileWidth(int tileWidth) {
        this.tileWidth = tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public void setTileHeight(int tileHeight) {
        this.tileHeight = tileHeight;
    }

    public int getSelectedTile() {
        return selectedTile;
    }

    public void setSelectedTile(int selectedTile) {
        this.selectedTile = selectedTile;
    }

    public String getSourceTilesetPath() {
        return sourceTilesetPath;
    }

    public void setSourceTilesetPath(String sourceTilesetPath) {
        this.sourceTilesetPath = sourceTilesetPath;
    }

    public List<Integer> getMap() {
        return map;
    }

    public void setMap(List<Integer> map) {
        this.map = map != null ? new ArrayList<>(map) : new ArrayList<>();
    }

    public List<Boolean> getCollisions() {
        return collisions;
    }

    public void setCollisions(List<Boolean> collisions) {
        this.collisions = collisions != null ? new ArrayList<>(collisions) : new ArrayList<>();
    }

    public List<TilemapTileData> getTiles() {
        return tiles;
    }

    public void setTiles(List<TilemapTileData> tiles) {
        this.tiles = tiles != null ? new ArrayList<>(tiles) : new ArrayList<>();
    }
}
