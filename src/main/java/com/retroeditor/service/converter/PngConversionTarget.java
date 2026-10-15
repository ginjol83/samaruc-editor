package com.retroeditor.service.converter;

import java.util.Optional;

import com.retroeditor.service.ProjectPlatformDetectionService.Platform;

/**
 * Maquinas a las que la herramienta "Convertir PNG a C" puede generar codigo.
 *
 * Agrupa las 9 plataformas cubiertas por los conversores existentes: Game Boy,
 * ZX Spectrum y la familia Retro (CPC, C64, Master System, Game Gear, Atari,
 * MSX, NES). Cada maquina sabe si le corresponde el conversor Retro y, en ese
 * caso, cual es el modo de video por defecto razonable.
 */
public enum PngConversionTarget {
    GAMEBOY("gameboy"),
    ZX_SPECTRUM("zxspectrum"),
    CPC("cpc", RetroImageMode.CPC_MODE_1),
    C64("c64", RetroImageMode.C64_BITMAP),
    MASTER_SYSTEM("mastersystem", RetroImageMode.MASTER_SYSTEM_TILES),
    GAME_GEAR("gamegear", RetroImageMode.GAME_GEAR_TILES),
    ATARI("atari", RetroImageMode.ATARI_PLAYFIELD),
    MSX("msx", RetroImageMode.MSX_TILES),
    NES("nes", RetroImageMode.NES_TILES);

    private final String key;
    private final RetroImageMode defaultRetroMode;

    PngConversionTarget(String key) {
        this(key, null);
    }

    PngConversionTarget(String key, RetroImageMode defaultRetroMode) {
        this.key = key;
        this.defaultRetroMode = defaultRetroMode;
    }

    /**
     * Clave i18n residual para la etiqueta de la maquina: la interfaz la traduce
     * como {@code tool.pngToC.platform.<key>}.
     */
    public String key() {
        return key;
    }

    /**
     * Modo de video por defecto cuando la maquina usa el conversor Retro.
     * Vacio para Game Boy y ZX Spectrum, que tienen conversor propio.
     */
    public Optional<RetroImageMode> defaultRetroMode() {
        return Optional.ofNullable(defaultRetroMode);
    }

    /** {@code true} si la maquina se convierte con la familia Retro. */
    public boolean isRetro() {
        return defaultRetroMode != null;
    }

    /**
     * Mapea la plataforma detectada del proyecto abierto a la maquina de
     * conversion. {@code UNKNOWN} (o {@code null}) devuelve vacio: la herramienta
     * deja entonces que el usuario elija la maquina manualmente.
     */
    public static Optional<PngConversionTarget> fromDetectedPlatform(Platform platform) {
        if (platform == null) {
            return Optional.empty();
        }
        return switch (platform) {
            case GAMEBOY -> Optional.of(GAMEBOY);
            case SPECTRUM -> Optional.of(ZX_SPECTRUM);
            case CPC -> Optional.of(CPC);
            case C64 -> Optional.of(C64);
            case SMS -> Optional.of(MASTER_SYSTEM);
            case GAME_GEAR -> Optional.of(GAME_GEAR);
            case ATARI -> Optional.of(ATARI);
            case MSX -> Optional.of(MSX);
            case NES -> Optional.of(NES);
            case UNKNOWN -> Optional.empty();
        };
    }
}