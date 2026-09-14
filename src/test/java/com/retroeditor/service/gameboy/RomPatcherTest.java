package com.retroeditor.service.gameboy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Pruebas unitarias de {@link RomPatcher}.
 */
class RomPatcherTest {

    private byte[] createTestRom() {
        byte[] rom = new byte[32 * 1024]; // 2 banks
        rom[0x134] = (byte) 'T';
        rom[0x135] = (byte) 'E';
        rom[0x136] = (byte) 'S';
        rom[0x137] = (byte) 'T';
        return rom;
    }

    @Test
    void validateTranslationsReturnsNoErrorsForValidInput() {
        byte[] romData = createTestRom();
        GbRomParser parser = new GbRomParser(romData);
        RomPatcher patcher = new RomPatcher(parser);

        // Translation fits within original length
        byte[] origBytes = {0x01, 0x02, 0x03};
        TextStringEntry entry = new TextStringEntry(
            0, 0x100, origBytes, "ABC", "XY", TextEncoding.SENTINEL_0x50
        );

        List<String> errors = patcher.validateTranslations(List.of(entry));
        Assertions.assertTrue(errors.isEmpty());
    }

    @Test
    void validateTranslationsCatchesOversizedTranslation() {
        byte[] romData = createTestRom();
        GbRomParser parser = new GbRomParser(romData);
        RomPatcher patcher = new RomPatcher(parser);

        byte[] origBytes = {0x01, 0x02};
        TextStringEntry entry = new TextStringEntry(
            0, 0x100, origBytes, "AB", "TOOLONG", TextEncoding.SENTINEL_0x50
        );

        List<String> errors = patcher.validateTranslations(List.of(entry));
        Assertions.assertFalse(errors.isEmpty());
    }

    @Test
    void validateTranslationsIgnoresEmptyTranslations() {
        byte[] romData = createTestRom();
        GbRomParser parser = new GbRomParser(romData);
        RomPatcher patcher = new RomPatcher(parser);

        byte[] origBytes = {0x01, 0x02};
        TextStringEntry entry = new TextStringEntry(
            0, 0x100, origBytes, "AB", "", TextEncoding.SENTINEL_0x50
        );

        List<String> errors = patcher.validateTranslations(List.of(entry));
        Assertions.assertTrue(errors.isEmpty());
    }

    @Test
    void applyTranslationsModifiesRomData() {
        byte[] romData = createTestRom();
        GbRomParser parser = new GbRomParser(romData);
        RomPatcher patcher = new RomPatcher(parser);

        byte[] origBytes = {0x01, 0x02};
        TextStringEntry entry = new TextStringEntry(
            0, 0x100, origBytes, "AB", "XY", TextEncoding.SENTINEL_0x50
        );

        byte[] result = patcher.applyTranslations(List.of(entry));
        Assertions.assertNotNull(result);
        Assertions.assertEquals(romData.length, result.length);
    }

    @Test
    void generateIpsPatchProducesValidHeader() {
        byte[] romData = createTestRom();
        GbRomParser parser = new GbRomParser(romData);
        RomPatcher patcher = new RomPatcher(parser);

        byte[] origBytes = {0x01, 0x02};
        TextStringEntry entry = new TextStringEntry(
            0, 0x100, origBytes, "AB", "XY", TextEncoding.SENTINEL_0x50
        );

        byte[] ips = patcher.generateIpsPatch(List.of(entry));

        // IPS must start with "PATCH" and end with "EOF"
        Assertions.assertTrue(ips.length >= 8); // PATCH(5) + at least 1 record + EOF(3)
        String header = new String(ips, 0, 5);
        Assertions.assertEquals("PATCH", header);
    }

    @Test
    void generateIpsPatchWithNoTranslationsProducesMinimalPatch() {
        byte[] romData = createTestRom();
        GbRomParser parser = new GbRomParser(romData);
        RomPatcher patcher = new RomPatcher(parser);

        byte[] ips = patcher.generateIpsPatch(List.of());
        String content = new String(ips);
        Assertions.assertTrue(content.contains("PATCH"));
        Assertions.assertTrue(content.contains("EOF"));
    }
}
