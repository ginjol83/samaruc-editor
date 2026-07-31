package com.retroeditor.service.zxspectrum;

/**
 * Enum que define los diferentes layouts de empaquetamiento de pixels
 * en formato ZX Spectrum 1bpp (1 bit por pixel).
 */
public enum ZxBitmapLayout {
    /**
     * Layout lineal: los bytes se disponen fila por fila consecutivamente.
     * Útil para fondos parciales, blitting propio, o renderizado flexible.
     *
     * Ejemplo (8x2 píxeles):
     *   Fila 0: 8 píxeles en 1 byte
     *   Fila 1: 8 píxeles en 1 byte
     *   Total: 2 bytes
     */
    LINEAR,

    /**
     * ZX Spectrum Screen Layout: bytes reordenados según la disposición
     * real de la memoria de pantalla del Spectrum (256x192).
     *
     * La pantalla se divide en 3 tercios de 64 líneas cada uno.
     * Cada tercio ocupa 6144 bytes (32 bytes × 192 líneas).
     *
     * Útil si se quiere copiar directamente a la memoria de pantalla
     * o usar emuladores que esperan este layout.
     *
     * Nota: Implementación en fase posterior (v2).
     */
    ZX_SCREEN_LAYOUT
}
