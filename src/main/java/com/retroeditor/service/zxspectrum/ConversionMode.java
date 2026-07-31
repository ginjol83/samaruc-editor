package com.retroeditor.service.zxspectrum;

/**
 * Modo de conversión para el conversor PNG a ZX Spectrum.
 *
 * - MONOCHROME_1BPP: 1 bit por píxel (solo blanco/negro)
 * - COLOR_8BITS: 1 byte por píxel con colores cuantizados al Spectrum (8x paleta de colores)
 */
public enum ConversionMode {
    /**
     * Conversión monocromo: 1 bit por píxel (blanco/negro).
     * Mejor para fondos, patrones geométricos.
     */
    MONOCHROME_1BPP,

    /**
     * Conversión a color: 1 byte por píxel con colores del Spectrum.
     * Mejor para imágenes complejas manteniendo fidelidad de color.
     * Cada píxel es un índice a la paleta de colores ZX Spectrum (0-7 normal, 0-7 bright).
     */
    COLOR_8BITS
}
