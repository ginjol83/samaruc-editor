package com.retroeditor.service.zxspectrum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZxScreenCodeGeneratorTest {

    private final ZxScreenCodeGenerator generator = new ZxScreenCodeGenerator();

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    @Test
    void declaresTheAttributeFileGeometry() {
        String code = generator.generate("screen_attrs", new ZxScreenEditorModel());
        assertTrue(code.contains("#define SCREEN_ATTRS_COLS 32"));
        assertTrue(code.contains("#define SCREEN_ATTRS_ROWS 24"));
        assertTrue(code.contains("#define SCREEN_ATTRS_COUNT 768"));
        assertTrue(code.contains("#define SCREEN_ATTRS_ADDRESS 0x5800"));
    }

    @Test
    void arrayHoldsEveryAttributeByte() {
        String code = generator.generate("screen_attrs", new ZxScreenEditorModel());
        // 32 bytes por fila x 24 filas
        assertEquals(24, countOccurrences(code, "// Fila "));
        assertTrue(code.contains("const unsigned char screen_attrs[SCREEN_ATTRS_COUNT] = {"));
        assertTrue(code.contains("};"));
    }

    @Test
    void encodesInkPaperBrightAndFlash() {
        ZxScreenEditorModel model = new ZxScreenEditorModel();
        model.setAttribute(0, 0, new ZxScreenAttribute(ZxScreenAttribute.RED, ZxScreenAttribute.BLUE, false, false));
        model.setAttribute(0, 1, new ZxScreenAttribute(ZxScreenAttribute.GREEN, ZxScreenAttribute.YELLOW, true, false));
        model.setAttribute(0, 2, new ZxScreenAttribute(ZxScreenAttribute.CYAN, ZxScreenAttribute.MAGENTA, false, true));

        String code = generator.generate("attrs", model);
        // RED=2 (tinta) | BLUE=1 << 3
        assertTrue(code.contains("0x0A"));
        // GREEN=4 | YELLOW=6 << 3 | 0x40 (brillo)
        assertTrue(code.contains("0x74"));
        // CYAN=5 | MAGENTA=3 << 3 | 0x80 (parpadeo)
        assertTrue(code.contains("0x9D"));
    }

    @Test
    void legendListsUsedAttributes() {
        ZxScreenEditorModel model = new ZxScreenEditorModel();
        model.setAttribute(3, 4, new ZxScreenAttribute(ZxScreenAttribute.RED, ZxScreenAttribute.BLUE, true, false));

        String code = generator.generate("attrs", model);
        assertTrue(code.contains("INK=RED"));
        assertTrue(code.contains("PAPER=BLUE"));
        assertTrue(code.contains("BRIGHT"));
        assertTrue(code.contains("(1 caracter)"));
        assertTrue(code.contains("(767 caracteres)"));
    }

    @Test
    void usesTheDefaultArrayNameWhenNoneIsGiven() {
        String code = generator.generate(null, new ZxScreenEditorModel());
        assertTrue(code.contains("const unsigned char screen_attrs["));
    }

    @Test
    void sanitizesInvalidSymbols() {
        String code = generator.generate("9 pantallas-zeta", new ZxScreenEditorModel());
        assertTrue(code.contains("const unsigned char _9_pantallas_zeta["));
    }

    @Test
    void rejectsAttributeBuffersWithWrongSize() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate("attrs", new byte[10]));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("attrs", (byte[]) null));
    }
}