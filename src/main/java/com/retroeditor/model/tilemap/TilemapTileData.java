package com.retroeditor.model.tilemap;

import java.util.ArrayList;
import java.util.List;

public class TilemapTileData {
    private int width;
    private int height;
    private List<Integer> pixels = new ArrayList<>();
    private List<Integer> nativeData = new ArrayList<>();

    public TilemapTileData() {
    }

    public TilemapTileData(int width, int height, List<Integer> pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels != null ? new ArrayList<>(pixels) : new ArrayList<>();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public List<Integer> getPixels() {
        return pixels;
    }

    public void setPixels(List<Integer> pixels) {
        this.pixels = pixels != null ? new ArrayList<>(pixels) : new ArrayList<>();
    }

    public List<Integer> getNativeData() {
        return nativeData;
    }

    public void setNativeData(List<Integer> nativeData) {
        this.nativeData = nativeData != null ? new ArrayList<>(nativeData) : new ArrayList<>();
    }
}
