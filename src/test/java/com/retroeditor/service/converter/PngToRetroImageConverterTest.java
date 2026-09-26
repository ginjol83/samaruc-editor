package com.retroeditor.service.converter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PngToRetroImageConverterTest {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private final PngToRetroImageConverter converter = new PngToRetroImageConverter();

    @Test
    void convertPixels_ForCpcMode0_PacksOneBitPerPixel() {
        int[] pixels = new int[8];
        Arrays.fill(pixels, WHITE);
        pixels[0] = BLACK;

        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.CPC_MODE_0, 8, 1);

        assertEquals(1, conversion.layers().size());
        assertEquals(1, conversion.totalBytes());
        assertEquals((byte) 0x7F, conversion.layers().get(0).data()[0], "El primer pixel negro, los demas claros");
        assertArrayEquals(new int[] { BLACK, 0xFF8080FF }, conversion.palette(),
                "El CPC no tiene blanco puro: el azul pastel es la tinta mas clara");
    }

    @Test
    void convertPixels_ForCpcMode2_KeepsTheHardwareInkNumbers() {
        int[] pixels = new int[8];
        Arrays.fill(pixels, 0xFF800000);
        pixels[0] = 0xFF00FF00;
        pixels[1] = 0xFF000080;

        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.CPC_MODE_2, 8, 1);

        assertEquals(4, conversion.totalBytes(), "4bpp: 2 pixeles por byte, 8 pixeles = 4 bytes");
        assertEquals(3, conversion.palette().length, "Solo se exportan los tres colores usados");
        assertEquals(1, conversion.hardwareIndex(0), "azul = tinta 1");
        assertEquals(3, conversion.hardwareIndex(1), "rojo = tinta 3");
        assertEquals(9, conversion.hardwareIndex(2), "verde = tinta 9");
        assertEquals(2, conversion.indexAt(0, 0), "el verde es el segundo color mas usado");
        assertEquals(0, conversion.indexAt(1, 0), "el azul es la tinta 1, la mas baja de las tres");
        assertEquals(1, conversion.indexAt(2, 0), "el rojo es la tinta 3");
    }

    @Test
    void convertPixels_ForMasterSystem_ForcesTransparencyAndEmitsCram() {
        int[] pixels = new int[8 * 8];
        Arrays.fill(pixels, 0xFFFF0000);
        pixels[0] = 0x00FFFFFF;

        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.MASTER_SYSTEM_TILES, 8, 8);

        assertEquals(0, conversion.indexAt(0, 0), "El pixel transparente se queda en el indice 0");
        assertNotNull(conversion.cram());
        assertEquals(2, conversion.cram().length, "Solo se exportan los colores usados");
        assertEquals(0x00, conversion.cram()[0] & 0xFF, "El color 0 del TMS9918 es transparente");
        assertEquals(0x03, conversion.cram()[1] & 0xFF, "Rojo claro = %000011");
        assertEquals(32, conversion.totalBytes(), "Un tile 8x8 de 4bpp ocupa 32 bytes");
    }

    @Test
    void convertPixels_ForC64Charset_SplitsCharactersAndColorRam() {
        int[] pixels = new int[8 * 8];
        Arrays.fill(pixels, BLACK);
        pixels[0] = 0xFFB8C76F;

        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.C64_CHARSET, 8, 8);

        assertEquals(2, conversion.layers().size());
        assertEquals(8, conversion.layers().get(0).data().length);
        assertEquals(1, conversion.layers().get(1).data().length);
        assertEquals((byte) 0x80, conversion.layers().get(0).data()[0]);
        assertEquals(7, conversion.hardwareIndex(1), "El color claro del VIC-II es el 7");
        assertEquals((byte) 0x70, conversion.layers().get(1).data()[0], "Primer plano 7, fondo 0");
    }

    @Test
    void convertPixels_ForAtariMode7_UsesPriorThree() {
        int[] pixels = new int[8];
        Arrays.fill(pixels, WHITE);
        pixels[0] = BLACK;

        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.ATARI_MODE_7, 8, 1);

        assertEquals(4, conversion.totalBytes());
        assertEquals((byte) 0xC4, conversion.layers().get(0).data()[0],
                "PRIOR 3 con el pixel 0 en el color 0 y el pixel 1 en el color 1");
    }

    @Test
    void convertPixels_ForGameGear_UsesTwelveBitPaletteValues() {
        int[] pixels = new int[8 * 8];
        Arrays.fill(pixels, 0xFFFF0000);

        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.GAME_GEAR_TILES, 8, 8);

        assertNotNull(conversion.cram());
        assertEquals(1, conversion.cram().length);
        assertTrue(conversion.cram()[0] >= 0 && conversion.cram()[0] <= 0x0FFF,
                "Los valores de paleta del GG caben en 12 bits");
        assertEquals(0x007, conversion.cram()[0], "Rojo claro: R=7, G=0, B=0");
    }

    @Test
    void convertPixels_WithMoreTonesThanColours_KeepsThemCloseToTheOriginal() {
        // Degradado de 16 tonos de gris: mas de los 2 colores del modo 0 del CPC.
        int width = 16;
        int height = 8;
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int grey = 16 + i * 14;
            pixels[i] = 0xFF000000 | grey << 16 | grey << 8 | grey;
        }

        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.CPC_MODE_0, width, height);

        double error = meanSquaredError(pixels, conversion);
        double naive = meanSquaredErrorToConstant(pixels, conversion.palette()[0]);
        assertTrue(error < naive / 2,
                "Los tonos descartados tienen que ir al color conservado mas cercano, no todos al primero: "
                        + error + " frente a " + naive);
    }

    /** Error cuadratico medio entre los pixeles originales y los ya cuantizados. */
    private static double meanSquaredError(int[] pixels, RetroImageConversion conversion) {
        double total = 0;
        for (int i = 0; i < pixels.length; i++) {
            int index = conversion.indexAt(i % conversion.width(), i / conversion.width());
            total += squaredDistance(pixels[i], conversion.palette()[index]);
        }
        return total / pixels.length;
    }

    /** El mismo error si todos los pixeles acabaran en un unico color. */
    private static double meanSquaredErrorToConstant(int[] pixels, int color) {
        double total = 0;
        for (int pixel : pixels) {
            total += squaredDistance(pixel, color);
        }
        return total / pixels.length;
    }

    private static double squaredDistance(int one, int other) {
        double dr = ((one >> 16) & 0xFF) - ((other >> 16) & 0xFF);
        double dg = ((one >> 8) & 0xFF) - ((other >> 8) & 0xFF);
        double db = (one & 0xFF) - (other & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    @Test
    void convertPixels_WithWrongPixelCount_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertPixels(new int[4], RetroImageMode.CPC_MODE_0, 8, 1));
    }

    @Test
    void convertPixels_WithUnalignedSize_Throws() {
        int[] pixels = new int[6 * 8];

        assertThrows(IllegalArgumentException.class,
                () -> converter.convertPixels(pixels, RetroImageMode.CPC_MODE_1, 6, 8));
        assertThrows(IllegalArgumentException.class,
                () -> converter.convertPixels(pixels, RetroImageMode.MASTER_SYSTEM_TILES, 8, 6));
    }

    @Test
    void convertPixels_WithTooBigImage_Throws() {
        int[] pixels = new int[8];

        assertThrows(IllegalArgumentException.class,
                () -> converter.convertPixels(pixels, RetroImageMode.CPC_MODE_0, 2000, 1));
    }

    @Test
    void convert_ScalesTheImageToTheRequestedSize(@TempDir Path tempDir) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, BLACK);
        image.setRGB(1, 0, WHITE);
        image.setRGB(0, 1, BLACK);
        image.setRGB(1, 1, WHITE);
        File png = tempDir.resolve("origen.png").toFile();
        ImageIO.write(image, "png", png);

        RetroImageConversion conversion =
                converter.convert(png, RetroImageMode.CPC_MODE_0, 8, 1);

        assertEquals(8, conversion.width());
        assertEquals(1, conversion.totalBytes(), "1bpp: 8 pixeles por byte");
        assertEquals((byte) 0x0F, conversion.layers().get(0).data()[0],
                "La mitad izquierda de la imagen se escala a los 4 pixeles negros");
    }

    @Test
    void convert_WithMissingFile_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert(new File("no-existe.png"), RetroImageMode.CPC_MODE_0, 8, 1));
    }

    @Test
    void convert_WithNonImageFile_Throws(@TempDir Path tempDir) throws IOException {
        File notAnImage = tempDir.resolve("texto.txt").toFile();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", notAnImage);
        byte[] raw = "esto no es una imagen".getBytes("UTF-8");
        java.nio.file.Files.write(notAnImage.toPath(), raw);

        assertThrows(IOException.class,
                () -> converter.convert(notAnImage, RetroImageMode.CPC_MODE_0, 8, 1));
    }
}
