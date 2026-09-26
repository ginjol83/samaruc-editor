package com.retroeditor.service.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RetroQuantizerTest {
    @Test
    void nearestIndex_PicksClosestPaletteEntry() {
        int[] palette = RetroHardwarePalette.cpc16();

        assertEquals(0, RetroQuantizer.nearestIndex(0xFF101010, palette));
        assertEquals(6, RetroQuantizer.nearestIndex(0xFFEE0000, palette));
        assertEquals(14, RetroQuantizer.nearestIndex(0xFFFEFEFE, palette), "El CPC no tiene un blanco puro");
        assertEquals(14, RetroQuantizer.nearestIndex(0xFFFFFFFF, palette), "El azul pastel es lo mas cercano al blanco");
    }

    @Test
    void quantize_WithTransparentFirstColor_ForcesAlphaToZero() {
        int[] palette = RetroHardwarePalette.masterSystem();
        int[] pixels = { 0x00FFFFFF, 0xFFFF0000, 0x7F00FF00 };

        int[] indices = RetroQuantizer.quantize(pixels, palette, true);

        assertEquals(0, indices[0], "El pixel totalmente transparente debe ir al indice 0");
        assertEquals(9, indices[1], "El rojo puro es el indice 9 de la paleta del Master System");
        assertEquals(0, indices[2], "El alfa por debajo de 128 cuenta como transparente");
    }

    @Test
    void mostUsedIndices_KeepsMostFrequentOnes() {
        int[] indices = { 0, 0, 0, 0, 5, 5, 9, 2 };

        int[] selected = RetroQuantizer.mostUsedIndices(indices, 16, 3);

        assertEquals(3, selected.length);
        assertEquals(0, selected[0]);
        assertEquals(2, selected[1], "El indice 2 aparece una vez, el 9 tambien: gana el menor");
        assertEquals(5, selected[2]);
    }

    @Test
    void mostUsedOrdered_SkipsUnusedColorsAndKeepsFrequencyOrder() {
        int[] indices = { 8, 8, 8, 5, 1 };

        int[] ordered = RetroQuantizer.mostUsedOrdered(indices, 16, 4);

        assertEquals(3, ordered.length, "Solo se devuelven los colores usados");
        assertEquals(8, ordered[0], "El mas frecuente es el fondo");
        assertEquals(1, ordered[1], "Empate entre 5 y 1: gana el menor");
        assertEquals(5, ordered[2]);
    }

    @Test
    void remap_RenumbersToZeroBasedRange() {
        int[] remapped = RetroQuantizer.remap(new int[] { 8, 1, 5, 8, 1 }, new int[] { 1, 8 });

        assertEquals(1, remapped[0]);
        assertEquals(0, remapped[1]);
        assertEquals(0, remapped[2], "Los indices fuera del subconjunto caen en el primero");
        assertEquals(1, remapped[3]);
        assertEquals(0, remapped[4]);
    }

    @Test
    void selectColors_ReturnsPaletteEntriesInOrder() {
        int[] palette = RetroHardwarePalette.c64();

        int[] colors = RetroQuantizer.selectColors(palette, new int[] { 15, 6 });

        assertEquals(2, colors.length);
        assertEquals(palette[15], colors[0]);
        assertEquals(palette[6], colors[1]);
    }

    @Test
    void nearestIndex_WithEmptyPalette_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RetroQuantizer.nearestIndex(0xFFFFFFFF, new int[0]));

        assertTrue(error.getMessage().contains("paleta"));
    }
}
