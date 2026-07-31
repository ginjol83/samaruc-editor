package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para CArrayFormatter.
 */
public class CArrayFormatterTest {
    private CArrayFormatter formatter;

    @BeforeEach
    public void setUp() {
        formatter = new CArrayFormatter();
    }

    @Test
    public void testFormatBasic() {
        byte[] data = new byte[] { (byte)0x00, (byte)0xFF, (byte)0xAA };
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "test", 24, 1, 128, ZxBitmapLayout.LINEAR, false, false, "Test image"
        );

        String result = formatter.formatAsC(data, opts);

        assertTrue(result.contains("unsigned char test[]"), "Debe incluir declaración del array");
        assertTrue(result.contains("0x00"), "Debe incluir byte 0x00");
        assertTrue(result.contains("0xFF"), "Debe incluir byte 0xFF");
        assertTrue(result.contains("0xAA"), "Debe incluir byte 0xAA");
        assertFalse(result.contains("const unsigned"), "No debe incluir const");
        assertTrue(result.contains("Test image"), "Debe incluir comentario");
    }

    @Test
    public void testFormatWithConst() {
        byte[] data = new byte[] { (byte)0x42 };
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "my_data", 8, 1, 128, ZxBitmapLayout.LINEAR, true, false, ""
        );

        String result = formatter.formatAsC(data, opts);
        assertTrue(result.contains("const unsigned char my_data[]"), "Debe incluir const");
    }

    @Test
    public void testFormatWithDefines() {
        byte[] data = new byte[] { (byte)0x42 };
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "sprite", 16, 8, 128, ZxBitmapLayout.LINEAR, true, true, ""
        );

        String result = formatter.formatAsC(data, opts);
        assertTrue(result.contains("#define SPRITE_WIDTH 16"), "Debe incluir define WIDTH");
        assertTrue(result.contains("#define SPRITE_HEIGHT 8"), "Debe incluir define HEIGHT");
    }

    @Test
    public void testSanitizeArrayName_ValidNames() {
        byte[] data = new byte[] { (byte)0x00 };
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "valid_name_123", 8, 1, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );
        String result = formatter.formatAsC(data, opts);
        assertTrue(result.contains("unsigned char valid_name_123[]"));
    }

    @Test
    public void testSanitizeArrayName_SpecialCharacters() {
        byte[] data = new byte[] { (byte)0x00 };
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "my-sprite@v2!", 8, 1, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );
        String result = formatter.formatAsC(data, opts);
        assertTrue(result.contains("unsigned char my_sprite_v2_[]"));
    }

    @Test
    public void testSanitizeArrayName_StartsWithDigit() {
        byte[] data = new byte[] { (byte)0x00 };
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "123array", 8, 1, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );
        String result = formatter.formatAsC(data, opts);
        assertTrue(result.contains("unsigned char _123array[]"));
    }

    @Test
    public void testFormatMultipleBytes() {
        byte[] data = new byte[24];  // 3 filas de 8 bytes
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (0x00 + i);
        }

        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "data", 64, 3, 128, ZxBitmapLayout.LINEAR, true, false, ""
        );

        String result = formatter.formatAsC(data, opts);

        // Verifica que está bien formateado
        assertTrue(result.contains("0x00"), "Primer byte");
        assertTrue(result.contains("0x17"), "Último byte (23 en hex)");
        assertTrue(result.contains("};"), "Cierra array");
    }

    @Test
    public void testFormatEmptyDataThrows() {
        byte[] emptyData = new byte[0];
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "empty", 8, 1, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );
        assertThrows(IllegalArgumentException.class, () -> formatter.formatAsC(emptyData, opts));
    }

    @Test
    public void testFormatNullDataThrows() {
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "null_test", 8, 1, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );
        assertThrows(IllegalArgumentException.class, () -> formatter.formatAsC(null, opts));
    }

    @Test
    public void testFormatCommentIncluded() {
        byte[] data = new byte[] { (byte)0x00 };
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "img", 8, 1, 128, ZxBitmapLayout.LINEAR, false, false, "My custom image"
        );

        String result = formatter.formatAsC(data, opts);
        assertTrue(result.contains("My custom image"));
        assertTrue(result.contains("8x1 píxeles"));
        assertTrue(result.contains("1 bytes"));
    }

    @Test
    public void testFormatInfoComments() {
        byte[] data = new byte[] { (byte)0x42 };
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "sprite", 8, 1, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );

        String result = formatter.formatAsC(data, opts);
        assertTrue(result.contains("// Imagen: 8x1 píxeles"));
        assertTrue(result.contains("// Total: 1 bytes"));
        assertTrue(result.contains("layout linear"));
    }
}
