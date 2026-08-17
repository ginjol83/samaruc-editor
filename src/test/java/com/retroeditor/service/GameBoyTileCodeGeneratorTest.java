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
    void to2bppBytes_With8x16Sprite_Returns32Bytes() {
        int[][] pixels = new int[16][8];
        byte[] bytes = generator.to2bppBytes(pixels);
        assertTrue(bytes.length == 32);
    }

    @Test
    void to2bppBytes_With16x16Sprite_Returns64Bytes() {
        int[][] pixels = new int[16][16];
        byte[] bytes = generator.to2bppBytes(pixels);
        assertTrue(bytes.length == 64);
    }

    @Test
    void generateTileCode_With8x16_ProducesDefinesAndCorrectBytes() {
        int[][] pixels = new int[16][8];
        String code = generator.generateTileCode(pixels, "sprite16", true, true);

        assertTrue(code.contains("#define SPRITE16_WIDTH 8"));
        assertTrue(code.contains("#define SPRITE16_HEIGHT 16"));
        assertTrue(code.contains("#define SPRITE16_TILE_COUNT 2"));
        assertTrue(code.contains("const unsigned char sprite16[] = {"));
    }
}

