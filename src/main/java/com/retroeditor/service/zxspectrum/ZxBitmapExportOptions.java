package com.retroeditor.service.zxspectrum;

/**
 * Opciones de conversión para PNG a formato ZX Spectrum 1bpp.
 *
 * Record que encapsula todos los parámetros configurables del conversor.
 */
public record ZxBitmapExportOptions(
    String arrayName,
    int width,
    int height,
    int monochromeThreshold,
    ZxBitmapLayout layout,
    boolean includeConst,
    boolean includeWidthHeightDefines,
    String headerComment
) {
    /**
     * Constructor compacto con defaults razonables.
     */
    public static ZxBitmapExportOptions defaults(String arrayName, int width, int height) {
        return new ZxBitmapExportOptions(
            arrayName,
            width,
            height,
            128,                          // Umbral monocromo por defecto
            ZxBitmapLayout.LINEAR,        // Layout lineal en v1
            true,                         // Incluir const por defecto
            false,                        // Sin #defines por defecto
            "Generado por Samaruc"        // Comentario estándar
        );
    }

    /**
     * Valida las opciones. Lanza IllegalArgumentException si hay problemas.
     */
    public void validate() {
        if (arrayName == null || arrayName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del array no puede estar vacío");
        }
        if (width <= 0) {
            throw new IllegalArgumentException("El ancho debe ser mayor que 0");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("El alto debe ser mayor que 0");
        }
        if (width % 8 != 0) {
            throw new IllegalArgumentException("El ancho debe ser múltiplo de 8 (tienes " + width + ")");
        }
        if (monochromeThreshold < 0 || monochromeThreshold > 255) {
            throw new IllegalArgumentException("El umbral debe estar entre 0 y 255 (tienes " + monochromeThreshold + ")");
        }
        if (layout == null) {
            throw new IllegalArgumentException("El layout no puede ser nulo");
        }
        // ZX_SCREEN_LAYOUT requiere exactamente 256x192 (resolución full screen del Spectrum)
        if (layout == ZxBitmapLayout.ZX_SCREEN_LAYOUT && (width != 256 || height != 192)) {
            throw new IllegalArgumentException(
                String.format("ZX_SCREEN_LAYOUT requiere exactamente 256x192 (tienes %dx%d)", width, height)
            );
        }
    }
}
