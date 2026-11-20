package com.retroeditor.model;

/**
 * Un paso dentro de una animación de sprites: referencia a un frame existente
 * y permite desplazarlo respecto al origen del sprite.
 */
public class SpriteAnimationStep {

    private int frameIndex;
    private int offsetX;
    private int offsetY;

    public SpriteAnimationStep() {
    }

    public SpriteAnimationStep(int frameIndex, int offsetX, int offsetY) {
        this.frameIndex = frameIndex;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public int getFrameIndex() { return frameIndex; }
    public void setFrameIndex(int frameIndex) { this.frameIndex = frameIndex; }
    public int getOffsetX() { return offsetX; }
    public void setOffsetX(int offsetX) { this.offsetX = offsetX; }
    public int getOffsetY() { return offsetY; }
    public void setOffsetY(int offsetY) { this.offsetY = offsetY; }
}
