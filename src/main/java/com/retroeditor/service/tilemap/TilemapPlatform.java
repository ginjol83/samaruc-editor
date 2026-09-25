package com.retroeditor.service.tilemap;

public enum TilemapPlatform {
    GAMEBOY("Game Boy", "GB", 2),
    ZX_SPECTRUM("ZX Spectrum", "ZX", 1),
    CPC("Amstrad CPC", "CPC", 2),
    MASTER_SYSTEM("Sega Master System", "SMS", 2),
    GAME_GEAR("Game Gear", "GG", 4),
    ATARI("Atari 8-bit", "ATARI", 2),
    C64("Commodore 64", "C64", 2);

    private final String displayName;
    private final String codeName;
    private final int bitsPerPixel;

    TilemapPlatform(String displayName, String codeName, int bitsPerPixel) {
        this.displayName = displayName;
        this.codeName = codeName;
        this.bitsPerPixel = bitsPerPixel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCodeName() {
        return codeName;
    }

    public int getBitsPerPixel() {
        return bitsPerPixel;
    }

    public int getMaxTileIndex() {
        return 255;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
