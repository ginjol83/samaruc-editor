package com.retroeditor.service.gameboy;

/**
 * Opciones de exportación para conversión PNG a Game Boy.
 *
 * @param arrayName nombre del array C
 * @param width ancho en píxeles (múltiplo de 8)
 * @param height alto en píxeles (múltiplo de 8)
 * @param useGameBoyColor si true, usa paleta Game Boy Color
 * @param includeConst si true, incluye modificador const
 * @param includeDefines si true, incluye #defines con ancho/alto
 * @param contrastLevel nivel de contraste (-50 a 50, donde 0 es sin cambios)
 * @param invertColors si true, invierte los colores
 * @param comment comentario de generación
 */
public record GameBoyBitmapExportOptions(
    String arrayName,
    int width,
    int height,
    boolean useGameBoyColor,
    boolean includeConst,
    boolean includeDefines,
    int contrastLevel,
    boolean invertColors,
    String comment
) {
    public void validate() {
        if (width <= 0 || width % 8 != 0) {
            throw new IllegalArgumentException("Ancho debe ser múltiplo de 8: " + width);
        }
        if (height <= 0 || height % 8 != 0) {
            throw new IllegalArgumentException("Alto debe ser múltiplo de 8: " + height);
        }
        if (arrayName == null || arrayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Nombre del array no puede estar vacío");
        }
    }
}
