package com.retroeditor.service.atari.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AtariBcdTest {

    @Test
    void encodesZeroAsAllZeroBytes() {
        AtariBcd bcd = AtariBcd.fromDouble(0.0);
        assertEquals(0x00, bcd.exp());
        for (int i = 0; i < 5; i++) {
            assertEquals(0x00, bcd.digit(i));
        }
    }

    @Test
    void encodesOneAsOracleGolden() {
        AtariBcd bcd = AtariBcd.fromDouble(1.0);
        assertEquals(0x40, bcd.exp());
        assertEquals(0x01, bcd.digit(0));
        for (int i = 1; i < 5; i++) {
            assertEquals(0x00, bcd.digit(i));
        }
    }

    @Test
    void encodesHundredAsOracleGolden() {
        AtariBcd bcd = AtariBcd.fromDouble(100.0);
        assertEquals(0x41, bcd.exp());
        assertEquals(0x01, bcd.digit(0));
        for (int i = 1; i < 5; i++) {
            assertEquals(0x00, bcd.digit(i));
        }
    }

    @Test
    void encodesNegativeHalfWithSignBit() {
        AtariBcd bcd = AtariBcd.fromDouble(-0.5);
        assertEquals(0xBF, bcd.exp());
        assertEquals(0x50, bcd.digit(0));
        for (int i = 1; i < 5; i++) {
            assertEquals(0x00, bcd.digit(i));
        }
    }

    @Test
    void encodesBigNumberWithOverflowMarker() {
        AtariBcd bcd = AtariBcd.fromDouble(1e98);
        assertEquals(0x71, bcd.exp());
        for (int i = 0; i < 5; i++) {
            assertEquals(0x99, bcd.digit(i));
        }
    }

    @Test
    void encodesSmallNumberWithUnderflow() {
        AtariBcd bcd = AtariBcd.fromDouble(1e-100);
        assertEquals(0x00, bcd.exp());
        for (int i = 0; i < 5; i++) {
            assertEquals(0x00, bcd.digit(i));
        }
    }

    @Test
    void decodesToDouble() {
        assertEquals(1.0, AtariBcd.of(0x40, 0x01, 0, 0, 0, 0).toDouble(), 0.0);
        assertEquals(100.0, AtariBcd.of(0x41, 0x01, 0, 0, 0, 0).toDouble(), 0.0);
        assertEquals(10.0, AtariBcd.of(0x40, 0x10, 0, 0, 0, 0).toDouble(), 0.0);
        assertEquals(1e9, AtariBcd.of(0x44, 0x10, 0, 0, 0, 0).toDouble(), 0.0);
        assertEquals(0.5, AtariBcd.of(0x3F, 0x50, 0, 0, 0, 0).toDouble(), 0.0);
        assertEquals(0.0, AtariBcd.of(0x00, 0, 0, 0, 0, 0).toDouble(), 0.0);
        assertEquals(-0.0, AtariBcd.of(0x80, 0, 0, 0, 0, 0).toDouble(), 0.0);
        assertEquals(12345.0, AtariBcd.of(0x42, 0x01, 0x23, 0x45, 0x00, 0x00).toDouble(), 0.0);
    }

    @Test
    void printsBasicNumbers() {
        assertEquals("0", AtariBcd.of(0x00, 0, 0, 0, 0, 0).toBasicString());
        assertEquals("1", AtariBcd.of(0x40, 0x01, 0, 0, 0, 0).toBasicString());
        assertEquals("10", AtariBcd.of(0x40, 0x10, 0, 0, 0, 0).toBasicString());
        assertEquals("100", AtariBcd.of(0x41, 0x01, 0, 0, 0, 0).toBasicString());
        assertEquals("1E9", AtariBcd.of(0x44, 0x10, 0, 0, 0, 0).toBasicString());
        assertEquals(".5", AtariBcd.of(0x3F, 0x50, 0, 0, 0, 0).toBasicString());
        assertEquals(".05", AtariBcd.of(0x3F, 0x05, 0, 0, 0, 0).toBasicString());
        assertEquals("3.14159", AtariBcd.of(0x40, 0x03, 0x14, 0x15, 0x90, 0x00).toBasicString());
        assertEquals("12345", AtariBcd.of(0x42, 0x01, 0x23, 0x45, 0x00, 0x00).toBasicString());
    }

    @Test
    void roundTripsSmallIntegersExactly() {
        for (int v = 0; v <= 100; v++) {
            assertEquals(v, AtariBcd.fromDouble(v).toDouble(), 0.0);
        }
        assertEquals(3.14159, AtariBcd.fromDouble(3.14159).toDouble(), 1e-12);
        assertEquals(1e9, AtariBcd.fromDouble(1e9).toDouble(), 0.0);
        assertEquals(0.5, AtariBcd.fromDouble(0.5).toDouble(), 0.0);
    }
}
