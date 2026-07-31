package com.retroeditor.service.zxspectrum;

/**
 * Atributos de color ZX Spectrum (2 bits: tinta + fondo + brillo).
 *
 * El Spectrum tiene una paleta de 16 colores (8 colores normales × 2 niveles de brillo).
 * Los atributos ocupan un byte por carácter (8x8 píxeles):
 *
 * Bits 7-6: No usados (0)
 * Bit  5: Brillo (0=normal, 1=bright)
 * Bits 4-2: Color de fondo (000-111)
 * Bits 1-0: Color de tinta (00-11, los 2 bits bajos)
 *
 * Pero en el bitmap 1bpp, necesitamos solo distinción tinta/fondo.
 */
public record ZxSpectrumColor(
    int colorIndex,          // 0-7 (negro, azul, rojo, magenta, verde, cian, amarillo, blanco)
    boolean isBright         // true para brillo normal, false para normal
) {
    /**
     * Colores estándar del ZX Spectrum.
     */
    public static final ZxSpectrumColor[] STANDARD_PALETTE = {
        new ZxSpectrumColor(0, false),  // Black
        new ZxSpectrumColor(1, false),  // Blue
        new ZxSpectrumColor(2, false),  // Red
        new ZxSpectrumColor(3, false),  // Magenta
        new ZxSpectrumColor(4, false),  // Green
        new ZxSpectrumColor(5, false),  // Cyan
        new ZxSpectrumColor(6, false),  // Yellow
        new ZxSpectrumColor(7, false),  // White
    };

    public static final ZxSpectrumColor[] BRIGHT_PALETTE = {
        new ZxSpectrumColor(0, true),   // Bright Black (Gray)
        new ZxSpectrumColor(1, true),   // Bright Blue
        new ZxSpectrumColor(2, true),   // Bright Red
        new ZxSpectrumColor(3, true),   // Bright Magenta
        new ZxSpectrumColor(4, true),   // Bright Green
        new ZxSpectrumColor(5, true),   // Bright Cyan
        new ZxSpectrumColor(6, true),   // Bright Yellow
        new ZxSpectrumColor(7, true),   // Bright White
    };

    public ZxSpectrumColor {
        if (colorIndex < 0 || colorIndex > 7) {
            throw new IllegalArgumentException("Color index must be 0-7, got: " + colorIndex);
        }
    }

    /**
     * Convierte a formato de byte de atributo ZX.
     *
     * @param fgColor color de tinta (foreground)
     * @param bgColor color de fondo (background)
     * @return byte con atributo codificado
     */
    public static byte encodeAttribute(ZxSpectrumColor fgColor, ZxSpectrumColor bgColor) {
        int attr = 0;
        if (fgColor.isBright || bgColor.isBright) {
            attr |= 0x40;  // Bit de brillo (compartido para tinta y fondo)
        }
        attr |= (bgColor.colorIndex & 0x07) << 3;  // Bits 5-3: color de fondo
        attr |= (fgColor.colorIndex & 0x07);       // Bits 2-0: color de tinta
        return (byte) attr;
    }

    /**
     * Decodifica un byte de atributo ZX.
     *
     * @param attr byte con atributo
     * @return array [fgColor, bgColor]
     */
    public static ZxSpectrumColor[] decodeAttribute(byte attr) {
        boolean bright = (attr & 0x40) != 0;
        int bgIdx = (attr >> 3) & 0x07;
        int fgIdx = attr & 0x07;
        return new ZxSpectrumColor[] {
            new ZxSpectrumColor(fgIdx, bright),
            new ZxSpectrumColor(bgIdx, bright)
        };
    }

    /**
     * Retorna el nombre del color en inglés.
     */
    public String getColorName() {
        String[] names = { "Black", "Blue", "Red", "Magenta", "Green", "Cyan", "Yellow", "White" };
        String bright = isBright ? " (Bright)" : "";
        return names[colorIndex] + bright;
    }
}
