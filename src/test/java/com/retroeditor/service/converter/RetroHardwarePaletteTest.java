package com.retroeditor.service.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RetroHardwarePaletteTest {

    /** Los 4 niveles de 2 bits del TMS9918. */
    private static final int[] LEVEL2 = { 0, 85, 170, 255 };

    /** Los 8 niveles de 3 bits de la CRAM del Game Gear. */
    private static final int[] LEVEL3 = { 0, 36, 73, 109, 146, 182, 219, 255 };

    @Test
    void cpc16_UsesTheFirmwareInkTableWithoutDuplicates() {
        int[] palette = RetroHardwarePalette.cpc16();

        assertEquals(16, palette.length);
        assertEquals(0xFF000000, palette[0]);
        assertEquals(0xFF000080, palette[1], "tinta 1: azul");
        assertEquals(0xFF0000FF, palette[2], "tinta 2: azul brillante");
        assertEquals(0xFF800000, palette[3], "tinta 3: rojo");
        assertEquals(0xFFFF8000, palette[15], "tinta 15: naranja");
        assertEquals(16, distinctColours(palette), "Las 16 tintas tienen que ser colores distintos");
    }

    @Test
    void cpcModes_UseTheFirstInksOfTheTable() {
        int[] inks = RetroHardwarePalette.cpc16();

        assertEquals(0xFF000000, RetroHardwarePalette.cpcMode0()[0]);
        assertEquals(inks[14], RetroHardwarePalette.cpcMode0()[1], "la tinta mas clara de la tabla");
        assertEquals(inks[3], RetroHardwarePalette.cpcMode1()[3]);
        assertEquals(inks[0], RetroHardwarePalette.cpcMode1()[0]);
    }

    @Test
    void masterSystemCramByte_UsesTwoBitsPerChannelAsALevel() {
        int[] palette = RetroHardwarePalette.masterSystem();

        assertEquals(0x03, RetroHardwarePalette.masterSystemCramByte(palette[9]), "rojo 255 = %11");
        assertEquals(0x30, RetroHardwarePalette.masterSystemCramByte(palette[5]), "azul 255 = %11");
        assertEquals(0x08, RetroHardwarePalette.masterSystemCramByte(palette[2]), "verde 170 = %10");
        assertEquals(0x0C, RetroHardwarePalette.masterSystemCramByte(palette[3]), "verde 255 = %11");
        assertEquals(0x3F, RetroHardwarePalette.masterSystemCramByte(palette[15]), "blanco = %111111");
    }

    @Test
    void gameGearCramValue_PacksTwelveBitsInLittleEndianOrder() {
        int[] palette = RetroHardwarePalette.masterSystem();

        assertEquals(0x777, RetroHardwarePalette.gameGearCramValue(palette[15]), "blanco: 7/7/7");
        assertEquals(0x000, RetroHardwarePalette.gameGearCramValue(palette[0]));
        assertEquals(0x007, RetroHardwarePalette.gameGearCramValue(palette[9]), "rojo claro: R=7");
        assertEquals(0x700, RetroHardwarePalette.gameGearCramValue(palette[5]), "azul claro: B=7");
        assertTrue(RetroHardwarePalette.gameGearCramValue(palette[2]) < 0x1000, "Cabe en 12 bits");
    }

    @Test
    void msxPalette_IsTheSixteenColourTms9918Table() {
        int[] palette = RetroHardwarePalette.msx();

        assertEquals(16, palette.length);
        assertEquals(0xFF000000, palette[0], "transparente de sprites = negro");
        assertEquals(0xFF00AA00, palette[2], "verde medio");
        assertEquals(0xFF0000FF, palette[5], "azul claro");
        assertEquals(0xFF00FF00, palette[3], "verde claro");
        assertEquals(0xFFFFFFFF, palette[15], "blanco");
        assertEquals(15, distinctColours(palette), "solo se repiten el 0 y el 1 (negro)");
    }

    @Test
    void msxCramByte_UsesTheSameSixBitLayoutAsTheVdp() {
        int[] palette = RetroHardwarePalette.msx();

        assertEquals(0x03, RetroHardwarePalette.msxCramByte(palette[9]), "rojo 255 = %11");
        assertEquals(0x30, RetroHardwarePalette.msxCramByte(palette[5]), "azul 255 = %11");
        assertEquals(0x3F, RetroHardwarePalette.msxCramByte(palette[15]), "blanco = %111111");
        for (int i = 0; i < palette.length; i++) {
            assertEquals(palette[i], fromCram2(RetroHardwarePalette.msxCramByte(palette[i])),
                    "El byte de CRAM del color " + i + " no devuelve el mismo color");
        }
    }

    @Test
    void nesPalette_HasTheSixtyFourPpuEntriesWithBlackedOutTail() {
        int[] palette = RetroHardwarePalette.nes();

        assertEquals(64, palette.length);
        assertEquals(0xFF7C7C7C, palette[0x00], "gris oscuro");
        assertEquals(0xFFF8F8F8, palette[0x20], "gris claro");
        assertEquals(0xFFFCFCFC, palette[0x30], "blanco");
        assertEquals(0xFF0078F8, palette[0x11], "azul brillante");
        assertEquals(0xFFFCA044, palette[0x27], "naranja");
        assertEquals(0xFF787878, palette[0x2D], "gris medio del renglon 2");
        assertEquals(0xFFF8D8F8, palette[0x3D], "tono claro del renglon 3");
        for (int index : new int[] { 0x0D, 0x0E, 0x0F, 0x1D, 0x1E, 0x1F, 0x2E, 0x2F, 0x3E, 0x3F }) {
            assertEquals(0xFF000000, palette[index], "el indice " + index + " es el negro de renglon");
        }
        assertEquals(55, distinctColours(palette), "64 entradas menos los 9 negros repetidos");
    }

    @Test
    void c64Palette_MatchesTheTileEditorPalette() {
        int[] palette = RetroHardwarePalette.c64();

        assertEquals(16, palette.length);
        assertEquals(0xFF000000, palette[0]);
        assertEquals(0xFF68372B, palette[2], "marrun del VIC-II");
        assertEquals(0xFF959595, palette[15], "gris claro del VIC-II");
    }

    @Test
    void atariPalettes_ShareTheGreysOfTheGraphicsModes() {
        int[] sixteen = RetroHardwarePalette.atari16();
        int[] four = RetroHardwarePalette.atari4();

        assertEquals(4, four.length);
        assertEquals(16, sixteen.length);
        for (int i = 0; i < four.length; i++) {
            assertEquals(sixteen[i], four[i], "Los cuatro primeros registros GTIA son los de graphics 0/1");
        }
        assertNotEquals(sixteen[0], sixteen[5], "El negro y el blanco tienen que ser distintos");
        assertEquals(16, distinctColours(sixteen));
    }

    @Test
    void everyColourOfAPaletteQuantisesToItsOwnIndex() {
        for (RetroImageMode mode : RetroImageMode.values()) {
            int[] palette = mode.palette();
            if (mode.hasTransparentFirstColor()) {
                continue;
            }
            if (distinctColours(palette) < palette.length) {
                // Las paletas con colores repetidos (la del NES repite el negro
                // de los indices 0x0D-0x3F) no pueden devolverse a si mismas.
                continue;
            }
            for (int i = 0; i < palette.length; i++) {
                int[] pixel = { 0xFF000000 | palette[i] };
                int index = RetroQuantizer.quantize(pixel, palette, false)[0];
                assertEquals(i, index,
                        "El color " + i + " de " + mode + " se cuantiza sobre si mismo o la paleta no es utilizable");
            }
        }
    }

    @Test
    void masterSystemCramValuesReproduceThePaletteColours() {
        int[] palette = RetroHardwarePalette.masterSystem();

        for (int i = 0; i < palette.length; i++) {
            assertEquals(palette[i], fromCram2(RetroHardwarePalette.masterSystemCramByte(palette[i])),
                    "El byte de CRAM del color " + i + " no devuelve el mismo color");
        }
    }

    @Test
    void gameGearCramValues_AreDistinctAndStayOnTheNearestLevel() {
        int[] palette = RetroHardwarePalette.gameGear();
        Set<Integer> words = new HashSet<>();

        for (int i = 0; i < palette.length; i++) {
            int word = RetroHardwarePalette.gameGearCramValue(palette[i]);
            words.add(word);
            int decoded = ((LEVEL3[word & 0x07] << 16) | (LEVEL3[(word >> 4) & 0x07] << 8) | LEVEL3[(word >> 8) & 0x07]) & 0xFFFFFF;
            assertEquals(nearestColour3(palette[i]) & 0xFFFFFF, decoded,
                    "El color " + i + " deberia quedarse en el nivel de 3 bits mas cercano de cada canal");
        }
        assertEquals(15, words.size(),
                "Solo se repiten el color 0 y el 1, que son el mismo negro por el color transparente del sprite");
        assertEquals(RetroHardwarePalette.gameGearCramValue(palette[0]),
                RetroHardwarePalette.gameGearCramValue(palette[1]),
                "El 0 transparente y el 1 negro comparten palabra de CRAM");
    }

    @Test
    void channelsAlwaysGoToTheNearestLevel() {
        for (int value = 0; value <= 0xFF; value++) {
            int grey = grey(value);

            assertEquals(grey(LEVEL2[nearest(LEVEL2, value)]), fromCram2(RetroHardwarePalette.masterSystemCramByte(grey)),
                    "El gris " + value + " deberia ir al nivel de 2 bits mas cercano");

            int word = RetroHardwarePalette.gameGearCramValue(grey);
            assertEquals(grey(LEVEL3[nearest(LEVEL3, value)]), nearestColour3(grey),
                    "El gris " + value + " deberia ir al nivel de 3 bits mas cercano");
        }
    }

    private static int nearestColour3(int argb) {
        return 0xFF000000
                | (LEVEL3[nearest(LEVEL3, (argb >> 16) & 0xFF)] << 16)
                | (LEVEL3[nearest(LEVEL3, (argb >> 8) & 0xFF)] << 8)
                | LEVEL3[nearest(LEVEL3, argb & 0xFF)];
    }

    private static int grey(int level) {
        return 0xFF000000 | (level << 16) | (level << 8) | level;
    }

    /** Nivel mas cercano, y en caso de empate el mas alto. */
    private static int nearest(int[] levels, int value) {
        int best = 0;
        for (int level = 1; level < levels.length; level++) {
            if (Math.abs(levels[level] - value) <= Math.abs(levels[best] - value)) {
                best = level;
            }
        }
        return best;
    }

    /** Vuelve de un byte %00BBGGRR a los niveles de 2 bits. */
    private static int fromCram2(int cram) {
        return 0xFF000000
                | (LEVEL2[cram & 0x03] << 16)
                | (LEVEL2[(cram >> 2) & 0x03] << 8)
                | (LEVEL2[(cram >> 4) & 0x03]);
    }

    private static int distinctColours(int[] palette) {
        Set<Integer> values = new HashSet<>();
        for (int colour : palette) {
            values.add(colour & 0xFFFFFF);
        }
        return values.size();
    }
}
