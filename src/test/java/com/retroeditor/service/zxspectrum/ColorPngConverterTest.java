package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para conversión de PNG a color indexed (ZX Spectrum).
 */
public class ColorPngConverterTest {

    private PngToZxBitmapConverter converter = new PngToZxBitmapConverter();

    /**
     * Crea un PNG de prueba con color uniforme.
     */
    private File createColorTestPng(int width, int height, int rgbColor, @TempDir File tempDir) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, rgbColor);
            }
        }
        File file = new File(tempDir, "test_color.png");
        ImageIO.write(img, "PNG", file);
        return file;
    }

    @Test
    public void testConvertColorImageBlack(@TempDir File tempDir) throws IOException {
        File png = createColorTestPng(16, 16, 0xFF000000, tempDir);
        ZxColorImage result = converter.convertPngToColorIndexed(png, 16, 16, false);

        assertNotNull(result);
        assertEquals(16, result.width());
        assertEquals(16, result.height());
        assertEquals(256, result.pixelData().length);
        
        // Todos los píxeles deben ser color 0 (negro)
        for (byte b : result.pixelData()) {
            assertEquals(0, b & 0xFF, "Todos píxeles deben ser negro");
        }
    }

    @Test
    public void testConvertColorImageRed(@TempDir File tempDir) throws IOException {
        // RGB(215, 0, 0) = rojo puro del Spectrum (índice 2)
        File png = createColorTestPng(8, 8, 0xFFD70000, tempDir);
        ZxColorImage result = converter.convertPngToColorIndexed(png, 8, 8, false);

        assertEquals(2, result.pixelData()[0] & 0xFF, "Rojo puro -> índice 2");
    }

    @Test
    public void testConvertColorImagePalette(@TempDir File tempDir) throws IOException {
        File png = createColorTestPng(8, 8, 0xFFFFFFFF, tempDir);
        ZxColorImage result = converter.convertPngToColorIndexed(png, 8, 8, false);

        assertNotNull(result.paletteRgb());
        assertEquals(32, result.paletteRgb().length, "8 colores × 4 bytes");
    }

    @Test
    public void testConvertColorImageDimensionValidation(@TempDir File tempDir) throws IOException {
        File png = createColorTestPng(8, 8, 0xFF000000, tempDir);

        // Ancho no múltiplo de 8
        assertThrows(IllegalArgumentException.class, 
            () -> converter.convertPngToColorIndexed(png, 7, 8, false),
            "Ancho no múltiplo de 8");

        // Altura negativa
        assertThrows(IllegalArgumentException.class, 
            () -> converter.convertPngToColorIndexed(png, 8, -1, false),
            "Altura negativa");
    }

    @Test
    public void testConvertColorImageOversized(@TempDir File tempDir) throws IOException {
        // PNG pequeño, pero convertir con dimensiones más grandes
        File png = createColorTestPng(4, 4, 0xFF0000FF, tempDir);
        ZxColorImage result = converter.convertPngToColorIndexed(png, 16, 16, false);

        // Los píxeles fuera de la imagen deben ser negro (índice 0)
        assertEquals(0, result.pixelData()[255] & 0xFF, "Píxeles fuera = negro");
    }

    @Test
    public void testConvertColorImageBrightPalette(@TempDir File tempDir) throws IOException {
        File png = createColorTestPng(8, 8, 0xFF555555, tempDir);  // Gray
        ZxColorImage result = converter.convertPngToColorIndexed(png, 8, 8, true);

        assertTrue(result.isBrightPalette(), "Debe usar paleta bright");
    }

    @Test
    public void testValidateColorImage(@TempDir File tempDir) throws IOException {
        File png = createColorTestPng(8, 8, 0xFF000000, tempDir);
        ZxColorImage result = converter.convertPngToColorIndexed(png, 8, 8, false);

        // No debe lanzar excepción
        result.validate();
    }

    @Test
    public void testGetPixelColor(@TempDir File tempDir) throws IOException {
        File png = createColorTestPng(8, 8, 0xFFD70000, tempDir);
        ZxColorImage result = converter.convertPngToColorIndexed(png, 8, 8, false);

        assertEquals(2, result.getPixelColor(0, 0), "Pixel (0,0) = rojo");
        assertEquals(0, result.getPixelColor(100, 100), "Pixel fuera = negro");
    }
}
