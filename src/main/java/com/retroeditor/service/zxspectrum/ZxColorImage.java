package com.retroeditor.service.zxspectrum;

/**
 * Datos de imagen ZX Spectrum a color.
 *
 * Record que encapsula:
 * - pixelData: índices de color (0-7) para cada píxel
 * - palette: colores RGB reales para previsualización
 * - width, height: dimensiones
 */
public record ZxColorImage(
    byte[] pixelData,        // Índices de color por píxel
    int[] paletteRgb,        // Colores RGB de la paleta (32 ints: 8 colores × 4 bytes ARGB)
    int width,
    int height,
    boolean isBrightPalette
) {
    /**
     * Obtiene el índice de color en la posición (x, y).
     */
    public int getPixelColor(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return 0;
        }
        return pixelData[y * width + x] & 0xFF;
    }

    /**
     * Retorna el color RGB de un índice de color.
     *
     * @param colorIndex índice (0-7)
     * @return color ARGB con alpha=255
     */
    public int getColorRgb(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= 8) {
            return 0xFF000000; // Negro
        }
        // paletteRgb contiene 8 colores RGB de 3 bytes cada uno
        // Convertimos a ARGB
        int idx = colorIndex * 4;
        int r = paletteRgb[idx] & 0xFF;
        int g = paletteRgb[idx + 1] & 0xFF;
        int b = paletteRgb[idx + 2] & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Valida la coherencia de los datos.
     */
    public void validate() {
        if (pixelData == null || pixelData.length == 0) {
            throw new IllegalArgumentException("pixelData no puede ser nulo o vacío");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensiones inválidas: " + width + "x" + height);
        }
        if (pixelData.length != width * height) {
            throw new IllegalArgumentException(
                String.format("pixelData size %d no coincide con %dx%d=%d",
                    pixelData.length, width, height, width * height)
            );
        }
        if (paletteRgb == null || paletteRgb.length != 32) {
            throw new IllegalArgumentException("paletteRgb debe tener 32 elementos (8 colores × 4)");
        }
    }
}
