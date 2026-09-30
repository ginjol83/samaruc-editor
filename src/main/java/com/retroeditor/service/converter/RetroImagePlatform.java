package com.retroeditor.service.converter;

/**
 * Plataformas 8-bit cubiertas por el conversor PNG a C.
 *
 * Solo incluye las máquinas para las que no existe un conversor dedicado:
 * CPC, C64, Master System, Game Gear, Atari 8-bit, MSX y NES. Game Boy y ZX
 * Spectrum tienen sus propios conversores en {@code service.gameboy} y
 * {@code service.zxspectrum}.
 */
public enum RetroImagePlatform {
    CPC("Amstrad CPC", "CPC"),
    C64("Commodore 64", "C64"),
    MASTER_SYSTEM("Sega Master System", "SMS"),
    GAME_GEAR("Game Gear", "GG"),
    ATARI("Atari 8-bit", "ATARI"),
    MSX("MSX", "MSX"),
    NES("Nintendo NES", "NES");

    private final String displayName;
    private final String codeName;

    RetroImagePlatform(String displayName, String codeName) {
        this.displayName = displayName;
        this.codeName = codeName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCodeName() {
        return codeName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
