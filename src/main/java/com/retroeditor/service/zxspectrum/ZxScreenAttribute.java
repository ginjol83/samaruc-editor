package com.retroeditor.service.zxspectrum;

public record ZxScreenAttribute(
        int ink,
        int paper,
        boolean bright,
        boolean flash
) {
    public static final int BLACK = 0;
    public static final int BLUE = 1;
    public static final int RED = 2;
    public static final int MAGENTA = 3;
    public static final int GREEN = 4;
    public static final int CYAN = 5;
    public static final int YELLOW = 6;
    public static final int WHITE = 7;

    public byte toByte() {
        int value = ink & 0x07;
        value |= ((paper & 0x07) << 3);
        if (bright) { value |= 0x40; }
        if (flash) { value |= 0x80; }
        return (byte) value;
    }

    public static ZxScreenAttribute fromByte(byte b) {
        int val = b & 0xFF;
        int ink = val & 0x07;
        int paper = (val >> 3) & 0x07;
        boolean bright = (val & 0x40) != 0;
        boolean flash = (val & 0x80) != 0;
        return new ZxScreenAttribute(ink, paper, bright, flash);
    }
}
