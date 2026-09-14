package com.retroeditor.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.retroeditor.service.gameboy.TextEncoding;

class TranslatorModelTest {

    @Test
    void defaultValuesAreCorrect() {
        TranslatorModel model = new TranslatorModel();
        assertEquals(TranslatorModel.CURRENT_VERSION, model.getVersion());
        assertNull(model.getRomPath());
        assertEquals("pokemon", model.getFontTable());
        assertNull(model.getFontTablePath());
        assertEquals(TextEncoding.SENTINEL_0x50, model.getEncoding());
        assertNotNull(model.getEntries());
        assertTrue(model.getEntries().isEmpty());
        assertNotNull(model.getDetectedMappings());
        assertTrue(model.getDetectedMappings().isEmpty());
    }

    @Test
    void setAndGetRomPath() {
        TranslatorModel model = new TranslatorModel();
        model.setRomPath("roms/pokemon_red.gbc");
        assertEquals("roms/pokemon_red.gbc", model.getRomPath());
    }

    @Test
    void setAndGetFontTable() {
        TranslatorModel model = new TranslatorModel();
        model.setFontTable("dragonquest");
        assertEquals("dragonquest", model.getFontTable());
    }

    @Test
    void setAndGetFontTablePath() {
        TranslatorModel model = new TranslatorModel();
        model.setFontTablePath("custom.tbl");
        assertEquals("custom.tbl", model.getFontTablePath());
    }

    @Test
    void encodingRoundTrips() {
        TranslatorModel model = new TranslatorModel();
        model.setEncoding(TextEncoding.SENTINEL_0xFF);
        assertEquals(TextEncoding.SENTINEL_0xFF, model.getEncoding());
        assertEquals("SENTINEL_0xFF", model.getEncodingName());
    }

    @Test
    void invalidEncodingReturnsDefault() {
        TranslatorModel model = new TranslatorModel();
        model.setEncodingName("INVALID");
        assertEquals(TextEncoding.SENTINEL_0x50, model.getEncoding());
    }

    @Test
    void addEntry() {
        TranslatorModel model = new TranslatorModel();
        assertTrue(model.getEntries().isEmpty());

        TranslatorModel.TranslatorEntry entry = new TranslatorModel.TranslatorEntry(
            1, "4A2F", "C3 A1", "HELLO", "HOLA"
        );
        model.addEntry(entry);
        assertEquals(1, model.getEntries().size());
        assertEquals("HOLA", model.getEntries().get(0).getTranslatedText());
    }

    @Test
    void setEntries() {
        TranslatorModel model = new TranslatorModel();
        List<TranslatorModel.TranslatorEntry> entries = List.of(
            new TranslatorModel.TranslatorEntry(0, "0000", "AA BB", "A", "X"),
            new TranslatorModel.TranslatorEntry(1, "1000", "CC DD", "B", "Y")
        );
        model.setEntries(entries);
        assertEquals(2, model.getEntries().size());
    }

    @Test
    void setNullEntriesCreatesEmptyList() {
        TranslatorModel model = new TranslatorModel();
        model.setEntries(null);
        assertNotNull(model.getEntries());
        assertTrue(model.getEntries().isEmpty());
    }

    @Test
    void detectedMappingsRoundTrip() {
        TranslatorModel model = new TranslatorModel();
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("41", "A");
        mappings.put("42", "B");
        model.setDetectedMappings(mappings);
        assertEquals(2, model.getDetectedMappings().size());
        assertEquals("A", model.getDetectedMappings().get("41"));
    }

    @Test
    void setNullMappingsCreatesEmptyMap() {
        TranslatorModel model = new TranslatorModel();
        model.setDetectedMappings(null);
        assertNotNull(model.getDetectedMappings());
        assertTrue(model.getDetectedMappings().isEmpty());
    }

    @Test
    void translatorEntryDefaultValues() {
        TranslatorModel.TranslatorEntry entry = new TranslatorModel.TranslatorEntry();
        assertEquals(0, entry.getBank());
        assertNull(entry.getOffset());
        assertNull(entry.getOriginalBytes());
        assertNull(entry.getDecodedText());
        assertNull(entry.getTranslatedText());
    }

    @Test
    void translatorEntrySettersAndGetters() {
        TranslatorModel.TranslatorEntry entry = new TranslatorModel.TranslatorEntry();
        entry.setBank(5);
        entry.setOffset("ABCD");
        entry.setOriginalBytes("AA BB CC");
        entry.setDecodedText("TEST");
        entry.setTranslatedText("PRUEBA");

        assertEquals(5, entry.getBank());
        assertEquals("ABCD", entry.getOffset());
        assertEquals("AA BB CC", entry.getOriginalBytes());
        assertEquals("TEST", entry.getDecodedText());
        assertEquals("PRUEBA", entry.getTranslatedText());
    }

    @Test
    void fileExtensionIsSmt() {
        assertEquals("smt", TranslatorModel.FILE_EXTENSION);
    }
}
