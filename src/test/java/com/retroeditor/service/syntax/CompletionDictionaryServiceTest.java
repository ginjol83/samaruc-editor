package com.retroeditor.service.syntax;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompletionDictionaryServiceTest {

    private final CompletionDictionaryService service = new CompletionDictionaryService();

    @Test
    void smsProfileIncludesSmsFunctions() {
        Set<String> dict = service.dictionaryFor("z88dk", "sms", null, "test.c");
        assertTrue(dict.contains("clear_vram"));
        assertTrue(dict.contains("read_joypad1"));
        assertTrue(dict.contains("set_sound_freq"));
    }

    @Test
    void gameGearProfileIncludesGameGearFunction() {
        Set<String> dict = service.dictionaryFor("z88dk", "gg", null, "test.c");
        assertTrue(dict.contains("load_palette_gamegear"));
        assertTrue(dict.contains("clear_vram")); // SMS base
    }

    @Test
    void msxProfileIncludesMsxFunctions() {
        Set<String> dict = service.dictionaryFor("z88dk", "msx", null, "test.c");
        assertTrue(dict.contains("msx_set_mode"));
        assertTrue(dict.contains("msx_vwrite"));
        assertTrue(dict.contains("msx_set_sprite_8"));
        assertTrue(dict.contains("gotoxy")); // conio presente
    }

    @Test
    void nesTargetIncludesNesKeywords() {
        Set<String> dict = service.dictionaryFor("cc65", "nes", "nes", "test.c");
        assertTrue(dict.contains("waitvsync"));
        assertTrue(dict.contains("PPU"));
        assertTrue(dict.contains("APU"));
    }

    @Test
    void cpcProfileIncludesCpcFunctions() {
        Set<String> dict = service.dictionaryFor("z88dk", "cpc", null, "test.c");
        assertTrue(dict.contains("bordercolor"));
        assertTrue(dict.contains("cprintf"));
    }

    @Test
    void assemblyFilesReturnEmptyCKeywords() {
        Set<String> dict = service.dictionaryFor("z88dk", "msx", null, "main.asm");
        assertFalse(dict.contains("printf"));
        assertFalse(dict.contains("msx_set_mode"));
    }

    @Test
    void suggestPrioritizesDocumentWords() {
        var suggestions = service.suggest("msx_vpoke(0x1800, 15); msx_vpeek", "msx_vp", "z88dk", "msx", null, "test.c");
        assertFalse(suggestions.isEmpty());
        // same frequency and length; lexicographic order
        assertEquals("msx_vpeek", suggestions.get(0));
    }
}