package com.retroeditor.service.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class RetroImagePackerTest {
    @Test
    void packLinear_ForOneBitMode_PacksEightPixelsPerByte() {
        int[] indices = { 1, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0 };

        byte[] packed = RetroImagePacker.packLinear(RetroImageMode.CPC_MODE_0, indices, 8, 2);

        assertEquals(2, packed.length);
        assertEquals((byte) 0xD1, packed[0], "1101 0001: el pixel de la izquierda en el bit alto");
        assertEquals((byte) 0x00, packed[1]);
    }

    @Test
    void packLinear_ForFourBitMode_UsesNibblePairsWithLeftPixelInHighNibble() {
        int[] indices = { 0, 15, 1, 2 };

        byte[] packed = RetroImagePacker.packLinear(RetroImageMode.CPC_MODE_2, indices, 4, 1);

        assertEquals(2, packed.length);
        assertEquals((byte) 0x0F, packed[0]);
        assertEquals((byte) 0x12, packed[1]);
    }

    @Test
    void packGti7_UsesPriorThreeInHighNibble() {
        int[] indices = { 0, 3, 1, 2, 0, 0, 0, 0 };

        byte[] packed = RetroImagePacker.packGti7(indices, 8, 1);

        assertEquals(4, packed.length);
        assertEquals((byte) 0xCC, packed[0], "PRIOR 3 con color 0 a la izquierda y color 3 a la derecha");
        assertEquals((byte) 0xC9, packed[1], "PRIOR 3 con color 1 a la izquierda y color 2 a la derecha");
        assertEquals((byte) 0xC0, packed[2], "Dos pixeles del color 0");
        assertEquals((byte) 0xC0, packed[3], "Dos pixeles del color 0");
    }

    @Test
    void packTilePlanar_WritesLowAndHighPlanePerColumn() {
        int[] indices = new int[64];
        for (int i = 0; i < 64; i++) {
            indices[i] = 3;
        }
        indices[0] = 1;

        byte[] packed = RetroImagePacker.packTilePlanar(indices, 8, 8);

        assertEquals(32, packed.length, "Un tile 8x8 de 4bpp ocupa 32 bytes");
        assertEquals((byte) 0xFF, packed[0], "Plano bajo de la columna 0: todos los pixeles son impares");
        assertEquals((byte) 0x7F, packed[1], "Plano alto: solo el pixel superior tiene el bit 1 a 0");
        assertEquals((byte) 0xFF, packed[2], "Columna 1 completa con color 3");
        assertEquals((byte) 0xFF, packed[3]);
    }

    @Test
    void packNES_SplitsTilesAttributesAndPaletteRam() {
        RetroImageMode mode = RetroImageMode.NES_TILES;
        int width = 16;
        int height = 16;
        int[] indices = new int[width * height];
        Arrays.fill(indices, 0);
        indices[0] = 3;
        int[] palette = { RetroHardwarePalette.nes()[0], RetroHardwarePalette.nes()[1],
                RetroHardwarePalette.nes()[2], RetroHardwarePalette.nes()[3] };
        int[] hardwareIndices = { 0x00, 0x01, 0x02, 0x03 };

        List<RetroImageLayer> layers = RetroImagePacker.pack(mode, indices, palette, hardwareIndices, width, height);

        assertEquals(3, layers.size());
        assertEquals("tiles", layers.get(0).name());
        assertEquals("attributes", layers.get(1).name());
        assertEquals("palette", layers.get(2).name());
        assertEquals(64, layers.get(0).data().length, "4 tiles de 8x8 a 16 bytes cada uno");
        assertEquals(1, layers.get(1).data().length, "Una celda de 16x16");
        assertEquals(16, layers.get(2).data().length, "4 subpaletas de 4 colores");
        // El pixel (0,0) usa el color 3 (entrada 0x03), que tras el mas usado (0x00)
        // ocupa el slot 1 de la subpaleta 0: bit 0 a 1, bit 1 a 0.
        assertEquals((byte) 0x80, layers.get(0).data()[0], "Plano 0: el bit alto de la fila 0");
        assertEquals((byte) 0x00, layers.get(0).data()[8], "Plano 1: el slot 1 solo tiene el bit 0");
        assertEquals((byte) 0x00, layers.get(1).data()[0], "La celda usa la subpaleta 0");
        assertEquals((byte) 0x00, layers.get(2).data()[0]);
        assertEquals((byte) 0x03, layers.get(2).data()[1], "El color de la entrada 3 se guarda en el slot 1");
        assertEquals((byte) 0xFF, layers.get(2).data()[2], "El resto de la paleta RAM queda sin usar");
        assertEquals((byte) 0xFF, layers.get(2).data()[15]);
    }

    @Test
    void packNES_ReusesASubpaletteWhoseColoursAlreadyFit() {
        RetroImageMode mode = RetroImageMode.NES_TILES;
        int width = 16;
        int height = 32;
        int[] indices = new int[width * height];
        // La primera mitad usa los colores 0-3 y la segunda solo 0-1.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                indices[y * width + x] = y < 16 ? ((x / 2) + y) % 4 : x % 2;
            }
        }
        int[] palette = { RetroHardwarePalette.nes()[0], RetroHardwarePalette.nes()[1],
                RetroHardwarePalette.nes()[2], RetroHardwarePalette.nes()[3] };
        int[] hardwareIndices = { 0x00, 0x01, 0x02, 0x03 };

        List<RetroImageLayer> layers = RetroImagePacker.pack(mode, indices, palette, hardwareIndices, width, height);

        int[] attributes = new int[layers.get(1).data().length];
        for (int i = 0; i < attributes.length; i++) {
            attributes[i] = layers.get(1).data()[i] & 0x03;
        }
        assertEquals(0, attributes[0], "La primera celda se queda con la subpaleta 0");
        assertEquals(0, attributes[1], "Los colores 0-1 ya estaban en la subpaleta 0 y se reutilizan");
        assertEquals(8, layers.get(0).data().length / 16, "2 tiles por fila y 4 filas");
        assertEquals(2, attributes.length, "16x32 son dos celdas de 16x16");
    }

    @Test
    void packC64Bitmap_SplitsPixelesScreenAndColorRam() {
        int[] hardwareIndices = identity(16);
        int[] indices = new int[8 * 8];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = 2;
        }
        indices[0] = 6;

        List<RetroImageLayer> layers = RetroImagePacker.packC64Bitmap(indices, hardwareIndices, 8, 8);

        assertEquals(3, layers.size());
        assertEquals("bitmap", layers.get(0).name());
        assertEquals("screen", layers.get(1).name());
        assertEquals("colors", layers.get(2).name());
        assertEquals(16, layers.get(0).data().length, "2bpp: 4 pixeles por byte");
        assertEquals(1, layers.get(1).data().length, "1 byte de pantalla por celda de 8x8");
        assertEquals(1, layers.get(2).data().length, "1 byte de color RAM por celda de 8x8");
        assertEquals((byte) 0x40, layers.get(0).data()[0], "El pixel 0 es el unico de primer plano (01)");
        assertEquals((byte) 0x00, layers.get(0).data()[1]);
        assertEquals((byte) 0x66, layers.get(1).data()[0], "Solo se usa el color 6, en los dos nibbles");
        assertEquals((byte) 0x06, layers.get(2).data()[0]);
    }

    @Test
    void packC64Bitmap_SharesOneGlobalBackgroundBetweenCells() {
        int[] hardwareIndices = identity(16);
        int width = 16;
        int height = 8;
        int[] indices = new int[width * height];
        // Mitad izquierda de color 2 y mitad derecha de color 9.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                indices[y * width + x] = x < 8 ? 2 : 9;
            }
        }
        indices[0] = 6;
        indices[1] = 6;
        indices[8] = 4;

        List<RetroImageLayer> layers = RetroImagePacker.packC64Bitmap(indices, hardwareIndices, width, height);

        assertEquals(9, RetroImagePacker.c64BitmapBackgroundPosition(indices, hardwareIndices),
                "El fondo global es el color mas usado de toda la imagen");
        byte[] bitmap = layers.get(0).data();
        byte[] screen = layers.get(1).data();
        byte[] colors = layers.get(2).data();
        // Celda 0: 2 pasa a ser el nibble alto (01) porque el fondo global es 9.
        assertEquals((byte) 0xA5, bitmap[0], "6 = 10 y 2 = 01 en los cuatro primeros pixeles");
        assertEquals((byte) 0x55, bitmap[1], "El resto de la fila es todo color 2 = 01");
        assertEquals((byte) ((2 << 4) | 6), screen[0]);
        assertEquals((byte) 6, colors[0]);
        // Celda 1: 9 es el fondo global (00) y 4 el nibble alto (01).
        assertEquals((byte) 0x40, bitmap[2], "4 = 01 y el resto 9 = 00");
        assertEquals((byte) 0x00, bitmap[3]);
        assertEquals((byte) ((4 << 4) | 4), screen[1]);
        assertEquals((byte) 4, colors[1]);
    }

    @Test
    void packC64Charset_UsesTheColourOfEachCharacter() {
        int[] hardwareIndices = identity(16);
        int width = 16;
        int height = 8;
        int[] indices = new int[width * height];
        // Mitad izquierda de color 1 y mitad derecha de color 12.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                indices[y * width + x] = x < 8 ? 1 : 12;
            }
        }
        indices[0] = 3;
        indices[8] = 5;

        List<RetroImageLayer> layers = RetroImagePacker.packC64Charset(indices, hardwareIndices, width, height);

        byte[] chars = layers.get(0).data();
        byte[] colors = layers.get(1).data();
        assertEquals((byte) 0x80, chars[0], "Primer caracter: solo el pixel 0 es primer plano");
        assertEquals((byte) ((3 << 4) | 1), colors[0]);
        // El segundo caracter usa sus propios colores, no los del primero.
        assertEquals((byte) 0x80, chars[8], "Segundo caracter: el pixel 0 de esa celda");
        assertEquals((byte) ((5 << 4) | 12), colors[1]);
    }

    @Test
    void packC64Charset_UsesTwoMostUsedColorsPerCharacter() {
        int[] hardwareIndices = identity(16);
        int[] indices = new int[8 * 8];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = 2;
        }
        indices[0] = 6;

        List<RetroImageLayer> layers = RetroImagePacker.packC64Charset(indices, hardwareIndices, 8, 8);

        assertEquals(2, layers.size());
        assertEquals(8, layers.get(0).data().length);
        assertEquals(1, layers.get(1).data().length);
        assertEquals((byte) 0x80, layers.get(0).data()[0], "Solo el primer pixel de la fila 0 es primer plano");
        assertEquals((byte) ((6 << 4) | 2), layers.get(1).data()[0], "Nibble alto = primer plano 6, bajo = fondo 2");
    }

    @Test
    void packMsxTiles_SplitsPatternsNameTableAndColourTable() {
        int[] hardwareIndices = identity(16);
        int[] indices = new int[8 * 8];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = 3;
        }
        indices[0] = 11;

        List<RetroImageLayer> layers = RetroImagePacker.packMsxTiles(indices, hardwareIndices, 8, 8);

        assertEquals(3, layers.size());
        assertEquals("patterns", layers.get(0).name());
        assertEquals(8, layers.get(0).data().length, "SCREEN 1: 8 bytes de 1bpp por patron");
        assertEquals((byte) 0x80, layers.get(0).data()[0], "Solo el pixel raro es primer plano");
        assertEquals("map", layers.get(1).name());
        assertEquals(1, layers.get(1).data().length, "Un byte por celda de la tabla de nombres");
        assertEquals((byte) 0, layers.get(1).data()[0], "La unica celda usa el patron 0");
        assertEquals("colors", layers.get(2).name());
        assertEquals(1, layers.get(2).data().length, "Un byte por cada 8 caracteres, no uno por celda");
        assertEquals((byte) ((11 << 4) | 3), layers.get(2).data()[0],
                "Nibble alto = primer plano 11, bajo = fondo 3");
    }

    @Test
    void packMsxTiles_SharesThePatternOfRepeatedBlocks() {
        int[] hardwareIndices = identity(16);
        int[] indices = new int[16 * 8];
        java.util.Arrays.fill(indices, 2);
        // La segunda celda lleva el pixel en otro sitio, asi que es otro patron.
        indices[8] = 5;

        List<RetroImageLayer> layers = RetroImagePacker.packMsxTiles(indices, hardwareIndices, 16, 8);

        assertEquals(16, layers.get(0).data().length, "Dos celdas pero dos patrones distintos de 8 bytes");
        assertEquals(2, layers.get(1).data().length, "La tabla de nombres tiene una entrada por celda");
        assertEquals(1, layers.get(2).data().length, "Los dos patrones caben en un grupo de 8");
    }

    @Test
    void packMsxTiles_PutsTheCharactersOfOneColourInTheSameColourByte() {
        int[] hardwareIndices = identity(16);
        int cells = 16;
        int width = cells * 8;
        int[] indices = new int[width * 8];
        java.util.Arrays.fill(indices, 1);
        for (int cell = 0; cell < cells; cell++) {
            // 8 patrones con un pixel de color 6 y 8 con un pixel de color 3:
            // 16 patrones distintos repartidos en dos colores.
            int row = cell % 8;
            int column = cell < 8 ? 0 : 1;
            indices[row * width + cell * 8 + column] = cell < 8 ? 6 : 3;
        }

        List<RetroImageLayer> layers = RetroImagePacker.packMsxTiles(indices, hardwareIndices, width, 8);

        assertEquals(cells * 8, layers.get(0).data().length, "16 patrones distintos de 8 bytes");
        assertEquals(cells, layers.get(1).data().length, "Una entrada de la tabla de nombres por celda");
        assertEquals(2, layers.get(2).data().length, "16 caracteres = 2 bytes de tabla de color");
        assertEquals((byte) ((3 << 4) | 1), layers.get(2).data()[0],
                "Los patrones se reordenan por color, asi que el grupo 0 es el del color mas bajo");
        assertEquals((byte) ((6 << 4) | 1), layers.get(2).data()[1]);
    }

    @Test
    void packMsxTiles_RejectsImagesWithMoreThan256Patterns() {
        int[] hardwareIndices = identity(16);

        int fits = RetroImageMode.MSX_SCREEN1_PATTERNS;
        assertEquals(fits, RetroImagePacker.packMsxTiles(distinctCells(fits), hardwareIndices, fits * 8, 8)
                .get(0).data().length / 8, "Con 256 patrones distintos todavia cabe");

        int overflows = fits + 1;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RetroImagePacker.packMsxTiles(distinctCells(overflows), hardwareIndices, overflows * 8, 8));

        assertTrue(error.getMessage().contains("256 patrones"), error.getMessage());
    }

    /**
     * Fila de celdas 8x8 en la que ninguna se repite: los 8 bits del numero de
     * celda se ponen en las 8 filas del patron (un solo pixel por fila, para no
     * empatar con el fondo), y la celda 256, que no cabe en 8 bits, lleva un
     * unico pixel en la fila 0.
     */
    private static int[] distinctCells(int cells) {
        int width = cells * 8;
        int[] pixels = new int[width * 8];
        java.util.Arrays.fill(pixels, 1);
        for (int cell = 0; cell < cells; cell++) {
            for (int row = 0; row < 8; row++) {
                for (int column = 0; column < 8; column++) {
                    boolean on = cell < RetroImageMode.MSX_SCREEN1_PATTERNS
                            ? ((cell >> row) & 1) == 1 && column == 4
                            : row == 0 && column == 0;
                    pixels[row * width + cell * 8 + column] = on ? 2 : 1;
                }
            }
        }
        return pixels;
    }

    @Test
    void msxColourTableLength_IsOneBytePerEightCharacters() {
        assertEquals(0, RetroImagePacker.msxColourTableLength(0));
        assertEquals(1, RetroImagePacker.msxColourTableLength(1));
        assertEquals(1, RetroImagePacker.msxColourTableLength(8));
        assertEquals(2, RetroImagePacker.msxColourTableLength(9));
        assertEquals(32, RetroImagePacker.msxColourTableLength(256));
    }

    private static int[] identity(int length) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = i;
        }
        return values;
    }

    @Test
    void pack_WithWrongIndexCount_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RetroImagePacker.pack(RetroImageMode.CPC_MODE_0, new int[3], new int[] { 0xFF000000 },
                        new int[] { 0 }, 8, 1));

        assertTrue(error.getMessage().contains("numero de indices"));
    }

    @Test
    void pack_WithPaletteWithoutHardwareIndices_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RetroImagePacker.pack(RetroImageMode.CPC_MODE_1, new int[16],
                        new int[] { 0xFF000000, 0xFF000000 }, new int[] { 0 }, 8, 2));

        assertTrue(error.getMessage().contains("indice de hardware"));
    }

    @Test
    void validateDimensions_ForTileModes_RejectsUnalignedSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> RetroImageMode.MASTER_SYSTEM_TILES.validateDimensions(12, 8));
        assertThrows(IllegalArgumentException.class,
                () -> RetroImageMode.MSX_TILES.validateDimensions(8, 6));
        assertThrows(IllegalArgumentException.class,
                () -> RetroImageMode.NES_TILES.validateDimensions(10, 8));
        assertThrows(IllegalArgumentException.class,
                () -> RetroImageMode.C64_CHARSET.validateDimensions(16, 4));
        assertThrows(IllegalArgumentException.class,
                () -> RetroImageMode.C64_BITMAP.validateDimensions(8, 12));
        assertThrows(IllegalArgumentException.class,
                () -> RetroImageMode.CPC_MODE_1.validateDimensions(6, 8));
    }

    @Test
    void layerNames_MatchTheGeneratedLayers() {
        assertEquals(List.of("hero_chars", "hero_colors"),
                RetroImagePacker.layerNames(RetroImageMode.C64_CHARSET, "hero"));
        assertEquals(List.of("hero_bitmap", "hero_screen", "hero_colors"),
                RetroImagePacker.layerNames(RetroImageMode.C64_BITMAP, "hero"));
        assertEquals(List.of("hero_patterns", "hero_map", "hero_colors"),
                RetroImagePacker.layerNames(RetroImageMode.MSX_TILES, "hero"));
        assertEquals(List.of("hero", "hero_attributes", "hero_palette"),
                RetroImagePacker.layerNames(RetroImageMode.NES_TILES, "hero"));
        assertEquals(List.of("hero"),
                RetroImagePacker.layerNames(RetroImageMode.CPC_MODE_1, "hero"));
    }
}
