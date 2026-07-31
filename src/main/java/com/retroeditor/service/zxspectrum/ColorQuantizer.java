package com.retroeditor.service.zxspectrum;

/**
 * Cuantizador de colores RGB a paleta ZX Spectrum.
 *
 * Responsabilidades:
 * - Reducir colores RGB a los 8 colores estándar del Spectrum
 * - Usar distancia Euclidiana en espacio RGB para encontrar mejor coincidencia
 * - Soportar tanto paleta normal como bright
 *
 * Uso:
 *   ColorQuantizer q = new ColorQuantizer(useBrightPalette);
 *   int r = (rgb >> 16) & 0xFF;
 *   int g = (rgb >> 8) & 0xFF;
 *   int b = rgb & 0xFF;
 *   ZxSpectrumColor color = q.quantize(r, g, b);
 */
public class ColorQuantizer {
    /**
     * Valores RGB reales de los colores ZX Spectrum.
     * Índices: 0=Black, 1=Blue, 2=Red, 3=Magenta, 4=Green, 5=Cyan, 6=Yellow, 7=White
     */
    private static final int[][] SPECTRUM_RGB = {
        {  0,   0,   0 },  // Black
        {  0,   0, 215 },  // Blue
        {215,   0,   0 },  // Red
        {215,   0, 215 },  // Magenta
        {  0, 215,   0 },  // Green
        {  0, 215, 215 },  // Cyan
        {215, 215,   0 },  // Yellow
        {215, 215, 215 },  // White
    };

    /**
     * Valores RGB reales de los colores ZX Spectrum BRIGHT.
     * Índices: 0=Bright Black (Gray), 1=Bright Blue, ..., 7=Bright White
     */
    private static final int[][] SPECTRUM_BRIGHT_RGB = {
        { 85,  85,  85 },   // Bright Black (Gray)
        { 85,  85, 255 },   // Bright Blue
        {255,  85,  85 },   // Bright Red
        {255,  85, 255 },   // Bright Magenta
        { 85, 255,  85 },   // Bright Green
        { 85, 255, 255 },   // Bright Cyan
        {255, 255,  85 },   // Bright Yellow
        {255, 255, 255 },   // Bright White
    };

    private final int[][] palette;

    /**
     * Inicializa el cuantizador.
     *
     * @param useBrightPalette true para usar colores bright, false para colores normales
     */
    public ColorQuantizer(boolean useBrightPalette) {
        this.palette = useBrightPalette ? SPECTRUM_BRIGHT_RGB : SPECTRUM_RGB;
    }

    /**
     * Cuantiza un color RGB al color del Spectrum más cercano.
     *
     * @param r componente rojo (0-255)
     * @param g componente verde (0-255)
     * @param b componente azul (0-255)
     * @return ZxSpectrumColor más cercano
     */
    public ZxSpectrumColor quantize(int r, int g, int b) {
        int bestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < palette.length; i++) {
            double dist = colorDistance(r, g, b, palette[i][0], palette[i][1], palette[i][2]);
            if (dist < minDistance) {
                minDistance = dist;
                bestIndex = i;
            }
        }

        return new ZxSpectrumColor(bestIndex, palette == SPECTRUM_BRIGHT_RGB);
    }

    /**
     * Cuantiza un color ARGB (32 bits) al color del Spectrum más cercano.
     *
     * @param argb color en formato ARGB (se ignora el canal alpha)
     * @return ZxSpectrumColor más cercano
     */
    public ZxSpectrumColor quantize(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return quantize(r, g, b);
    }

    /**
     * Calcula distancia Euclidiana en espacio RGB.
     */
    private double colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        double dr = r1 - r2;
        double dg = g1 - g2;
        double db = b1 - b2;
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * Retorna el color RGB del índice especificado en la paleta actual.
     *
     * @param colorIndex índice de color (0-7)
     * @return array [R, G, B]
     */
    public int[] getRgb(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= palette.length) {
            throw new IllegalArgumentException("Color index out of range: " + colorIndex);
        }
        return palette[colorIndex];
    }
}
