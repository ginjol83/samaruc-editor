package com.retroeditor.service.gameboy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de {@link GbRomParser}.
 */
class GbRomParserTest {

    /**
     * Crea un ROM de prueba de 32KB (2 banks) con datos mínimos.
     */
    private byte[] createTestRom() {
        byte[] rom = new byte[32 * 1024]; // 2 banks
        // Header info at 0x100-0x14F
        rom[0x134] = (byte) 'T';
        rom[0x135] = (byte) 'E';
        rom[0x136] = (byte) 'S';
        rom[0x137] = (byte) 'T';
        rom[0x147] = 0x00; // ROM ONLY
        rom[0x148] = 0x00; // 32KB
        return rom;
    }

    @Test
    void constructorLoadsRomCorrectly() {
        byte[] data = createTestRom();
        GbRomParser parser = new GbRomParser(data);

        Assertions.assertEquals(2, parser.getBankCount());
        Assertions.assertEquals(32 * 1024, parser.getRomSize());
        Assertions.assertEquals("TEST", parser.getTitle());
        Assertions.assertEquals(0x00, parser.getCartridgeType());
    }

    @Test
    void getBankReturnsCorrectSlice() {
        byte[] data = createTestRom();
        data[0] = 0x42; // Bank 0, offset 0
        data[16 * 1024] = (byte) 0xAB; // Bank 1, offset 0

        GbRomParser parser = new GbRomParser(data);
        byte[] bank0 = parser.getBank(0);
        byte[] bank1 = parser.getBank(1);

        Assertions.assertEquals(0x42, bank0[0] & 0xFF);
        Assertions.assertEquals(0xAB, bank1[0] & 0xFF);
    }

    @Test
    void readByteReturnsCorrectValue() {
        byte[] data = createTestRom();
        data[100] = 0x55;

        GbRomParser parser = new GbRomParser(data);
        Assertions.assertEquals(0x55, parser.readByte(100));
    }

    @Test
    void readByteReturnsMinusOneForOOB() {
        byte[] data = createTestRom();
        GbRomParser parser = new GbRomParser(data);
        Assertions.assertEquals(-1, parser.readByte(99999));
    }

    @Test
    void toAbsoluteOffsetCalculatesCorrectly() {
        byte[] data = createTestRom();
        GbRomParser parser = new GbRomParser(data);

        // Bank 1, offset 0x4000 = 1 * 16384 + 16384 = 32768
        Assertions.assertEquals(32768, parser.toAbsoluteOffset(1, 0x4000));
    }

    @Test
    void readBytesReturnsCorrectSlice() {
        byte[] data = createTestRom();
        data[0] = 0x10;
        data[1] = 0x20;
        data[2] = 0x30;

        GbRomParser parser = new GbRomParser(data);
        byte[] result = parser.readBytes(0, 3);

        Assertions.assertEquals(3, result.length);
        Assertions.assertEquals(0x10, result[0] & 0xFF);
        Assertions.assertEquals(0x20, result[1] & 0xFF);
        Assertions.assertEquals(0x30, result[2] & 0xFF);
    }

    @Test
    void constructorRejectsTooSmallRom() {
        byte[] data = new byte[100]; // Too small
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GbRomParser(data));
    }

    @Test
    void constructorRejectsNullData() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GbRomParser(null));
    }

    @Test
    void cartridgeTypeDescriptionReturnsReadableString() {
        byte[] data = createTestRom();
        data[0x147] = 0x19;
        GbRomParser parser = new GbRomParser(data);
        Assertions.assertEquals("MBC5", parser.getCartridgeTypeDescription());
    }
}
