package com.retroeditor.service.gameboy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de {@link GbTextDecoder}.
 */
class GbTextDecoderTest {

    @Test
    void decodeWithSentinel0x50StopsAtTerminator() {
        GbFontTable table = GbFontTable.createPokemonTable();
        GbTextDecoder decoder = new GbTextDecoder(table, TextEncoding.SENTINEL_0x50);

        // "HELLO" followed by 0x50 sentinel
        byte[] data = {0x07, 0x04, 0x0B, 0x0B, 0x0E, 0x50};
        String result = decoder.decode(data);
        Assertions.assertEquals("HELLO", result);
    }

    @Test
    void decodeWithSentinel0xFFStopsAtTerminator() {
        GbFontTable table = GbFontTable.createPokemonTable();
        GbTextDecoder decoder = new GbTextDecoder(table, TextEncoding.SENTINEL_0xFF);

        byte[] data = {0x07, 0x04, 0x0B, 0x0B, 0x0E, (byte) 0xFF};
        String result = decoder.decode(data);
        Assertions.assertEquals("HELLO", result);
    }

    @Test
    void decodeWithFixedLengthStopsAtLength() {
        GbFontTable table = GbFontTable.createPokemonTable();
        GbTextDecoder decoder = new GbTextDecoder(table, TextEncoding.FIXED_LENGTH, 4);

        byte[] data = {0x07, 0x04, 0x0B, 0x0B, 0x0E, 0x50};
        String result = decoder.decode(data);
        Assertions.assertEquals("HELL", result);
    }

    @Test
    void decodeEmptyArrayReturnsEmpty() {
        GbFontTable table = GbFontTable.createPokemonTable();
        GbTextDecoder decoder = new GbTextDecoder(table, TextEncoding.SENTINEL_0x50);

        String result = decoder.decode(new byte[0]);
        Assertions.assertEquals("", result);
    }

    @Test
    void decodeNullReturnsEmpty() {
        GbFontTable table = GbFontTable.createPokemonTable();
        GbTextDecoder decoder = new GbTextDecoder(table, TextEncoding.SENTINEL_0x50);

        String result = decoder.decode(null);
        Assertions.assertEquals("", result);
    }

    @Test
    void decodeWithUnknownTilesShowsHex() {
        GbFontTable table = GbFontTable.createPokemonTable();
        GbTextDecoder decoder = new GbTextDecoder(table, TextEncoding.SENTINEL_0x50);

        byte[] data = {0x00, (byte) 0xFD, 0x50}; // A, [FD] (unknown), terminator
        String result = decoder.decode(data);
        Assertions.assertTrue(result.contains("A"));
        Assertions.assertTrue(result.contains("[FD]"));
    }

    @Test
    void getterMethodsReturnCorrectValues() {
        GbFontTable table = GbFontTable.createPokemonTable();
        GbTextDecoder decoder = new GbTextDecoder(table, TextEncoding.SENTINEL_0x50);

        Assertions.assertSame(table, decoder.getFontTable());
        Assertions.assertEquals(TextEncoding.SENTINEL_0x50, decoder.getEncoding());
    }
}
