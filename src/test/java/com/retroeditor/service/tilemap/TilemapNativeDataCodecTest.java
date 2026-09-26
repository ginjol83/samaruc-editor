package com.retroeditor.service.tilemap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.retroeditor.model.tilemap.TilemapDocument;
import com.retroeditor.model.tilemap.TilemapTileData;

class TilemapNativeDataCodecTest {
    @Test
    void encode_ForOneBitPlatform_PacksPixelsInRows() {
        byte[] encoded = TilemapNativeDataCodec.encode(
                TilemapPlatform.ZX_SPECTRUM,
                2,
                2,
                Arrays.asList(0xFFFFFFFF, 0xFF000000, 0xFF000000, 0xFFFFFFFF));

        assertEquals(2, encoded.length);
        assertEquals((byte) 0x80, encoded[0]);
        assertEquals((byte) 0x40, encoded[1]);
    }

    @Test
    void generate_WithTiles_IncludesNativeTileArray() {
        TilemapDocument document = new TilemapDocument();
        document.setPlatform(TilemapPlatform.GAMEBOY.name());
        document.setMapWidth(1);
        document.setMapHeight(1);
        document.setTileWidth(2);
        document.setTileHeight(2);
        document.setMap(List.of(0));
        document.setCollisions(List.of(false));
        document.setTiles(List.of(new TilemapTileData(2, 2, Arrays.asList(
                0xFFFFFFFF, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFFFFFFF))));

        String code = new TilemapCodeGenerator().generate(document);

        assertTrue(code.contains("#define NATIVE_TILE_BYTES 2"));
        assertTrue(code.contains("#define NATIVE_TILE_COUNT 1"));
        assertTrue(code.contains("const unsigned char native_tiles[NATIVE_TILE_COUNT][NATIVE_TILE_BYTES]"));
    }
}
