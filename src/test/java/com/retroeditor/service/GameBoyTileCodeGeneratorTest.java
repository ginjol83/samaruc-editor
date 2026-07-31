package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameBoyTileCodeGeneratorTest {

    private final GameBoyTileCodeGenerator generator = new GameBoyTileCodeGenerator();

    @Test
    void to2bppBytes_WithEmptyTile_Returns16ZeroBytes() {
        int[][] pixels = new int[8][8];

        byte[] bytes = generator.to2bppBytes(pixels);

        assertArrayEquals(new byte[16], bytes);
    }

    @Test
    void generateTileCode_WithDefines_ProducesDefines() {
        int[][] pixels = new int[8][8];
        String code = generator.generateTileCode(pixels, "test", true, true);

        assertTrue(code.contains("#define TEST_WIDTH 8"));
        assertTrue(code.contains("#define TEST_HEIGHT 8"));
        assertTrue(code.contains("#define TEST_TILE_COUNT 1"));
    }

    @Test
    void generateTileCode_WithoutConst_DoesNotHaveConst() {
        int[][] pixels = new int[8][8];
        String code = generator.generateTileCode(pixels, "test", false, false);

        assertTrue(code.contains("unsigned char test[] = {"));
        assertTrue(!code.contains("const unsigned char test[] = {"));
    }
}

