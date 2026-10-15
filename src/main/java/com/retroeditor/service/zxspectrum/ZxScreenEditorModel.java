package com.retroeditor.service.zxspectrum;

public class ZxScreenEditorModel {
    public static final int ATTR_COLS = 32;
    public static final int ATTR_ROWS = 24;
    public static final int PIXEL_COLS = 256;
    public static final int PIXEL_ROWS = 192;

    private final ZxScreenAttribute[][] attributes = new ZxScreenAttribute[ATTR_ROWS][ATTR_COLS];

    public ZxScreenEditorModel() {
        ZxScreenAttribute defaultAttr = new ZxScreenAttribute(ZxScreenAttribute.WHITE, ZxScreenAttribute.BLACK, false, false);
        for (int r = 0; r < ATTR_ROWS; r++) {
            for (int c = 0; c < ATTR_COLS; c++) {
                attributes[r][c] = defaultAttr;
            }
        }
    }

    public ZxScreenAttribute getAttribute(int row, int col) {
        if (row < 0 || row >= ATTR_ROWS || col < 0 || col >= ATTR_COLS) {
            return new ZxScreenAttribute(ZxScreenAttribute.WHITE, ZxScreenAttribute.BLACK, false, false);
        }
        return attributes[row][col];
    }

    public void setAttribute(int row, int col, ZxScreenAttribute attr) {
        if (row < 0 || row >= ATTR_ROWS || col < 0 || col >= ATTR_COLS) {
            return;
        }
        attributes[row][col] = attr;
    }

    public byte[] getAttributeBytes() {
        byte[] data = new byte[ATTR_COLS * ATTR_ROWS];
        for (int r = 0; r < ATTR_ROWS; r++) {
            for (int c = 0; c < ATTR_COLS; c++) {
                data[r * ATTR_COLS + c] = attributes[r][c].toByte();
            }
        }
        return data;
    }

    public void setAttributeBytes(byte[] data) {
        if (data == null) return;
        int limit = Math.min(data.length, ATTR_COLS * ATTR_ROWS);
        for (int i = 0; i < limit; i++) {
            int r = i / ATTR_COLS;
            int c = i % ATTR_COLS;
            attributes[r][c] = ZxScreenAttribute.fromByte(data[i]);
        }
    }

    public boolean hasAttributeClash(int pixelRow, int pixelCol) {
        if (pixelRow < 0 || pixelRow >= PIXEL_ROWS || pixelCol < 0 || pixelCol >= PIXEL_COLS) {
            return false;
        }
        int attrRow = pixelRow / 8;
        int attrCol = pixelCol / 8;
        ZxScreenAttribute attr = getAttribute(attrRow, attrCol);
        return attr.bright() || attr.flash();
    }
}
