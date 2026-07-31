package com.retroeditor.service.zxspectrum;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversor de PNG a bytes en formato ZX Spectrum 1bpp (1 bit por pixel).
 *
 * Responsabilidades:
 * - Cargar PNG con validación
 * - Normalizar dimensiones (validar múltiplo de 8)
 * - Convertir RGB a monocromo usando umbral configurable
 * - Empaquetar pixels a bytes (bit 7 = pixel izquierdo, bit 0 = derecho)
 * - Soportar layouts lineal y ZX screen (v1: solo lineal)
 *
 * Diseño:
 * - Sin dependencias JavaFX (reutilizable en plugins, CLI, etc.)
 * - Métodos públicos validados y documentados
 * - Excepciones descriptivas
 */
public class PngToZxBitmapConverter {
    /**
     * Número de píxeles por byte en formato 1bpp.
     */
    private static final int PIXELS_PER_BYTE = 8;

    /**
     * Matriz de Bayer 4x4 para dithering ordenado.
     * Valores normalizados a rango 0-15.
     * Usada para mejorar la conversión monocromo con patrón visual.
     */
    private static final int[][] BAYER_MATRIX_4x4 = {
        {  0,  8,  2, 10 },
        { 12,  4, 14,  6 },
        {  3, 11,  1,  9 },
        { 15,  7, 13,  5 }
    };

    /**
     * Convierte un archivo PNG a bytes en formato ZX Spectrum 1bpp.
     *
     * @param pngFile archivo PNG a cargar
     * @param options opciones de conversión (validadas internamente)
     * @return array de bytes en formato 1bpp según layout especificado
     * @throws IOException si el PNG no se puede leer
     * @throws IllegalArgumentException si las opciones o dimensiones son inválidas
     */
    public byte[] convertPngToMonochrome(File pngFile, ZxBitmapExportOptions options) throws IOException {
        return convertPngToMonochrome(pngFile, options, false);
    }

    /**
     * Convierte un archivo PNG a bytes en formato ZX Spectrum 1bpp con opción de dithering.
     *
     * @param pngFile archivo PNG a cargar
     * @param options opciones de conversión (validadas internamente)
     * @param useDithering si true, aplica dithering de Bayer para mejor resultado visual
     * @return array de bytes en formato 1bpp según layout especificado
     * @throws IOException si el PNG no se puede leer
     * @throws IllegalArgumentException si las opciones o dimensiones son inválidas
     */
    public byte[] convertPngToMonochrome(File pngFile, ZxBitmapExportOptions options, boolean useDithering) throws IOException {
        options.validate();

        if (!pngFile.exists() || !pngFile.isFile()) {
            throw new IllegalArgumentException("El archivo PNG no existe: " + pngFile);
        }

        BufferedImage image;
        try {
            image = ImageIO.read(pngFile);
        } catch (Exception e) {
            throw new IOException("No se puede leer el archivo PNG: " + pngFile, e);
        }

        if (image == null) {
            throw new IOException("El archivo no parece ser un PNG válido: " + pngFile);
        }

        // Convertir a monocromo (matriz booleana: true = blanco, false = negro)
        boolean[][] monochrome = rgbToMonochrome(image, options.width(), options.height(), 
                                                 options.monochromeThreshold(), useDithering);

        // Empaquetar según layout
        return switch (options.layout()) {
            case LINEAR -> packLinearLayout(monochrome, options.width(), options.height());
            case ZX_SCREEN_LAYOUT -> packZxScreenLayout(monochrome, options.width(), options.height());
        };
    }

    /**
     * Convierte una sprite sheet PNG a múltiples frames ZX Spectrum 1bpp.
     *
     * @param spriteSheetPng archivo PNG con la sprite sheet
     * @param frameWidth ancho de cada frame (debe ser múltiplo de 8)
     * @param frameHeight alto de cada frame
     * @param frameCountX número de frames por fila
     * @param frameCountY número de frames por columna
     * @param options opciones de conversión (layout, threshold, etc.)
     * @param useDithering si true, aplica dithering
     * @return ZxMultiframeSprite con todos los frames extraídos
     * @throws IOException si el PNG no se puede leer
     * @throws IllegalArgumentException si los parámetros son inválidos
     */
    public ZxMultiframeSprite convertSpriteSheet(File spriteSheetPng, int frameWidth, int frameHeight,
                                                  int frameCountX, int frameCountY,
                                                  ZxBitmapExportOptions options,
                                                  boolean useDithering) throws IOException {
        if (!spriteSheetPng.exists() || !spriteSheetPng.isFile()) {
            throw new IllegalArgumentException("Sprite sheet file does not exist: " + spriteSheetPng);
        }

        BufferedImage sheetImage;
        try {
            sheetImage = ImageIO.read(spriteSheetPng);
        } catch (Exception e) {
            throw new IOException("Cannot read sprite sheet PNG: " + spriteSheetPng, e);
        }

        if (sheetImage == null) {
            throw new IOException("File does not appear to be a valid PNG: " + spriteSheetPng);
        }

        // Validar dimensiones
        int expectedSheetWidth = frameWidth * frameCountX;
        int expectedSheetHeight = frameHeight * frameCountY;
        if (sheetImage.getWidth() < expectedSheetWidth || sheetImage.getHeight() < expectedSheetHeight) {
            throw new IllegalArgumentException(
                String.format("Sprite sheet too small: expected %dx%d but got %dx%d",
                    expectedSheetWidth, expectedSheetHeight, sheetImage.getWidth(), sheetImage.getHeight())
            );
        }

        // Extraer cada frame
        List<byte[]> frames = new java.util.ArrayList<>();
        for (int frameY = 0; frameY < frameCountY; frameY++) {
            for (int frameX = 0; frameX < frameCountX; frameX++) {
                BufferedImage frameImage = extractFrameImage(sheetImage, frameX, frameY, frameWidth, frameHeight);
                byte[] frameBytes = convertFrameToBytes(frameImage, frameWidth, frameHeight, options, useDithering);
                frames.add(frameBytes);
            }
        }

        return new ZxMultiframeSprite(
            options.arrayName(),
            frameWidth,
            frameHeight,
            frameCountX,
            frameCountY,
            frames,
            options.layout()
        );
    }

    /**
     * Extrae una región rectangular de una imagen.
     */
    private BufferedImage extractFrameImage(BufferedImage sheet, int frameX, int frameY,
                                           int frameWidth, int frameHeight) {
        int startX = frameX * frameWidth;
        int startY = frameY * frameHeight;
        return sheet.getSubimage(startX, startY, frameWidth, frameHeight);
    }

    /**
     * Convierte un frame individual a bytes ZX.
     */
    private byte[] convertFrameToBytes(BufferedImage frameImage, int frameWidth, int frameHeight,
                                       ZxBitmapExportOptions options, boolean useDithering) {
        boolean[][] monochrome = rgbToMonochrome(frameImage, frameWidth, frameHeight,
                                                 options.monochromeThreshold(), useDithering);

        return switch (options.layout()) {
            case LINEAR -> packLinearLayout(monochrome, frameWidth, frameHeight);
            case ZX_SCREEN_LAYOUT -> packZxScreenLayout(monochrome, frameWidth, frameHeight);
        };
    }
    /**
     *
     * @param image imagen BufferedImage a convertir
     * @param targetWidth ancho esperado (crop/pad si difiere)
     * @param targetHeight alto esperado
     * @param threshold valor 0-255; píxeles >= threshold = blanco (true)
     * @param useDithering si true, aplica dithering de Bayer; si false, umbral simple
     * @return matriz booleana [alto][ancho]
     */
    private boolean[][] rgbToMonochrome(BufferedImage image, int targetWidth, int targetHeight, 
                                        int threshold, boolean useDithering) {
        boolean[][] result = new boolean[targetHeight][targetWidth];

        int imgWidth = image.getWidth();
        int imgHeight = image.getHeight();

        if (useDithering) {
            return rgbToMonochromeWithDithering(image, result, targetWidth, targetHeight, imgWidth, imgHeight, threshold);
        } else {
            return rgbToMonochromeSimpleThreshold(image, result, targetWidth, targetHeight, imgWidth, imgHeight, threshold);
        }
    }

    /**
     * Conversión simple con umbral (método original).
     */
    private boolean[][] rgbToMonochromeSimpleThreshold(BufferedImage image, boolean[][] result,
                                                       int targetWidth, int targetHeight,
                                                       int imgWidth, int imgHeight, int threshold) {
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                if (x >= imgWidth || y >= imgHeight) {
                    result[y][x] = false;
                } else {
                    int rgb = image.getRGB(x, y);
                    int luminance = calculateLuminance(rgb);
                    result[y][x] = luminance >= threshold;
                }
            }
        }
        return result;
    }

    /**
     * Conversión con dithering de Bayer 4x4.
     * Produce un patrón visual que imita degradados suavemente.
     */
    private boolean[][] rgbToMonochromeWithDithering(BufferedImage image, boolean[][] result,
                                                     int targetWidth, int targetHeight,
                                                     int imgWidth, int imgHeight, int threshold) {
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                if (x >= imgWidth || y >= imgHeight) {
                    result[y][x] = false;
                } else {
                    int rgb = image.getRGB(x, y);
                    int luminance = calculateLuminance(rgb);

                    // Obtener valor dither de la matriz (repetida si es necesario)
                    int ditherX = x % BAYER_MATRIX_4x4[0].length;
                    int ditherY = y % BAYER_MATRIX_4x4.length;
                    int ditherValue = BAYER_MATRIX_4x4[ditherY][ditherX];

                    // Convertir threshold y dither a rango comparable
                    int adjustedThreshold = threshold - 128 + (ditherValue - 7);
                    result[y][x] = luminance >= adjustedThreshold;
                }
            }
        }
        return result;
    }

    /**
     * Calcula la luminancia (brillo) de un color RGB usando fórmula estándar.
     * Rango: 0-255, donde 0 es negro y 255 es blanco.
     *
     * @param rgb valor RGB en formato 0xAARGBB
     * @return valor de luminancia 0-255
     */
    private int calculateLuminance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Fórmula ITU-R BT.601
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    /**
     * Empaqueta pixels monocromos en layout lineal (fila por fila).
     *
     * Convención de bits:
     * - Bit 7 = primer pixel de la fila (izquierdo)
     * - Bit 0 = octavo pixel de la fila (derecho)
     * - Pixel blanco (true) = bit 1
     * - Pixel negro (false) = bit 0
     *
     * @param mono matriz monocromo [alto][ancho]
     * @param width ancho (debe ser múltiplo de 8)
     * @param height alto
     * @return array de bytes empaquetados
     */
    private byte[] packLinearLayout(boolean[][] mono, int width, int height) {
        int bytesPerRow = width / PIXELS_PER_BYTE;
        byte[] result = new byte[bytesPerRow * height];

        int byteIndex = 0;
        for (int y = 0; y < height; y++) {
            for (int byteCol = 0; byteCol < bytesPerRow; byteCol++) {
                int byteValue = 0;
                for (int bitPos = 0; bitPos < PIXELS_PER_BYTE; bitPos++) {
                    int pixelX = byteCol * PIXELS_PER_BYTE + bitPos;
                    int bit = 7 - bitPos;  // Bit 7 = primer pixel, bit 0 = octavo
                    if (mono[y][pixelX]) {
                        byteValue |= (1 << bit);
                    }
                }
                result[byteIndex++] = (byte) byteValue;
            }
        }

        return result;
    }

    /**
     * Empaqueta pixels monocromos en layout ZX Spectrum Screen (modo pantalla 256x192).
     *
     * Fórmula de dirección en memoria (correcta para ZX Spectrum):
     *   offset = (y >> 3) * 256 + (y & 7) * 32 + x_byte
     *
     * Donde:
     *   - (y >> 3) = y / 8 (character row, 0-23 para 192 líneas)
     *   - (y & 7) = y % 8 (line within character row, 0-7)
     *   - Rango válido: 0-6143 para 256x192
     *
     * @param mono matriz monocromo [alto][ancho]
     * @param width ancho (debe ser múltiplo de 8, típicamente 256)
     * @param height alto (típicamente 192)
     * @return array de bytes en orden ZX screen
     */
    private byte[] packZxScreenLayout(boolean[][] mono, int width, int height) {
        int bytesPerRow = width / PIXELS_PER_BYTE;
        byte[] result = new byte[bytesPerRow * height];

        // Procesar por orden de pantalla ZX
        for (int y = 0; y < height; y++) {
            for (int byteCol = 0; byteCol < bytesPerRow; byteCol++) {
                // Calcular offset según fórmula ZX Spectrum correcta
                int offset = ((y >> 3) * 256) + ((y & 7) * 32) + byteCol;

                // Empaquetar pixel
                int byteValue = 0;
                for (int bitPos = 0; bitPos < PIXELS_PER_BYTE; bitPos++) {
                    int pixelX = byteCol * PIXELS_PER_BYTE + bitPos;
                    int bit = 7 - bitPos;
                    if (mono[y][pixelX]) {
                        byteValue |= (1 << bit);
                    }
                }
                result[offset] = (byte) byteValue;
            }
        }

        return result;
    }

    /**
     * Convierte un archivo PNG a bytes en formato ZX Spectrum usando paleta de colores.
     *
     * Cada píxel se convierte a un color de la paleta ZX Spectrum (0-7 o bright).
     * Useful para imágenes más complejas donde la fidelidad de color es importante.
     *
     * @param pngFile archivo PNG a cargar
     * @param width ancho esperado (múltiplo de 8)
     * @param height alto esperado
     * @param useBrightPalette si true, usa colores bright del Spectrum
     * @return ZxColorImage con índices de color y paleta
     * @throws IOException si el PNG no se puede leer
     * @throws IllegalArgumentException si las dimensiones son inválidas
     */
    public ZxColorImage convertPngToColorIndexed(File pngFile, int width, int height, boolean useBrightPalette) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Las dimensiones deben ser > 0");
        }
        if (width % 8 != 0) {
            throw new IllegalArgumentException("El ancho debe ser múltiplo de 8, tienes: " + width);
        }

        if (!pngFile.exists() || !pngFile.isFile()) {
            throw new IllegalArgumentException("El archivo PNG no existe: " + pngFile);
        }

        BufferedImage image;
        try {
            image = ImageIO.read(pngFile);
        } catch (Exception e) {
            throw new IOException("No se puede leer el archivo PNG: " + pngFile, e);
        }

        if (image == null) {
            throw new IOException("El archivo no parece ser un PNG válido: " + pngFile);
        }

        int imgWidth = image.getWidth();
        int imgHeight = image.getHeight();

        // Convertir a índices de color
        ColorQuantizer quantizer = new ColorQuantizer(useBrightPalette);
        byte[] pixelData = new byte[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x >= imgWidth || y >= imgHeight) {
                    // Fuera de la imagen: color negro (índice 0)
                    pixelData[y * width + x] = 0;
                } else {
                    int rgb = image.getRGB(x, y);
                    ZxSpectrumColor color = quantizer.quantize(rgb);
                    pixelData[y * width + x] = (byte) color.colorIndex();
                }
            }
        }

        // Preparar paleta RGB para previsualización
        int[] paletteRgb = new int[32];
        for (int i = 0; i < 8; i++) {
            int[] rgb = quantizer.getRgb(i);
            paletteRgb[i * 4] = rgb[0];
            paletteRgb[i * 4 + 1] = rgb[1];
            paletteRgb[i * 4 + 2] = rgb[2];
            paletteRgb[i * 4 + 3] = 255;  // Alpha
        }

        return new ZxColorImage(pixelData, paletteRgb, width, height, useBrightPalette);
    }
}
