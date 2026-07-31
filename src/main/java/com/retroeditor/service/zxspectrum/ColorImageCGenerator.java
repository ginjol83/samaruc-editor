package com.retroeditor.service.zxspectrum;

/**
 * Generador de código C para imágenes ZX Spectrum a color (1 byte por píxel).
 *
 * Responsabilidades:
 * - Formatear índices de color como array C
 * - Generar comentarios con información de paleta
 * - Incluir defines opcionales para width/height
 * - Incluir tabla de paleta RGB para referencia
 */
public class ColorImageCGenerator {
    /**
     * Genera código C para una imagen a color.
     *
     * Formato:
     * ```c
     * // Palette (normal or bright colors)
     * // 0: Black (RGB 0,0,0) [o colores bright equivalentes]
     * // ...
     * // 7: White (RGB 215,215,215) [o bright]
     *
     * const unsigned char imageName[] = {
     *     0x00, 0x02, 0x05, ...  // índices de color (0-7)
     * };
     * ```
     *
     * @param image imagen a color con índices
     * @param includeConst si true, añade "const"
     * @param includeWidthHeightDefines si true, añade #defines para width/height
     * @return código C generado
     */
    public String generateC(ZxColorImage image, String arrayName, boolean includeConst, boolean includeWidthHeightDefines) {
        image.validate();

        StringBuilder sb = new StringBuilder();

        // Comentario con información de paleta
        sb.append("// Imagen ZX Spectrum (1 byte = índice de color)\n");
        if (image.isBrightPalette()) {
            sb.append("// Paleta: Colores BRIGHT del Spectrum (0-7)\n");
        } else {
            sb.append("// Paleta: Colores normales del Spectrum (0-7)\n");
        }
        sb.append("// Tamaño: ").append(image.width()).append("x").append(image.height())
          .append(" (").append(image.pixelData().length).append(" bytes)\n");
        sb.append("\n");

        // Tabla de colores para referencia
        sb.append("// Índice de color -> nombre\n");
        String[] colorNames = { "Black", "Blue", "Red", "Magenta", "Green", "Cyan", "Yellow", "White" };
        for (int i = 0; i < 8; i++) {
            int idx = i * 4;
            int r = image.paletteRgb()[idx] & 0xFF;
            int g = image.paletteRgb()[idx + 1] & 0xFF;
            int b = image.paletteRgb()[idx + 2] & 0xFF;
            String bright = image.isBrightPalette() ? " (bright)" : "";
            sb.append("// ").append(i).append(": ").append(colorNames[i]).append(bright)
              .append(" RGB(").append(r).append(",").append(g).append(",").append(b).append(")\n");
        }
        sb.append("\n");

        // Defines opcionales
        if (includeWidthHeightDefines) {
            String sanitized = sanitizeIdentifier(arrayName);
            sb.append("#define ").append(sanitized).append("_WIDTH ").append(image.width()).append("\n");
            sb.append("#define ").append(sanitized).append("_HEIGHT ").append(image.height()).append("\n");
            sb.append("#define ").append(sanitized).append("_SIZE ").append(image.pixelData().length).append("\n");
            sb.append("\n");
        }

        // Array declaration
        String constKeyword = includeConst ? "const " : "";
        String sanitized = sanitizeIdentifier(arrayName);
        sb.append(constKeyword).append("unsigned char ").append(sanitized).append("[] = {\n");

        // Datos de imagen
        byte[] data = image.pixelData();
        int bytesPerLine = Math.min(16, image.width()); // 16 bytes por línea, o menos si la imagen es más angosta
        for (int i = 0; i < data.length; i++) {
            if (i > 0 && i % bytesPerLine == 0) {
                sb.append("\n");
            }
            if (i % bytesPerLine == 0) {
                sb.append("    ");
            }
            sb.append("0x").append(String.format("%02X", data[i] & 0xFF));
            if (i < data.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("\n};\n");

        return sb.toString();
    }

    /**
     * Sanitiza un nombre de identificador C.
     * Reemplaza caracteres inválidos con '_'.
     */
    private String sanitizeIdentifier(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
