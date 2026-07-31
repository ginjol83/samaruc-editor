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
