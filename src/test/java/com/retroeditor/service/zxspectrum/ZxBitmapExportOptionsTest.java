package com.retroeditor.service.zxspectrum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para ZxBitmapExportOptions.
 */
public class ZxBitmapExportOptionsTest {
    private ZxBitmapExportOptions options;

    @BeforeEach
    public void setUp() {
        options = ZxBitmapExportOptions.defaults("test_bitmap", 256, 88);
    }

    @Test
    public void testDefaultsCreation() {
        assertNotNull(options);
        assertEquals("test_bitmap", options.arrayName());
        assertEquals(256, options.width());
        assertEquals(88, options.height());
        assertEquals(128, options.monochromeThreshold());
        assertEquals(ZxBitmapLayout.LINEAR, options.layout());
        assertTrue(options.includeConst());
        assertFalse(options.includeWidthHeightDefines());
    }

    @Test
    public void testValidation_ValidOptions() {
        assertDoesNotThrow(() -> options.validate());
    }

    @Test
    public void testValidation_EmptyArrayName() {
        ZxBitmapExportOptions bad = new ZxBitmapExportOptions(
            "", 256, 88, 128, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertThrows(IllegalArgumentException.class, bad::validate);
    }

    @Test
    public void testValidation_NullArrayName() {
        ZxBitmapExportOptions bad = new ZxBitmapExportOptions(
            null, 256, 88, 128, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertThrows(IllegalArgumentException.class, bad::validate);
    }

    @Test
    public void testValidation_ZeroWidth() {
        ZxBitmapExportOptions bad = new ZxBitmapExportOptions(
            "test", 0, 88, 128, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertThrows(IllegalArgumentException.class, bad::validate);
    }

    @Test
    public void testValidation_ZeroHeight() {
        ZxBitmapExportOptions bad = new ZxBitmapExportOptions(
            "test", 256, 0, 128, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertThrows(IllegalArgumentException.class, bad::validate);
    }

    @Test
    public void testValidation_WidthNotMultipleOf8() {
        ZxBitmapExportOptions bad = new ZxBitmapExportOptions(
            "test", 255, 88, 128, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertThrows(IllegalArgumentException.class, () -> {
            bad.validate();
        });
    }

    @Test
    public void testValidation_ThresholdTooLow() {
        ZxBitmapExportOptions bad = new ZxBitmapExportOptions(
            "test", 256, 88, -1, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertThrows(IllegalArgumentException.class, bad::validate);
    }

    @Test
    public void testValidation_ThresholdTooHigh() {
        ZxBitmapExportOptions bad = new ZxBitmapExportOptions(
            "test", 256, 88, 256, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertThrows(IllegalArgumentException.class, bad::validate);
    }

    @Test
    public void testValidation_NullLayout() {
        ZxBitmapExportOptions bad = new ZxBitmapExportOptions(
            "test", 256, 88, 128, null, true, false, ""
        );
        assertThrows(IllegalArgumentException.class, bad::validate);
    }

    @Test
    public void testValidation_ValidThresholdBoundaries() {
        ZxBitmapExportOptions lowThreshold = new ZxBitmapExportOptions(
            "test", 256, 88, 0, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertDoesNotThrow(lowThreshold::validate);

        ZxBitmapExportOptions highThreshold = new ZxBitmapExportOptions(
            "test", 256, 88, 255, ZxBitmapLayout.LINEAR, true, false, ""
        );
        assertDoesNotThrow(highThreshold::validate);
    }
}
