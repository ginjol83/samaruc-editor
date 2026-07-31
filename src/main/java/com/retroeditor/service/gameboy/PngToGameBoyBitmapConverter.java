package com.retroeditor.service.gameboy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Conversor de PNG a bytes en formato Game Boy 2bpp (2 bits por pixel).
 *
 * Responsabilidades:
 * - Cargar PNG con validación
 * - Normalizar dimensiones (validar múltiplos de 8)
 * - Convertir RGB a 4 colores (0-3) usando cuantización
 * - Empaquetar pixels a bytes en formato 2bpp
 *
 * Diseño:
 * - Sin dependencias JavaFX (reutilizable en plugins, CLI, etc.)
 * - Métodos públicos validados y documentados
 * - Excepciones descriptivas
 */
public class PngToGameBoyBitmapConverter {
    private static final int PIXELS_PER_BYTE = 4; // 4 píxeles por byte en 2bpp

    /**
     * Convierte un archivo PNG a bytes en formato Game Boy 2bpp.
     *
     * @param pngFile archivo PNG a cargar
     * @param options opciones de conversión (validadas internamente)
     * @return array de bytes en formato 2bpp
     * @throws IOException si el PNG no se puede leer
     * @throws IllegalArgumentException si las opciones o dimensiones son inválidas
     */
    public byte[] convertPngTo2bpp(File pngFile, GameBoyBitmapExportOptions options) throws IOException {
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

        // Convertir a índices de color (0-3 para Game Boy)
        byte[] colorIndices = rgbTo4Color(image, options.width(), options.height(),
                                         options.contrastLevel(), options.invertColors());

        // Empaquetar en formato 2bpp
        return pack2bpp(colorIndices, options.width(), options.height());
    }

    /**
     * Convierte una imagen RGB a índices de color Game Boy (0-3).
     * Usa cuantización simple de colores más cercanos.
     *
     * @param image imagen BufferedImage a convertir
     * @param targetWidth ancho esperado (crop/pad si difiere)
     * @param targetHeight alto esperado
     * @param contrastLevel nivel de contraste (-50 a 50)
     * @param invertColors si true, invierte los colores
     * @return array de bytes con índices de color [0-3]
     */
    private byte[] rgbTo4Color(BufferedImage image, int targetWidth, int targetHeight,
                               int contrastLevel, boolean invertColors) {
        byte[] result = new byte[targetWidth * targetHeight];
        
        int imgWidth = image.getWidth();
        int imgHeight = image.getHeight();
        GameBoyColorQuantizer quantizer = new GameBoyColorQuantizer();

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int rgb;
                if (x >= imgWidth || y >= imgHeight) {
                    // Fuera de la imagen: color blanco (índice 3)
                    rgb = 0xFFFFFF;
                } else {
                    rgb = image.getRGB(x, y);
                    // Aplicar contraste
                    rgb = applyContrast(rgb, contrastLevel);
                    // Aplicar inversión si está habilitada
                    if (invertColors) {
                        rgb = invertRgb(rgb);
                    }
                }
                
                byte colorIdx = quantizer.quantize(rgb);
                result[y * targetWidth + x] = colorIdx;
            }
        }

        return result;
    }

    /**
     * Aplica contraste a un color RGB.
     * 
     * @param rgb valor RGB 0xRRGGBB
     * @param contrastLevel nivel de contraste (-50 a 50)
     * @return RGB con contraste aplicado
     */
    private int applyContrast(int rgb, int contrastLevel) {
        if (contrastLevel == 0) return rgb;

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Aplicar contraste: mover valores hacia 0 o 255
        double factor = 1.0 + (contrastLevel / 100.0);
        int centerValue = 128;

        r = clampByte((int) (centerValue + (r - centerValue) * factor));
        g = clampByte((int) (centerValue + (g - centerValue) * factor));
        b = clampByte((int) (centerValue + (b - centerValue) * factor));

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Invierte un color RGB (complemento).
     */
    private int invertRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        r = 255 - r;
        g = 255 - g;
        b = 255 - b;

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Limita un valor a rango [0, 255].
     */
    private int clampByte(int value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }

    /**
     * Empaqueta pixels de 2 bits (índices 0-3) en bytes.
     * 
     * Formato Game Boy 2bpp por fila:
     * - Byte 0 (low):  bits 7-0 = píxeles 0-7, bit = bit bajo del color
     * - Byte 1 (high): bits 7-0 = píxeles 0-7, bit = bit alto del color
     *
     * @param colorIndices array de índices [0-3]
     * @param width ancho en píxeles
     * @param height alto en píxeles
     * @return array de bytes empaquetados en formato 2bpp
     */
    private byte[] pack2bpp(byte[] colorIndices, int width, int height) {
        int bytesPerRow = width / PIXELS_PER_BYTE * 2; // 2 bytes por fila (low + high)
        byte[] result = new byte[bytesPerRow * height];

        int resultIdx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x += PIXELS_PER_BYTE) {
                int lowByte = 0;
                int highByte = 0;

                for (int i = 0; i < PIXELS_PER_BYTE; i++) {
                    int pixelIdx = y * width + x + i;
                    if (pixelIdx < colorIndices.length) {
                        int colorValue = colorIndices[pixelIdx] & 0x03;
                        int bitPos = 7 - i;
                        
                        // Bit 0 va al byte low, bit 1 va al byte high
                        if ((colorValue & 0x01) != 0) {
                            lowByte |= (1 << bitPos);
                        }
                        if ((colorValue & 0x02) != 0) {
                            highByte |= (1 << bitPos);
                        }
                    }
                }

                result[resultIdx++] = (byte) lowByte;
                result[resultIdx++] = (byte) highByte;
            }
        }

        return result;
    }
}
