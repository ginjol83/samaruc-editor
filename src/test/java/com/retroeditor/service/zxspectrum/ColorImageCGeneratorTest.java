package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para el generador de código C para imágenes a color.
 */
public class ColorImageCGeneratorTest {

    private ColorImageCGenerator generator = new ColorImageCGenerator();

    /**
     * Crea una imagen de prueba simple (2x2 con índices 0,1,2,3).
     */
    private ZxColorImage createTestImage() {
        byte[] pixels = { 0, 1, 2, 3 };
        int[] palette = new int[32];
        // Llenar con colores básicos del Spectrum
        palette[0] = 0;     palette[1] = 0;     palette[2] = 0;     palette[3] = 255;  // Black
        palette[4] = 0;     palette[5] = 0;     palette[6] = 215;   palette[7] = 255;  // Blue
        // ... resto con ceros para simplificar
        for (int i = 8; i < 32; i++) {
            palette[i] = 0;
        }
        return new ZxColorImage(pixels, palette, 2, 2, false);
    }

    @Test
    public void testGenerateCBasic() {
        ZxColorImage img = createTestImage();
        String c = generator.generateC(img, "testImage", true, false);

        assertNotNull(c);
        assertTrue(c.contains("const unsigned char testImage[]"), "Debe contener declaración");
        assertTrue(c.contains("0x00"), "Debe contener píxeles");
        assertTrue(c.contains("// Imagen ZX Spectrum"), "Debe contener comentario");
    }

    @Test
    public void testGenerateCWithoutConst() {
        ZxColorImage img = createTestImage();
        String c = generator.generateC(img, "img", false, false);

        assertTrue(c.contains("unsigned char img[]"), "Sin const");
        assertFalse(c.contains("const unsigned char img[]"), "No debe tener const");
    }

    @Test
    public void testGenerateCWithDefines() {
        ZxColorImage img = createTestImage();
        String c = generator.generateC(img, "myImage", true, true);

        assertTrue(c.contains("#define myImage_WIDTH 2"), "Define WIDTH");
        assertTrue(c.contains("#define myImage_HEIGHT 2"), "Define HEIGHT");
        assertTrue(c.contains("#define myImage_SIZE 4"), "Define SIZE");
    }

    @Test
    public void testGenerateCPaletteComment() {
        ZxColorImage img = createTestImage();
        String c = generator.generateC(img, "img", true, false);

        assertTrue(c.contains("// 0: Black"), "Nombre del color 0");
        assertTrue(c.contains("RGB("), "Información RGB");
    }

    @Test
    public void testGenerateCPixelFormatting() {
        ZxColorImage img = createTestImage();
        String c = generator.generateC(img, "img", true, false);

        // Debe tener hexadecimal con 0x
        assertTrue(c.contains("0x00"), "Formato hex");
        assertTrue(c.contains("0x01"), "Índice 1");
        assertTrue(c.contains("0x02"), "Índice 2");
        assertTrue(c.contains("0x03"), "Índice 3");
    }

    @Test
    public void testGenerateCBrightPalette() {
        ZxColorImage img = new ZxColorImage(
            new byte[] { 0 },
            new int[32],
            1, 1,
            true  // Bright palette
        );
        String c = generator.generateC(img, "img", true, false);

        assertTrue(c.contains("BRIGHT"), "Debe mencionar bright");
    }

    @Test
    public void testSanitizeIdentifier() {
        // Test indirectamente a través de generateC
        ZxColorImage img = createTestImage();
        String c = generator.generateC(img, "my-image.name!", true, false);

        // El identificador debe estar sanitizado
        assertTrue(c.contains("unsigned char my_image_name"), "Caracteres inválidos reemplazados");
    }

    @Test
    public void testGenerateCLargeImage() {
        byte[] pixels = new byte[256];  // 16x16
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (i % 8);  // Colores 0-7
        }
        int[] palette = new int[32];
        ZxColorImage img = new ZxColorImage(pixels, palette, 16, 16, false);

        String c = generator.generateC(img, "large", true, true);

        assertNotNull(c);
        assertTrue(c.contains("256"), "Total de bytes");
        assertTrue(c.contains("#define large_SIZE 256"), "Define size");
    }
}
