package com.retroeditor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un frame de sprite dentro de una animación.
 *
 * {@code pixels} es una matriz [alto][ancho] donde cada valor es un índice de
 * la paleta de la plataforma (0..3 Game Boy, 0..15 ZX Spectrum / Atari).
 * {@code durationVbls} es la duración en VBLs (interrupciones verticales,
 * 50/60 Hz según la plataforma).
 *
 * {@code width}/{@code height} son propiedades computadas a partir de
 * {@code pixels}, por lo que no se serializan ni deben leerse del JSON.
 */
@JsonIgnoreProperties({ "width", "height" })
public class SpriteFrame {

    private int[][] pixels;
    private int durationVbls = 4;

    public SpriteFrame() {}

    public SpriteFrame(int[][] pixels, int durationVbls) {
        this.pixels = pixels;
        this.durationVbls = durationVbls;
    }

    public int[][] getPixels() { return pixels; }
    public void setPixels(int[][] pixels) { this.pixels = pixels; }
    public int getDurationVbls() { return durationVbls; }
    public void setDurationVbls(int durationVbls) { this.durationVbls = durationVbls; }

    public int getWidth() {
        return pixels != null && pixels.length > 0 ? pixels[0].length : 0;
    }

    public int getHeight() {
        return pixels != null ? pixels.length : 0;
    }
}