package com.retroeditor.service.gameboy;

/**
 * Tipos de codificación de texto encontrados en ROMs de Game Boy.
 */
public enum TextEncoding {

    /** Cada byte es un índice de tile en la tabla de fuentes del juego. */
    CUSTOM_TILE_TABLE("Tile table personalizada"),

    /** Texto terminado por byte sentinela 0x50 (común en Pokémon, Dragon Quest). */
    SENTINEL_0x50("Sentinela 0x50 (Pokémon/DQ)"),

    /** Texto terminado por byte sentinela 0xFF. */
    SENTINEL_0xFF("Sentinela 0xFF"),

    /** Texto terminado por byte sentinela 0x00. */
    SENTINEL_0x00("Sentinela 0x00"),

    /** Longitud fija por entrada (configurable). */
    FIXED_LENGTH("Longitud fija"),

    /** Texto en Shift-JIS (poco común en GB, mais en GBC). */
    SHIFT_JIS("Shift-JIS");

    private final String description;

    TextEncoding(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
