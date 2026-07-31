package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para cuantización de colores RGB a paleta ZX Spectrum.
 */
public class ColorQuantizerTest {

    @Test
    public void testQuantizePureBlack() {
        ColorQuantizer q = new ColorQuantizer(false);
        ZxSpectrumColor color = q.quantize(0, 0, 0);
        assertEquals(0, color.colorIndex(), "Black RGB(0,0,0) -> color 0");
    }

    @Test
    public void testQuantizePureWhite() {
        ColorQuantizer q = new ColorQuantizer(false);
        ZxSpectrumColor color = q.quantize(215, 215, 215);
        assertEquals(7, color.colorIndex(), "White RGB(215,215,215) -> color 7");
    }

    @Test
    public void testQuantizePureRed() {
        ColorQuantizer q = new ColorQuantizer(false);
        ZxSpectrumColor color = q.quantize(215, 0, 0);
        assertEquals(2, color.colorIndex(), "Red RGB(215,0,0) -> color 2");
    }

    @Test
    public void testQuantizeGray() {
        ColorQuantizer q = new ColorQuantizer(true);
        ZxSpectrumColor color = q.quantize(85, 85, 85);
        assertEquals(0, color.colorIndex(), "Gray RGB(85,85,85) bright -> color 0 (Bright Black)");
        assertTrue(color.isBright());
    }

    @Test
    public void testQuantizeIntRgb() {
        ColorQuantizer q = new ColorQuantizer(false);
        int argb = 0xFFD70000;  // Red in ARGB (RGB215,0,0)
        ZxSpectrumColor color = q.quantize(argb);
        assertEquals(2, color.colorIndex(), "Red via ARGB");
    }

    @Test
    public void testGetRgb() {
        ColorQuantizer q = new ColorQuantizer(false);
        int[] rgb = q.getRgb(0);
        assertEquals(3, rgb.length);
        assertEquals(0, rgb[0], "Black R");
        assertEquals(0, rgb[1], "Black G");
        assertEquals(0, rgb[2], "Black B");
    }

    @Test
    public void testGetRgbBright() {
        ColorQuantizer q = new ColorQuantizer(true);
        int[] rgb = q.getRgb(7);
        assertEquals(255, rgb[0], "Bright White R");
        assertEquals(255, rgb[1], "Bright White G");
        assertEquals(255, rgb[2], "Bright White B");
    }

    @Test
    public void testInvalidColorIndex() {
        ColorQuantizer q = new ColorQuantizer(false);
        assertThrows(IllegalArgumentException.class, () -> q.getRgb(-1), "Negative index");
        assertThrows(IllegalArgumentException.class, () -> q.getRgb(8), "Index > 7");
    }
}
