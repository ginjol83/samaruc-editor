package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ZxMultiframeSprite.
 */
public class ZxMultiframeSpriteTest {
    @Test
    public void testMultiframeSpriteValidation() {
        java.util.List<byte[]> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            frames.add(new byte[32]);  // 4 frames, 32 bytes each
        }

        ZxMultiframeSprite sprite = new ZxMultiframeSprite(
            "player_walk",
            32,
            32,
            2,  // 2x2 grid
            2,
            frames,
            ZxBitmapLayout.LINEAR
        );

        assertDoesNotThrow(sprite::validate);
        assertEquals(4, sprite.getTotalFrames());
        assertEquals("player_walk", sprite.spriteName());
    }

    @Test
    public void testMultiframeGetFrame() {
        java.util.List<byte[]> frames = new java.util.ArrayList<>();
        byte[] frame0 = new byte[] { 1, 2, 3 };
        byte[] frame1 = new byte[] { 4, 5, 6 };
        frames.add(frame0);
        frames.add(frame1);

        ZxMultiframeSprite sprite = new ZxMultiframeSprite(
            "test",
            8,
            8,
            2,
            1,
            frames,
            ZxBitmapLayout.LINEAR
        );

        assertArrayEquals(frame0, sprite.getFrame(0));
        assertArrayEquals(frame1, sprite.getFrame(1));
    }

    @Test
    public void testMultiframeGetFrameOutOfRange() {
        java.util.List<byte[]> frames = new java.util.ArrayList<>();
        frames.add(new byte[32]);

        ZxMultiframeSprite sprite = new ZxMultiframeSprite(
            "test",
            32,
            32,
            1,
            1,
            frames,
            ZxBitmapLayout.LINEAR
        );

        assertThrows(IllegalArgumentException.class, () -> sprite.getFrame(1));
        assertThrows(IllegalArgumentException.class, () -> sprite.getFrame(-1));
    }

    @Test
    public void testMultiframeValidationErrors() {
        java.util.List<byte[]> frames = new java.util.ArrayList<>();
        frames.add(new byte[32]);

        // Invalid name
        ZxMultiframeSprite badName = new ZxMultiframeSprite(
            "",
            32,
            32,
            1,
            1,
            frames,
            ZxBitmapLayout.LINEAR
        );
        assertThrows(IllegalArgumentException.class, badName::validate);

        // Invalid frame width (not multiple of 8)
        ZxMultiframeSprite badWidth = new ZxMultiframeSprite(
            "test",
            31,
            32,
            1,
            1,
            frames,
            ZxBitmapLayout.LINEAR
        );
        assertThrows(IllegalArgumentException.class, badWidth::validate);

        // Frame count mismatch
        ZxMultiframeSprite badCount = new ZxMultiframeSprite(
            "test",
            32,
            32,
            2,  // Expects 2x1 = 2 frames
            1,
            frames,  // But only 1 frame provided
            ZxBitmapLayout.LINEAR
        );
        assertThrows(IllegalArgumentException.class, badCount::validate);
    }

    @Test
    public void testMultiframeGridCalculation() {
        java.util.List<byte[]> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            frames.add(new byte[32]);
        }

        // 3x4 grid = 12 frames
        ZxMultiframeSprite sprite = new ZxMultiframeSprite(
            "sheet",
            32,
            32,
            3,
            4,
            frames,
            ZxBitmapLayout.LINEAR
        );

        assertEquals(12, sprite.getTotalFrames());
        sprite.validate();  // Should not throw
    }
}
