package com.retroeditor.service.gameboy;

/**
 * Cuantizador de colores para Game Boy.
 * Mapea colores RGB a 4 índices de color (0-3).
 *
 * Paleta predeterminada de Game Boy:
 * - 0: Blanco (#FFFFFF)
 * - 1: Gris claro (#AAAAAA)
 * - 2: Gris oscuro (#555555)
 * - 3: Negro (#000000)
 */
public class GameBoyColorQuantizer {
    // Paleta de colores Game Boy (RGB)
    private static final int[][] GB_PALETTE = {
        { 255, 255, 255 }, // 0: Blanco
        { 170, 170, 170 }, // 1: Gris claro
        { 85, 85, 85 },    // 2: Gris oscuro
        { 0, 0, 0 }        // 3: Negro
    };

    /**
     * Cuantiza un color RGB a un índice de Game Boy (0-3).
     * Busca el color más cercano en la paleta.
     *
     * @param rgb valor RGB en formato 0xRRGGBB
     * @return índice de color (0-3)
     */
    public byte quantize(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        int bestIdx = 0;
        double bestDistance = Double.MAX_VALUE;

        // Buscar el color más cercano
        for (int i = 0; i < GB_PALETTE.length; i++) {
            double distance = colorDistance(r, g, b, GB_PALETTE[i][0], GB_PALETTE[i][1], GB_PALETTE[i][2]);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIdx = i;
            }
        }

        return (byte) bestIdx;
    }

    /**
     * Cuantiza una imagen completa usando dithering Floyd-Steinberg.
     * Mejor para degradados y fotografías.
     *
     * @param image datos RGB de la imagen
     * @param width ancho
     * @param height alto
     * @return array con índices de color [0-3]
     */
    public byte[] quantizeWithFloydSteinberg(int[] imageRgb, int width, int height) {
        // Crear copia de trabajo con valores float para error
        float[][] r = new float[height][width];
        float[][] g = new float[height][width];
        float[][] b = new float[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int rgb = imageRgb[idx];
                r[y][x] = (rgb >> 16) & 0xFF;
                g[y][x] = (rgb >> 8) & 0xFF;
                b[y][x] = rgb & 0xFF;
            }
        }

        byte[] result = new byte[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Cuantizar pixel actual
                int oldR = Math.round(r[y][x]);
                int oldG = Math.round(g[y][x]);
                int oldB = Math.round(b[y][x]);
                oldR = clamp(oldR);
                oldG = clamp(oldG);
                oldB = clamp(oldB);

                byte colorIdx = quantizeRgb(oldR, oldG, oldB);
                result[y * width + x] = colorIdx;

                // Obtener color elegido
                int[] chosenColor = GB_PALETTE[colorIdx & 0x03];
                int newR = chosenColor[0];
                int newG = chosenColor[1];
                int newB = chosenColor[2];

                // Calcular error
                float errR = oldR - newR;
                float errG = oldG - newG;
                float errB = oldB - newB;

                // Distribuir error según Floyd-Steinberg
                // (7/16) a derecha, (3/16) abajo-izq, (5/16) abajo, (1/16) abajo-der
                if (x + 1 < width) {
                    r[y][x + 1] += errR * 7.0f / 16.0f;
                    g[y][x + 1] += errG * 7.0f / 16.0f;
                    b[y][x + 1] += errB * 7.0f / 16.0f;
                }
                if (y + 1 < height) {
                    if (x > 0) {
                        r[y + 1][x - 1] += errR * 3.0f / 16.0f;
                        g[y + 1][x - 1] += errG * 3.0f / 16.0f;
                        b[y + 1][x - 1] += errB * 3.0f / 16.0f;
                    }
                    r[y + 1][x] += errR * 5.0f / 16.0f;
                    g[y + 1][x] += errG * 5.0f / 16.0f;
                    b[y + 1][x] += errB * 5.0f / 16.0f;
                    if (x + 1 < width) {
                        r[y + 1][x + 1] += errR * 1.0f / 16.0f;
                        g[y + 1][x + 1] += errG * 1.0f / 16.0f;
                        b[y + 1][x + 1] += errB * 1.0f / 16.0f;
                    }
                }
            }
        }

        return result;
    }

    private byte quantizeRgb(int r, int g, int b) {
        int bestIdx = 0;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < GB_PALETTE.length; i++) {
            double distance = colorDistance(r, g, b, GB_PALETTE[i][0], GB_PALETTE[i][1], GB_PALETTE[i][2]);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIdx = i;
            }
        }

        return (byte) bestIdx;
    }

    private int clamp(int value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }

    /**
     * Calcula la distancia Euclidiana entre dos colores RGB.
     */
    private double colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        double dr = r1 - r2;
        double dg = g1 - g2;
        double db = b1 - b2;
        
        // Usar ponderación para luminancia (es más perceptible)
        dr *= 0.299;
        dg *= 0.587;
        db *= 0.114;
        
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * Retorna el valor RGB de un índice de color Game Boy.
     *
     * @param colorIdx índice (0-3)
     * @return valor RGB 0xRRGGBB
     */
    public int getRgb(int colorIdx) {
        if (colorIdx < 0 || colorIdx >= GB_PALETTE.length) {
            colorIdx = 0;
        }
        int[] color = GB_PALETTE[colorIdx];
        return (color[0] << 16) | (color[1] << 8) | color[2];
    }
}
