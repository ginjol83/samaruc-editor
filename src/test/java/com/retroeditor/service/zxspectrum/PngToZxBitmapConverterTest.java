package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.File;
import java.io.IOException;

/**
 * Tests unitarios para PngToZxBitmapConverter.
 */
public class PngToZxBitmapConverterTest {
    private PngToZxBitmapConverter converter;

    @BeforeEach
    public void setUp() {
        converter = new PngToZxBitmapConverter();
    }

    /**
     * Crea una imagen PNG sintética de prueba.
     * Los píxeles blancos son RGB(255,255,255), los negros RGB(0,0,0).
     */
    private File createTestPng(int width, int height, boolean[][] pattern, @TempDir File tempDir) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = (y < pattern.length && x < pattern[y].length && pattern[y][x])
                    ? Color.WHITE.getRGB()
                    : Color.BLACK.getRGB();
                img.setRGB(x, y, color);
            }
        }

        File file = new File(tempDir, "test_" + width + "x" + height + ".png");
        ImageIO.write(img, "PNG", file);
        return file;
    }

    @Test
    public void testConvert8x8_AllBlack(@TempDir File tempDir) throws IOException {
        // Patrón: todo negro
        boolean[][] pattern = new boolean[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                pattern[y][x] = false;
            }
        }

        File png = createTestPng(8, 8, pattern, tempDir);
        ZxBitmapExportOptions opts = ZxBitmapExportOptions.defaults("black", 8, 8);
        byte[] result = converter.convertPngToMonochrome(png, opts);

        assertEquals(8, result.length, "8x8 = 8 bytes (1 por fila)");
        for (byte b : result) {
            assertEquals(0x00, b & 0xFF, "Todo negro = 0x00");
        }
    }

    @Test
    public void testConvert8x8_AllWhite(@TempDir File tempDir) throws IOException {
        // Patrón: todo blanco
        boolean[][] pattern = new boolean[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                pattern[y][x] = true;
            }
        }

        File png = createTestPng(8, 8, pattern, tempDir);
        ZxBitmapExportOptions opts = ZxBitmapExportOptions.defaults("white", 8, 8);
        byte[] result = converter.convertPngToMonochrome(png, opts);

        assertEquals(8, result.length, "8x8 = 8 bytes (1 por fila)");
        for (byte b : result) {
            assertEquals(0xFF, b & 0xFF, "Todo blanco = 0xFF");
        }
    }

    @Test
    public void testConvert8x8_Alternating(@TempDir File tempDir) throws IOException {
        // Patrón: alternancia 10101010 (0xAA)
        boolean[][] pattern = new boolean[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                pattern[y][x] = (x % 2 == 0);  // x=0,2,4,6 = true (bits 7,5,3,1)
            }
        }

        File png = createTestPng(8, 8, pattern, tempDir);
        ZxBitmapExportOptions opts = ZxBitmapExportOptions.defaults("alternating", 8, 8);
        byte[] result = converter.convertPngToMonochrome(png, opts);

        assertEquals(8, result.length, "8x8 = 8 bytes (1 por fila)");
        for (byte b : result) {
            assertEquals(0xAA, b & 0xFF, "Alternancia 10101010 = 0xAA en cada fila");
        }
    }

    @Test
    public void testConvert16x8(@TempDir File tempDir) throws IOException {
        boolean[][] pattern = new boolean[8][16];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                pattern[y][x] = (y < 4);  // Primera mitad blanca, segunda negra
            }
        }

        File png = createTestPng(16, 8, pattern, tempDir);
        ZxBitmapExportOptions opts = ZxBitmapExportOptions.defaults("half", 16, 8);
        byte[] result = converter.convertPngToMonochrome(png, opts);

        assertEquals(16, result.length, "16x8 = 16 bytes (2 por fila × 8 filas)");
        // Primeras 4 filas: todo 0xFF (blanco) en ambos bytes
        for (int i = 0; i < 8; i += 2) {
            assertEquals(0xFF, result[i] & 0xFF, "Primera mitad de fila blanca");
            assertEquals(0xFF, result[i + 1] & 0xFF, "Segunda mitad de fila blanca");
        }
        // Últimas 4 filas: todo 0x00 (negro)
        for (int i = 8; i < 16; i += 2) {
            assertEquals(0x00, result[i] & 0xFF, "Primera mitad de fila negra");
            assertEquals(0x00, result[i + 1] & 0xFF, "Segunda mitad de fila negra");
        }
    }

    @Test
    public void testConvert256x1(@TempDir File tempDir) throws IOException {
        // Una fila de 256 píxeles = 32 bytes
        boolean[][] pattern = new boolean[1][256];
        for (int x = 0; x < 256; x++) {
            pattern[0][x] = true;  // Todo blanco
        }

        File png = createTestPng(256, 1, pattern, tempDir);
        ZxBitmapExportOptions opts = ZxBitmapExportOptions.defaults("wide", 256, 1);
        byte[] result = converter.convertPngToMonochrome(png, opts);

        assertEquals(32, result.length, "256x1 = 32 bytes");
        for (int i = 0; i < 32; i++) {
            assertEquals(0xFF, result[i] & 0xFF, "Todos blanco");
        }
    }

    @Test
    public void testConvert_InvalidPngFile() {
        File nonexistent = new File("/nonexistent/file.png");
        ZxBitmapExportOptions opts = ZxBitmapExportOptions.defaults("test", 8, 8);
        assertThrows(IllegalArgumentException.class, () -> converter.convertPngToMonochrome(nonexistent, opts));
    }

    @Test
    public void testConvert_InvalidDimensions_WidthNotMultiple8(@TempDir File tempDir) throws IOException {
        boolean[][] pattern = new boolean[8][7];
        File png = createTestPng(7, 8, pattern, tempDir);

        // Las opciones deben especificar múltiplo de 8
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "bad", 7, 8, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );

        assertThrows(IllegalArgumentException.class, () -> converter.convertPngToMonochrome(png, opts));
    }

    @Test
    public void testConvert_ZeroWidth() {
        File dummy = new File("dummy.png");
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "bad", 0, 8, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );

        assertThrows(IllegalArgumentException.class, () -> converter.convertPngToMonochrome(dummy, opts));
    }

    @Test
    public void testConvert_ZeroHeight() {
        File dummy = new File("dummy.png");
        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "bad", 8, 0, 128, ZxBitmapLayout.LINEAR, false, false, ""
        );

        assertThrows(IllegalArgumentException.class, () -> converter.convertPngToMonochrome(dummy, opts));
    }

    @Test
    public void testConvert_MonochromeThreshold(@TempDir File tempDir) throws IOException {
        // Imagen con píxeles grises oscuros (RGB 50,50,50) y blancos (255,255,255)
        // Luminancia gris = 0.299*50 + 0.587*50 + 0.114*50 = 50
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (y < 4) {
                    img.setRGB(x, y, new Color(50, 50, 50).getRGB());  // Gris oscuro
                } else {
                    img.setRGB(x, y, new Color(255, 255, 255).getRGB());  // Blanco
                }
            }
        }

        File png = new File(tempDir, "gray.png");
        ImageIO.write(img, "PNG", png);

        // Con threshold 100: gris oscuro será negro, blanco será blanco
        ZxBitmapExportOptions opts1 = new ZxBitmapExportOptions(
            "gray_100", 8, 8, 100, ZxBitmapLayout.LINEAR, false, false, ""
        );
        byte[] result1 = converter.convertPngToMonochrome(png, opts1);
        assertEquals(8, result1.length);
        // Primeras 4 bytes (filas 0-3 gris oscuro) = 0x00, últimas 4 (filas 4-7 blancas) = 0xFF
        for (int i = 0; i < 4; i++) {
            assertEquals(0x00, result1[i] & 0xFF, "Fila " + i + " gris oscuro (lum=50) con threshold 100 = negro");
        }
        for (int i = 4; i < 8; i++) {
            assertEquals(0xFF, result1[i] & 0xFF, "Fila " + i + " blanca = blanco");
        }

        // Con threshold 40: ambos serán blanco
        ZxBitmapExportOptions opts2 = new ZxBitmapExportOptions(
            "gray_40", 8, 8, 40, ZxBitmapLayout.LINEAR, false, false, ""
        );
        byte[] result2 = converter.convertPngToMonochrome(png, opts2);
        assertEquals(8, result2.length);
        for (byte b : result2) {
            assertEquals(0xFF, b & 0xFF, "Todo blanco con threshold 40");
        }
    }

    @Test
    public void testValidation_ZxScreenLayoutRequires256x192(@TempDir File tempDir) throws IOException {
        boolean[][] pattern = new boolean[8][8];
        File png = createTestPng(8, 8, pattern, tempDir);

        ZxBitmapExportOptions opts = new ZxBitmapExportOptions(
            "test", 8, 8, 128, ZxBitmapLayout.ZX_SCREEN_LAYOUT, false, false, ""
        );

        assertThrows(IllegalArgumentException.class, () -> converter.convertPngToMonochrome(png, opts));
    }

    @Test
    public void testConvert_BitPackingOrder(@TempDir File tempDir) throws IOException {
        // Especificamos un patrón conocido: 01010101 = 0x55
        // Bits: 7=0, 6=1, 5=0, 4=1, 3=0, 2=1, 1=0, 0=1
        // Píxeles: B,W,B,W,B,W,B,W
        boolean[][] pattern = new boolean[1][8];
        pattern[0][0] = false;  // Bit 7 = 0
        pattern[0][1] = true;   // Bit 6 = 1
        pattern[0][2] = false;  // Bit 5 = 0
        pattern[0][3] = true;   // Bit 4 = 1
        pattern[0][4] = false;  // Bit 3 = 0
        pattern[0][5] = true;   // Bit 2 = 1
        pattern[0][6] = false;  // Bit 1 = 0
        pattern[0][7] = true;   // Bit 0 = 1

        File png = createTestPng(8, 1, pattern, tempDir);
        ZxBitmapExportOptions opts = ZxBitmapExportOptions.defaults("bitorder", 8, 1);
        byte[] result = converter.convertPngToMonochrome(png, opts);

        assertEquals(1, result.length);
        assertEquals(0x55, result[0] & 0xFF, "Patrón BWBWBWBW = 0x55");
    }
}
