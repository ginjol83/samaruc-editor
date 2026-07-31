package com.retroeditor.service.zxspectrum;

import java.util.List;

/**
 * Representa un sprite con múltiples frames (para animaciones).
 *
 * Un sprite multiframe puede ser extraído de una sprite sheet donde cada frame
 * está alineado en una cuadrícula (frameWidth x frameHeight).
 *
 * Ejemplo: Sprite sheet 256x256 con frames de 32x32 contiene 64 frames (8x8).
 */
public record ZxMultiframeSprite(
    String spriteName,
    int frameWidth,
    int frameHeight,
    int frameCountX,           // Frames por fila en la sprite sheet
    int frameCountY,           // Frames por columna en la sprite sheet
    List<byte[]> frames,       // Array de bytes para cada frame
    ZxBitmapLayout layout
) {
    /**
     * Obtiene el número total de frames.
     */
    public int getTotalFrames() {
        return frameCountX * frameCountY;
    }

    /**
     * Obtiene un frame específico.
     *
     * @param frameIndex índice del frame (0 a getTotalFrames()-1)
     * @return bytes del frame
     * @throws IllegalArgumentException si el índice es inválido
     */
    public byte[] getFrame(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= getTotalFrames()) {
            throw new IllegalArgumentException("Frame index out of range: " + frameIndex + " (total: " + getTotalFrames() + ")");
        }
        return frames.get(frameIndex);
    }

    /**
     * Valida la integridad del sprite.
     *
     * @throws IllegalArgumentException si hay problemas
     */
    public void validate() {
        if (spriteName == null || spriteName.trim().isEmpty()) {
            throw new IllegalArgumentException("Sprite name cannot be empty");
        }
        if (frameWidth <= 0 || frameWidth % 8 != 0) {
            throw new IllegalArgumentException("Frame width must be positive multiple of 8");
        }
        if (frameHeight <= 0) {
            throw new IllegalArgumentException("Frame height must be positive");
        }
        if (frameCountX <= 0 || frameCountY <= 0) {
            throw new IllegalArgumentException("Frame count must be positive");
        }
        if (frames == null || frames.size() != getTotalFrames()) {
            throw new IllegalArgumentException(
                "Frame count mismatch: expected " + getTotalFrames() + ", got " + (frames == null ? 0 : frames.size())
            );
        }
        if (layout == null) {
            throw new IllegalArgumentException("Layout cannot be null");
        }
    }
}
