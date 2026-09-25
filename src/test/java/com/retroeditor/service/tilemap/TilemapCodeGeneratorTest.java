package com.retroeditor.service.tilemap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TilemapCodeGeneratorTest {
    private final TilemapCodeGenerator generator = new TilemapCodeGenerator();

    @Test
    void generate_WithGameBoyMap_IncludesDimensionsAndLayers() {
        int[][] tilemap = {
            { 0, 7, 3 },
            { 8, 1, 2 }
        };
        boolean[][] collisions = {
            { false, true, false },
            { true, false, true }
        };

        String code = generator.generate(TilemapPlatform.GAMEBOY, tilemap, collisions);

        assertTrue(code.contains("#define MAP_WIDTH  3"));
        assertTrue(code.contains("#define MAP_HEIGHT 2"));
        assertTrue(code.contains("#define TILE_BITS 2"));
        assertTrue(code.contains("const unsigned char level_tilemap[MAP_HEIGHT][MAP_WIDTH]"));
        assertTrue(code.contains("{   0,   7,   3 }"));
        assertTrue(code.contains("const unsigned char level_collisions[MAP_HEIGHT][MAP_WIDTH]"));
        assertTrue(code.contains("{ 0, 1, 0 }"));
    }

    @Test
    void generate_ForEachPlatform_AddsPlatformMetadata() {
        int[][] tilemap = { { 1 } };
        boolean[][] collisions = { { false } };

        for (TilemapPlatform platform : TilemapPlatform.values()) {
            String code = generator.generate(platform, tilemap, collisions);

            assertTrue(code.contains("#define TILE_BITS " + platform.getBitsPerPixel()));
            assertTrue(code.contains("#define TILEMAP_PLATFORM_" + platform.getCodeName() + " 1"));
            assertTrue(code.contains(platform == TilemapPlatform.C64 ? "screen_matrix" : "level_tilemap"));
        }
    }

    @Test
    void generate_WithMismatchedCollisions_RejectsInput() {
        int[][] tilemap = { { 0 } };
        boolean[][] collisions = { { false }, { false } };

        assertThrows(IllegalArgumentException.class, () -> {
            generator.generate(TilemapPlatform.GAMEBOY, tilemap, collisions);
        });
    }

    @Test
    void generate_WithTileOutsideByteRange_RejectsInput() {
        int[][] tilemap = { { 256 } };
        boolean[][] collisions = { { false } };

        assertThrows(IllegalArgumentException.class, () -> {
            generator.generate(TilemapPlatform.GAMEBOY, tilemap, collisions);
        });
    }
}
