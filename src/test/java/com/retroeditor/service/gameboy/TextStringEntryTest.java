package com.retroeditor.service.gameboy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de {@link TextStringEntry}.
 */
class TextStringEntryTest {

    @Test
    void recordCreationStoresValues() {
        byte[] bytes = {0x01, 0x02, 0x03};
        TextStringEntry entry = new TextStringEntry(
            1, 0x4000, bytes, "ABC", "", TextEncoding.SENTINEL_0x50
        );

        Assertions.assertEquals(1, entry.bank());
        Assertions.assertEquals(0x4000, entry.offset());
        Assertions.assertArrayEquals(bytes, entry.originalBytes());
        Assertions.assertEquals("ABC", entry.decodedText());
        Assertions.assertEquals("", entry.translatedText());
        Assertions.assertEquals(TextEncoding.SENTINEL_0x50, entry.encoding());
    }

    @Test
    void withTranslationCreatesNewEntry() {
        byte[] bytes = {0x01, 0x02};
        TextStringEntry original = new TextStringEntry(
            0, 0, bytes, "AB", "", TextEncoding.SENTINEL_0x50
        );
        TextStringEntry translated = original.withTranslation("XY");

        Assertions.assertEquals("XY", translated.translatedText());
        Assertions.assertEquals("AB", translated.decodedText());
        // Original unchanged
        Assertions.assertEquals("", original.translatedText());
    }

    @Test
    void ofRawCreatesEntryWithDefaults() {
        byte[] bytes = {0x41, 0x42};
        TextStringEntry entry = TextStringEntry.ofRaw(bytes, "AB", TextEncoding.CUSTOM_TILE_TABLE);

        Assertions.assertEquals(0, entry.bank());
        Assertions.assertEquals(0, entry.offset());
        Assertions.assertEquals("AB", entry.decodedText());
    }

    @Test
    void constructorRejectsNullBytes() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new TextStringEntry(0, 0, null, "A", "", TextEncoding.SENTINEL_0x50));
    }

    @Test
    void constructorRejectsEmptyBytes() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new TextStringEntry(0, 0, new byte[0], "A", "", TextEncoding.SENTINEL_0x50));
    }

    @Test
    void toStringShowsFormat() {
        byte[] bytes = {0x01};
        TextStringEntry entry = new TextStringEntry(
            2, 0x1234, bytes, "HELLO", "WORLD", TextEncoding.SENTINEL_0x50
        );

        String str = entry.toString();
        Assertions.assertTrue(str.contains("2"));
        Assertions.assertTrue(str.contains("1234"));
        Assertions.assertTrue(str.contains("HELLO"));
        Assertions.assertTrue(str.contains("WORLD"));
    }
}
