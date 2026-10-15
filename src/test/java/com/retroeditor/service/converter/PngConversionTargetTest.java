package com.retroeditor.service.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.retroeditor.service.ProjectPlatformDetectionService.Platform;

class PngConversionTargetTest {

    @Test
    void mapsEveryDetectedPlatformToTheRightMachine() {
        assertEquals(Optional.of(PngConversionTarget.GAMEBOY), PngConversionTarget.fromDetectedPlatform(Platform.GAMEBOY));
        assertEquals(Optional.of(PngConversionTarget.ZX_SPECTRUM), PngConversionTarget.fromDetectedPlatform(Platform.SPECTRUM));
        assertEquals(Optional.of(PngConversionTarget.CPC), PngConversionTarget.fromDetectedPlatform(Platform.CPC));
        assertEquals(Optional.of(PngConversionTarget.C64), PngConversionTarget.fromDetectedPlatform(Platform.C64));
        assertEquals(Optional.of(PngConversionTarget.MASTER_SYSTEM), PngConversionTarget.fromDetectedPlatform(Platform.SMS));
        assertEquals(Optional.of(PngConversionTarget.GAME_GEAR), PngConversionTarget.fromDetectedPlatform(Platform.GAME_GEAR));
        assertEquals(Optional.of(PngConversionTarget.ATARI), PngConversionTarget.fromDetectedPlatform(Platform.ATARI));
        assertEquals(Optional.of(PngConversionTarget.MSX), PngConversionTarget.fromDetectedPlatform(Platform.MSX));
        assertEquals(Optional.of(PngConversionTarget.NES), PngConversionTarget.fromDetectedPlatform(Platform.NES));
        // La deteccion sin decision (o sin proyecto) debe dejar elegir al usuario.
        assertEquals(Optional.empty(), PngConversionTarget.fromDetectedPlatform(Platform.UNKNOWN));
        assertEquals(Optional.empty(), PngConversionTarget.fromDetectedPlatform(null));
    }

    @Test
    void coversExactlyTheNineSupportedMachines() {
        assertEquals(9, PngConversionTarget.values().length);
        for (PngConversionTarget target : PngConversionTarget.values()) {
            assertFalse(target.key().isBlank(), "clave i18n vacia en " + target);
        }
    }

    @Test
    void retroMachinesHaveADefaultVideoModeAndOwnMachinesDoNot() {
        assertEquals(RetroImageMode.CPC_MODE_1, PngConversionTarget.CPC.defaultRetroMode().orElseThrow());
        assertEquals(RetroImageMode.C64_BITMAP, PngConversionTarget.C64.defaultRetroMode().orElseThrow());
        assertEquals(RetroImageMode.MASTER_SYSTEM_TILES, PngConversionTarget.MASTER_SYSTEM.defaultRetroMode().orElseThrow());
        assertEquals(RetroImageMode.GAME_GEAR_TILES, PngConversionTarget.GAME_GEAR.defaultRetroMode().orElseThrow());
        assertEquals(RetroImageMode.ATARI_PLAYFIELD, PngConversionTarget.ATARI.defaultRetroMode().orElseThrow());
        assertEquals(RetroImageMode.MSX_TILES, PngConversionTarget.MSX.defaultRetroMode().orElseThrow());
        assertEquals(RetroImageMode.NES_TILES, PngConversionTarget.NES.defaultRetroMode().orElseThrow());

        assertTrue(PngConversionTarget.GAMEBOY.defaultRetroMode().isEmpty());
        assertTrue(PngConversionTarget.ZX_SPECTRUM.defaultRetroMode().isEmpty());
        assertFalse(PngConversionTarget.GAMEBOY.isRetro());
        assertFalse(PngConversionTarget.ZX_SPECTRUM.isRetro());
        assertTrue(PngConversionTarget.CPC.isRetro());
        assertTrue(PngConversionTarget.NES.isRetro());
    }
}