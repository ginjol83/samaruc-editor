package com.retroeditor.service.gameboy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Pruebas unitarias de {@link TblGeneratorService}.
 */
class TblGeneratorServiceTest {

    /**
     * Crea un ROM de 32KB con secuencias ascendentes de letras y dígitos,
     * simulando una font table típica de Game Boy.
     */
    private byte[] createTestRomWithFontTable() {
        byte[] rom = new byte[32 * 1024];
        rom[0x134] = (byte) 'T'; rom[0x135] = (byte) 'E'; rom[0x136] = (byte) 'S'; rom[0x137] = (byte) 'T';
        rom[0x147] = 0x00;
        rom[0x148] = 0x00;

        // Secuencia de letras A-Z: bytes 0x20..0x39 en offset 0x200
        for (int i = 0; i < 26; i++) {
            rom[0x200 + i] = (byte) (0x20 + i);
        }
        // Secuencia de dígitos 0-9: bytes 0x40..0x49 en offset 0x400
        for (int i = 0; i < 10; i++) {
            rom[0x400 + i] = (byte) (0x40 + i);
        }
        return rom;
    }

    @Test
    void detectsAscendingLetterSequence() {
        GbRomParser parser = new GbRomParser(createTestRomWithFontTable());
        TblGeneratorService service = new TblGeneratorService(parser);

        GbFontTable table = service.detectTable();

        Assertions.assertEquals("A", table.decode(0x20));
        Assertions.assertEquals("M", table.decode(0x2C));
        Assertions.assertEquals("Z", table.decode(0x39));
    }

    @Test
    void detectsAscendingDigitSequence() {
        GbRomParser parser = new GbRomParser(createTestRomWithFontTable());
        TblGeneratorService service = new TblGeneratorService(parser);

        GbFontTable table = service.detectTable();

        Assertions.assertEquals("0", table.decode(0x40));
        Assertions.assertEquals("5", table.decode(0x45));
        Assertions.assertEquals("9", table.decode(0x49));
    }

    @Test
    void digitSequenceDoesNotOverwriteLetters() {
        GbRomParser parser = new GbRomParser(createTestRomWithFontTable());
        TblGeneratorService service = new TblGeneratorService(parser);

        GbFontTable table = service.detectTable();

        // Los dígitos empiezan en 0x40, lejos de las letras (0x20-0x39)
        Assertions.assertEquals("A", table.decode(0x20));
        Assertions.assertEquals("G", table.decode(0x26));
        Assertions.assertEquals("0", table.decode(0x40));
        Assertions.assertEquals("9", table.decode(0x49));
    }

    @Test
    void detectsPokemonStyleTableViaCommonPatterns() {
        byte[] rom = createTestRomWithFontTable();
        // Patrón Pokémon: bytes 0x00..0x19 = ABCD...P (secuencia de letras desde 0x00)
        for (int i = 0; i < 26; i++) {
            rom[0x600 + i] = (byte) (0x00 + i);
        }

        GbRomParser parser = new GbRomParser(rom);
        TblGeneratorService service = new TblGeneratorService(parser);

        GbFontTable table = service.detectTable();

        Assertions.assertEquals("A", table.decode(0x00));
        Assertions.assertEquals("P", table.decode(0x0F));
        Assertions.assertEquals("Z", table.decode(0x19));
    }

    @Test
    void returnEmptyTableWhenNoTextDetected() {
        byte[] rom = new byte[32 * 1024];
        GbRomParser parser = new GbRomParser(rom);
        TblGeneratorService service = new TblGeneratorService(parser);

        GbFontTable table = service.detectTable();

        Assertions.assertEquals(0, table.size());
    }

    @Test
    void detectTerminatorsFindsSentinels() {
        byte[] rom = new byte[32 * 1024];
        // Simular varias cadenas de texto ASCII seguidas de 0x50 (sentinel Pokémon)
        rom[0x100] = 'A'; rom[0x101] = 'B'; rom[0x102] = 'C'; rom[0x103] = 0x50;
        rom[0x200] = 'H'; rom[0x201] = 'I'; rom[0x202] = 0x50;
        rom[0x300] = 'L'; rom[0x301] = 'E'; rom[0x302] = 'V'; rom[0x303] = 'E'; rom[0x304] = 'L'; rom[0x305] = 0x50;
        rom[0x400] = 'H'; rom[0x401] = 'P'; rom[0x402] = 0x50;

        GbRomParser parser = new GbRomParser(rom);
        TblGeneratorService service = new TblGeneratorService(parser);

        Map<Integer, String> terminators = service.detectTerminators();

        Assertions.assertTrue(terminators.containsKey(0x50));
    }

    @Test
    void generateReportContainsSummary() {
        GbRomParser parser = new GbRomParser(createTestRomWithFontTable());
        TblGeneratorService service = new TblGeneratorService(parser);

        GbFontTable table = service.detectTable();
        String report = service.generateReport(table);

        Assertions.assertTrue(report.contains("TEST"));
        Assertions.assertTrue(report.contains("Mapeos detectados"));
        Assertions.assertTrue(report.contains("36")); // 26 letras + 10 dígitos
    }
}