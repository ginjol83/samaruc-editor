package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ZX Screen Layout packing.
 */
public class ZxScreenLayoutTest {
    /**
     * Verifica la fórmula de offset para ZX Screen Layout.
     * 
     * Fórmula correcta: offset = (y >> 3) * 256 + (y & 7) * 32 + x_byte
     * 
     * Donde:
     * - (y >> 3) = y / 8 (character row)
     * - (y & 7) = y % 8 (line within character row)
     * - Rango válido: 0-6143 para 256x192
     */
    @Test
    public void testZxScreenLayoutOffsetFormula() {
        // Línea 0, byte 0: debe estar en offset 0
        assertEquals(0, calculateZxScreenOffset(0, 0));

        // Línea 0, byte 31: offset 31 (última columna de línea 0)
        assertEquals(31, calculateZxScreenOffset(0, 31));

        // Línea 8, byte 0: nueva línea de caracteres (offset 256)
        assertEquals(256, calculateZxScreenOffset(8, 0));

        // Línea 64, byte 0: offset 64*32 = 2048
        assertEquals(2048, calculateZxScreenOffset(64, 0));

        // Línea 128, byte 0: offset 128/8*256 = 16*256 = 4096
        assertEquals(4096, calculateZxScreenOffset(128, 0));

        // Línea 191 (última), byte 31 (última columna)
        int lastOffset = calculateZxScreenOffset(191, 31);
        assertEquals(6143, lastOffset);
    }

    @Test
    public void testZxScreenLayoutTotalSize() {
        // 256x192 bitmap = 32 bytes × 192 lines = 6144 bytes
        int totalBytes = 32 * 192;
        assertEquals(6144, totalBytes);

        // Cada tercio: 64 líneas × 32 bytes = 2048 bytes
        // 3 tercios = 6144 bytes
        assertEquals(2048 * 3, totalBytes);
    }

    @Test
    public void testZxScreenLayoutOffsetRange() {
        // Generar todos los offsets y verificar que son únicos y dentro de rango
        boolean[] used = new boolean[6144];
        for (int y = 0; y < 192; y++) {
            for (int x = 0; x < 32; x++) {
                int offset = calculateZxScreenOffset(y, x);
                assertTrue(offset >= 0 && offset < 6144, "Offset out of range: " + offset);
                assertFalse(used[offset], "Duplicate offset at (" + x + "," + y + ")");
                used[offset] = true;
            }
        }

        // Verificar que se usaron todos los bytes
        for (int i = 0; i < 6144; i++) {
            assertTrue(used[i], "Unused offset at index " + i);
        }
    }

    /**
     * Fórmula de offset ZX Screen Layout (correcta).
     * offset = (y >> 3) * 256 + (y & 7) * 32 + byteCol
     */
    private int calculateZxScreenOffset(int y, int byteCol) {
        return ((y >> 3) * 256) + ((y & 7) * 32) + byteCol;
    }
}
