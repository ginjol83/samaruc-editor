package com.retroeditor.service.sprite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpriteAnimationCodeGeneratorTest {

    private final SpriteAnimationCodeGenerator gen = new SpriteAnimationCodeGenerator();

    private static final SpriteAnimationCodeGenerator.Options CONST_DEFINES =
            new SpriteAnimationCodeGenerator.Options(true, true, false);

    private int[][] frame(int w, int h) {
        int[][] f = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                f[y][x] = (x + y) % 4;
            }
        }
        return f;
    }

    private List<int[][]> frames(int width, int height, int count) {
        List<int[][]> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(frame(width, height));
        return list;
    }

    @Test
    void gameBoyGeneratesDefinesTablesAndFrames() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                frames(8, 8, 2), List.of(2, 3), "sprite_data", CONST_DEFINES);

        assertTrue(code.contains("#define SPRITE_DATA_FRAME_COUNT 2"));
        assertTrue(code.contains("#define SPRITE_DATA_TILES_PER_FRAME 1"));
        assertTrue(code.contains("#define SPRITE_DATA_TOTAL_TILES 2"));
        assertTrue(code.contains("const unsigned char sprite_data[] = {"));
        assertTrue(code.contains("// frame 0, tile 0, row 0"));
        assertTrue(code.contains("// frame 1, tile 1, row 7"));
        assertTrue(code.contains("sprite_data_frames[SPRITE_DATA_FRAME_COUNT] = {0, 1};"));
        assertTrue(code.contains("sprite_data_durations[SPRITE_DATA_FRAME_COUNT] = {2, 3};"));
        assertTrue(code.contains("set_sprite_data(0, SPRITE_DATA_TOTAL_TILES, sprite_data);"));
    }

    @Test
    void gameBoyMultitileComputesTileOffsets() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                frames(16, 16, 2), List.of(4, 5), "spr", CONST_DEFINES);

        assertTrue(code.contains("#define SPR_TILES_PER_FRAME 4"));
        assertTrue(code.contains("#define SPR_TOTAL_TILES 8"));
        assertTrue(code.contains("spr_frames[SPR_FRAME_COUNT] = {0, 4};"));
    }

    @Test
    void zxSpectrumCreatesPerFrameArraysAndPointerTable() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.ZX_SPECTRUM,
                frames(8, 8, 2), List.of(6, 6), "zx_walk", CONST_DEFINES);

        assertTrue(code.contains("#define ZX_WALK_FRAME_COUNT 2"));
        assertTrue(code.contains("#define ZX_WALK_BYTES_PER_FRAME 8"));
        assertTrue(code.contains("const unsigned char zx_walk_frame_0[] = {"));
        assertTrue(code.contains("const unsigned char zx_walk_frame_1[] = {"));
        assertTrue(code.contains("const unsigned char * const zx_walk_frames[ZX_WALK_FRAME_COUNT] = {zx_walk_frame_0, zx_walk_frame_1};"));
        assertTrue(code.contains("zx_walk_durations[ZX_WALK_FRAME_COUNT] = {6, 6};"));
    }

    @Test
    void atariComputesBytesPerFrameByRow() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.ATARI,
                frames(16, 8, 2), List.of(4, 4), "atari_run", CONST_DEFINES);

        assertTrue(code.contains("#define ATARI_RUN_BYTES_PER_FRAME 16"));
        assertTrue(code.contains("const unsigned char atari_run_frame_0[] = {"));
        assertTrue(code.contains("const unsigned char * const atari_run_frames[ATARI_RUN_FRAME_COUNT] = {atari_run_frame_0, atari_run_frame_1};"));
    }

    @Test
    void symbolIsSanitized() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                frames(8, 8, 1), null, "mi sprite!",
                new SpriteAnimationCodeGenerator.Options(false, false, false));

        assertTrue(code.contains("unsigned char mi_sprite_[]"));
        assertFalse(code.contains("mi sprite"));
    }

    @Test
    void defaultDurationUsedWhenMissing() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                frames(8, 8, 2), null, "spr",
                new SpriteAnimationCodeGenerator.Options(false, false, false));

        assertTrue(code.contains("spr_durations[2] = {4, 4};"));
    }

    @Test
    void rejectsEmptyFrameList() {
        assertThrows(IllegalArgumentException.class, () ->
                gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY, new ArrayList<>(), null, "s", CONST_DEFINES));
    }

    // ------------------------------------------------------------------
    // Regresiones de compilacion del .c generado
    // ------------------------------------------------------------------

    @Test
    void durationsBlockIsClosedExactlyOnce() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                frames(8, 8, 3), List.of(4, 4, 4), "spr", CONST_DEFINES);

        assertTrue(code.contains("spr_durations[SPR_FRAME_COUNT] = {4, 4, 4};\n\n"));
        assertFalse(code.contains("};\n\n};"));
    }

    /**
     * Barrido de compatibilidad: cada plataforma emite los marcos y las tablas base.
     */
    private void assertCommonShape(String code, String symbolUpper) {
        assertTrue(code.contains(symbolUpper + "_FRAME_COUNT"));
        assertTrue(code.contains("_frames["));
        assertTrue(code.contains("_durations["));
    }

    @Test
    void c64EmitsBitmapAndColorRamTables() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.C64,
                frames(16, 8, 1), List.of(4), "c64_spr", CONST_DEFINES);

        assertTrue(code.contains("#define C64_SPR_BYTES_PER_FRAME 16"));
        assertTrue(code.contains("#define C64_SPR_COLOR_BYTES_PER_FRAME 2"));
        assertTrue(code.contains("const unsigned char c64_spr_frame_0[] = {"));
        assertTrue(code.contains("const unsigned char c64_spr_colors_0[] = {"));
        assertTrue(code.contains("c64_spr_colors[C64_SPR_FRAME_COUNT] = {c64_spr_colors_0};"));
        assertCommonShape(code, "C64_SPR");
    }

    @Test
    void c64ColorRamUsesDominantOnColor() {
        int[][] f = new int[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) f[y][x] = x < 5 ? 7 : 2;
        }

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.C64,
                List.<int[][]>of(f), null, "c64_dom", CONST_DEFINES);

        assertTrue(code.contains("0x07, // color RAM at (0, 0) [frame 0]"));
    }

    @Test
    void cpcPacksFourPixelsPerByte() {
        int[][] f = new int[8][8];
        f[0][0] = 1;
        f[0][1] = 2;
        f[0][2] = 3;
        f[0][3] = 0;

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.CPC,
                List.<int[][]>of(f), null, "cpc_tile", CONST_DEFINES);

        assertTrue(code.contains("#define CPC_TILE_BYTES_PER_FRAME 16"));
        assertTrue(code.contains("0x6C, 0x00,"));
        assertCommonShape(code, "CPC_TILE");
    }

    @Test
    void smsPacksFourPixelsPerByte() {
        int[][] f = new int[8][8];
        f[0][0] = 3;
        f[0][1] = 1;

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.MASTER_SYSTEM,
                List.<int[][]>of(f), null, "sms_t", CONST_DEFINES);

        assertTrue(code.contains("#define SMS_T_BYTES_PER_FRAME 16"));
        assertTrue(code.contains("0xD0, 0x00,"));
        assertCommonShape(code, "SMS_T");
    }

    @Test
    void gameGearPacksFourBitPixelsPerByte() {
        int[][] f = new int[8][8];
        f[0][0] = 0x09;
        f[0][1] = 0x05;

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.GAME_GEAR,
                List.<int[][]>of(f), null, "gg_t", CONST_DEFINES);

        assertTrue(code.contains("#define GG_T_BYTES_PER_FRAME 32"));
        assertTrue(code.contains("0x59, 0x00, 0x00, 0x00,"));
        assertCommonShape(code, "GG_T");
    }

    @Test
    void msxEmitsOneBitPatternsAndTheSpriteColours() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.MSX,
                frames(16, 8, 2), List.of(4, 6), "msx_spr", CONST_DEFINES);

        assertTrue(code.contains("#define MSX_SPR_BYTES_PER_FRAME 16"));
        assertTrue(code.contains("#define MSX_SPR_COLOR_BYTES_PER_FRAME 2"));
        assertTrue(code.contains("const unsigned char msx_spr_frame_0[] = {"));
        assertTrue(code.contains("const unsigned char msx_spr_colors_0[] = {"));
        assertTrue(code.contains("msx_spr_colors[MSX_SPR_FRAME_COUNT] = {msx_spr_colors_0"));
        assertTrue(code.contains("set_sprite_8(tile, msx_spr_frames[anim_i] + tile * 8);"));
        assertTrue(code.contains("put_sprite_8(id, x, y, tile, msx_spr_colors[anim_i][tile]);"));
        assertFalse(code.contains("vwrite(msx_spr"),
                "set_sprite_8 ya copia los patrones al generador de sprites, no hay que volcar a 0x0000");
        assertCommonShape(code, "MSX_SPR");
    }

    @Test
    void msxSpriteColourIsASingleFourBitTmsIndex() {
        int[][] f = new int[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) f[y][x] = x < 5 ? 7 : 2;
        }

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.MSX,
                List.<int[][]>of(f), null, "msx_dom", CONST_DEFINES);

        assertTrue(code.contains("0x07, // color del sprite (0, 0) [frame 0]"),
                "El atributo de sprite del TMS9918 es un unico indice de 4 bits, no dos nibbles");
    }

    @Test
    void msxPatternsAreOneBitPerPixel() {
        int[][] f = new int[8][8];
        f[0][0] = 1;
        f[0][7] = 1;

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.MSX,
                List.<int[][]>of(f), null, "msx_bits", CONST_DEFINES);

        assertTrue(code.contains("0x81,"), "Un byte por fila con el pixel de la izquierda en el bit alto");
        assertTrue(code.indexOf("0x81,") < code.indexOf("0x00,"),
                "La unica fila con pixeles del tile es la primera");
    }

    @Test
    void nesEmitsTwoPlaneTilesAndPpuUsage() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.NES,
                frames(16, 8, 2), List.of(4, 6), "nes_spr", CONST_DEFINES);

        assertTrue(code.contains("#define NES_SPR_TILES_PER_FRAME 2"));
        assertTrue(code.contains("#define NES_SPR_BYTES_PER_FRAME 32"));
        assertTrue(code.contains("const unsigned char nes_spr_frame_0[] = {"));
        assertTrue(code.contains("nes_spr_frames[NES_SPR_FRAME_COUNT] = {nes_spr_frame_0"));
        assertTrue(code.contains("ppuWrite(0x1000, nes_spr_frames[anim_i], NES_SPR_BYTES_PER_FRAME);"));
        assertTrue(code.contains("// plano 0, fila 0"));
        assertTrue(code.contains("// plano 1, fila 7"));
        assertCommonShape(code, "NES_SPR");
    }

    @Test
    void nesPpuValuesAreTwoBitSlotsNotPaletteIndices() {
        int[][] f = new int[8][8];
        f[0][0] = 3;
        f[1][0] = 2;
        f[2][0] = 1;

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.NES,
                List.<int[][]>of(f), null, "nes_bits", CONST_DEFINES);

        // El slot va en los dos planos: 3 activa ambos, 2 solo el plano 1 y 1 solo el 0.
        // El pixel de la izquierda va en el bit alto, asi que cada fila vale 0x80.
        assertTrue(code.contains("0x80, // plano 0, fila 0"));
        assertTrue(code.contains("0x80, // plano 1, fila 0"));
        assertTrue(code.contains("0x00, // plano 0, fila 1"));
        assertTrue(code.contains("0x80, // plano 1, fila 1"));
        assertTrue(code.contains("0x80, // plano 0, fila 2"));
        assertTrue(code.contains("0x00, // plano 1, fila 2"));
    }

    @Test
    void nesEightBySixteenPairsTilesByColumn() {
        int[][] f = new int[16][16];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) f[y][x] = 1;
        }
        for (int y = 8; y < 16; y++) {
            for (int x = 0; x < 8; x++) f[y][x] = 2;
        }

        String columnOrder = gen.generate(SpriteAnimationCodeGenerator.Platform.NES,
                List.<int[][]>of(f), null, "nes_tall",
                new SpriteAnimationCodeGenerator.Options(true, true, true));

        int topLeft = columnOrder.indexOf("// Tile at (0, 0) [frame 0, tile 0]");
        int bottomLeft = columnOrder.indexOf("// Tile at (0, 1) [frame 0, tile 1]");
        int topRight = columnOrder.indexOf("// Tile at (1, 0) [frame 0, tile 2]");

        assertTrue(topLeft >= 0 && bottomLeft > topLeft && topRight > bottomLeft,
                "El PPU empareja los tiles por columna, asi que el par de la izquierda va contiguo");
    }

    @Test
    void gameBoyIncludesStdintAndUsesUint8Tables() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                frames(8, 8, 1), null, "spr", CONST_DEFINES);

        assertTrue(code.contains("#include <stdint.h>"));
        assertTrue(code.contains("static const uint8_t spr_frames["));
        assertTrue(code.contains("static const uint8_t spr_durations["));
        assertFalse(code.contains("UINT8"));
    }

    @Test
    void withoutDefinesTablesUseLiteralSizes() {
        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                frames(8, 8, 3), null, "spr",
                new SpriteAnimationCodeGenerator.Options(true, false, false));

        assertFalse(code.contains("#define"));
        assertTrue(code.contains("static const uint8_t spr_frames[3] = {0, 1, 2};"));
        assertTrue(code.contains("static const uint8_t spr_durations[3] = {4, 4, 4};"));
    }

    @Test
    void gameBoy8x16PairsOrderTilesByColumn() {
        int[][] f = new int[16][16];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) f[y][x] = 1;          // top-left tile: low 0xFF
        }
        for (int y = 8; y < 16; y++) {
            for (int x = 0; x < 8; x++) f[y][x] = 2;          // bottom-left tile: high 0xFF
        }

        String columnOrder = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                List.<int[][]>of(f), null, "spr",
                new SpriteAnimationCodeGenerator.Options(true, true, true));
        String rowOrder = gen.generate(SpriteAnimationCodeGenerator.Platform.GAMEBOY,
                List.<int[][]>of(f), null, "spr", CONST_DEFINES);

        int topLeftCol = columnOrder.indexOf("0xFF, 0x00,");
        int bottomLeftCol = columnOrder.indexOf("0x00, 0xFF,");
        int topRightCol = columnOrder.indexOf("0x00, 0x00,");

        int topRightRow = rowOrder.indexOf("0x00, 0x00,");
        int bottomLeftRow = rowOrder.indexOf("0x00, 0xFF,");

        assertTrue(topLeftCol >= 0 && bottomLeftCol > topLeftCol && bottomLeftCol < topRightCol,
                "los pares 8x16 deben ser contiguos por columna");
        assertTrue(topRightRow < bottomLeftRow, "el orden por filas intercala la otra columna");
    }

    @Test
    void zxGeneratesPerTileAttributeBytes() {
        int[][] f = new int[8][16];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) f[y][x] = 7;
            for (int x = 8; x < 16; x++) f[y][x] = 15;
        }

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.ZX_SPECTRUM,
                List.<int[][]>of(f), List.of(6), "zx_attr", CONST_DEFINES);

        assertTrue(code.contains("#define ZX_ATTR_ATTR_BYTES_PER_FRAME 2"));
        assertTrue(code.contains("0x07, // INK/PAPER tile at (0, 0) [frame 0]"));
        assertTrue(code.contains("0x47, // INK/PAPER tile at (1, 0) [frame 0]"));
        assertTrue(code.contains(
                "const unsigned char * const zx_attr_attributes[ZX_ATTR_FRAME_COUNT] = {zx_attr_attributes_0};"));
    }

    @Test
    void zxAttributeUsesDominantInkWithinBlock() {
        int[][] f = new int[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) f[y][x] = x < 3 ? 3 : 1;
        }

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.ZX_SPECTRUM,
                List.<int[][]>of(f), null, "zx_dom", CONST_DEFINES);

        assertTrue(code.contains("0x01, // INK/PAPER tile at (0, 0) [frame 0]"));
    }

    @Test
    void zxEmitsScreenDrawHelper() {
        int[][] f = new int[8][16];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) f[y][x] = x < 8 ? 7 : 3;
        }

        String code = gen.generate(SpriteAnimationCodeGenerator.Platform.ZX_SPECTRUM,
                List.<int[][]>of(f), List.of(4), "spr_hero", CONST_DEFINES);

        assertTrue(code.contains(
                "void spr_hero_draw(unsigned char gx, unsigned char gy, unsigned char frame_i) {"));
        assertTrue(code.contains(
                "bitmap = (unsigned char *)(0x4000 + ((y & 0xC0) << 5) + ((y & 0x07) << 8) + ((y & 0x38) << 2) + (x >> 3));"));
        assertTrue(code.contains("attrs = (unsigned char *)(0x5800 + ((y >> 3) << 5) + (x >> 3));"));
        assertTrue(code.contains("bitmap[r << 8] = *p++;"));
        assertTrue(code.contains("spr_hero_draw(px, py, anim_i);"));
    }
}