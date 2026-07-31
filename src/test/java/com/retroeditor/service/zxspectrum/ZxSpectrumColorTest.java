package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ZxSpectrumColor y codificación de atributos.
 */
public class ZxSpectrumColorTest {
    @Test
    public void testColorIndexValidation() {
        assertDoesNotThrow(() -> new ZxSpectrumColor(0, false));
        assertDoesNotThrow(() -> new ZxSpectrumColor(7, false));
        assertThrows(IllegalArgumentException.class, () -> new ZxSpectrumColor(-1, false));
        assertThrows(IllegalArgumentException.class, () -> new ZxSpectrumColor(8, false));
    }

    @Test
    public void testEncodeAttribute_BlackOnWhite() {
        ZxSpectrumColor black = new ZxSpectrumColor(0, false);
        ZxSpectrumColor white = new ZxSpectrumColor(7, false);
        
        byte attr = ZxSpectrumColor.encodeAttribute(black, white);
        
        // Fondo blanco = color 7 = 111b en bits 5-3
        // Tinta negra = color 0 = 000b en bits 2-0
        // Sin brillo
        assertEquals(0b00111000, attr & 0xFF);
    }

    @Test
    public void testEncodeAttribute_WithBright() {
        ZxSpectrumColor brightRed = new ZxSpectrumColor(2, true);
        ZxSpectrumColor black = new ZxSpectrumColor(0, false);
        
        byte attr = ZxSpectrumColor.encodeAttribute(brightRed, black);
        
        // Debe tener bit de brillo (bit 6)
        assertEquals(0x40, attr & 0x40);
    }

    @Test
    public void testDecodeAttribute() {
        ZxSpectrumColor magenta = new ZxSpectrumColor(3, false);
        ZxSpectrumColor cyan = new ZxSpectrumColor(5, false);
        
        byte encoded = ZxSpectrumColor.encodeAttribute(magenta, cyan);
        ZxSpectrumColor[] decoded = ZxSpectrumColor.decodeAttribute(encoded);
        
        assertEquals(magenta.colorIndex(), decoded[0].colorIndex());
        assertEquals(cyan.colorIndex(), decoded[1].colorIndex());
        assertEquals(magenta.isBright(), decoded[0].isBright());
        assertEquals(cyan.isBright(), decoded[1].isBright());
    }

    @Test
    public void testStandardPalette() {
        assertEquals(8, ZxSpectrumColor.STANDARD_PALETTE.length);
        assertEquals(8, ZxSpectrumColor.BRIGHT_PALETTE.length);
        
        for (int i = 0; i < 8; i++) {
            assertFalse(ZxSpectrumColor.STANDARD_PALETTE[i].isBright());
            assertTrue(ZxSpectrumColor.BRIGHT_PALETTE[i].isBright());
        }
    }

    @Test
    public void testColorNames() {
        ZxSpectrumColor black = new ZxSpectrumColor(0, false);
        ZxSpectrumColor brightYellow = new ZxSpectrumColor(6, true);
        
        assertTrue(black.getColorName().contains("Black"));
        assertTrue(brightYellow.getColorName().contains("Yellow"));
        assertTrue(brightYellow.getColorName().contains("Bright"));
    }
}
