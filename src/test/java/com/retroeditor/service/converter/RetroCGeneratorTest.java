package com.retroeditor.service.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RetroCGeneratorTest {
    private final PngToRetroImageConverter converter = new PngToRetroImageConverter();
    private final RetroCGenerator generator = new RetroCGenerator();

    @Test
    void generate_ForCpcMode1_EmitsDefinesAndArray() {
        int[] pixels = new int[8];
        Arrays.fill(pixels, 0xFFFFFFFF);

        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.CPC_MODE_1, 8, 1);

        String code = generator.generate(conversion, RetroExportOptions.defaults("hero"));

        assertTrue(code.contains("// Generado por Samaruc"));
        assertTrue(code.contains("Amstrad CPC"));
        assertTrue(code.contains("#define HERO_WIDTH  8"));
        assertTrue(code.contains("#define HERO_HEIGHT 1"));
        assertTrue(code.contains("#define HERO_BYTES  2"));
        assertTrue(code.contains("const unsigned char hero[] = {"));
        assertTrue(code.contains("0x00, 0x00"), "Un byte por cada 4 pixeles");
    }

    @Test
    void generate_ForC64Charset_UsesOneArrayPerLayer() {
        int[] pixels = new int[8 * 8];
        Arrays.fill(pixels, 0xFF000000);
        pixels[0] = 0xFFB8C76F;
        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.C64_CHARSET, 8, 8);

        String code = generator.generate(conversion, new RetroExportOptions("font", false, true, true, null));

        assertTrue(code.contains("unsigned char font_chars[] = {"));
        assertTrue(code.contains("unsigned char font_colors[] = {"));
        assertFalse(code.contains("const unsigned char"), "includeConst a false");
        assertTrue(code.contains("(VIC-II 7)"), "El comentario indica el numero de color del VIC-II");
    }

    @Test
    void generate_ForMasterSystem_CommentsTheCramBytes() {
        int[] pixels = new int[8 * 8];
        Arrays.fill(pixels, 0xFFFF0000);
        pixels[0] = 0x00FFFFFF;
        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.MASTER_SYSTEM_TILES, 8, 8);

        String code = generator.generate(conversion, RetroExportOptions.defaults("tiles"));

        assertTrue(code.contains("Bytes de CRAM para escribir en $C000: 0x00 0x03"));
        assertTrue(code.contains("(transparente en sprites)"));
        assertTrue(code.contains("const unsigned char tiles[] = {"));
    }

    @Test
    void generate_ForMsx_EmitsSmsIncludeAndCommentsVdpCram() {
        int[] pixels = new int[8 * 8];
        Arrays.fill(pixels, 0xFFFF0000);
        pixels[0] = 0x00FFFFFF;
        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.MSX_TILES, 8, 8);

        String code = generator.generate(conversion, RetroExportOptions.defaults("tiles"));

        assertTrue(code.contains("#include <msx.h>"), "El codigo MSX incluye la cabecera de MSX-C");
        assertTrue(code.contains("Paleta MSX1 (%00BBGGRR"), "EL comentario explica el layout de 6 bits");
        assertTrue(code.contains("0x00 0x03"), "Transparente y rojo claro en la tabla CRAM");
        assertTrue(code.contains("const unsigned char tiles[] = {"));
    }

    @Test
    void generate_ForNes_EmitsNesIncludeAndPaletteRamMap() {
        int[] pixels = new int[8 * 8];
        Arrays.fill(pixels, 0xFFFF0000);
        pixels[0] = 0xFF00B800;
        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.NES_TILES, 8, 8);

        String code = generator.generate(conversion, RetroExportOptions.defaults("hero"));

        assertTrue(code.contains("#include <nes.h>"), "El codigo NES incluye la cabecera de cc65");
        assertTrue(code.contains("unsigned char hero[] = {"));
        assertTrue(code.contains("unsigned char hero_attributes[] = {"));
        assertTrue(code.contains("unsigned char hero_palette[] = {"));
        assertTrue(code.contains("$3F01"), "El comentario mapea las subpaletas al PPU");
        assertTrue(code.contains("slot de la subpaleta"));
    }

    @Test
    void generate_ForGameGear_CommentsTwelveBitPalette() {
        int[] pixels = new int[8 * 8];
        Arrays.fill(pixels, 0xFFFF0000);
        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.GAME_GEAR_TILES, 8, 8);

        String code = generator.generate(conversion, RetroExportOptions.defaults("tiles"));

        assertTrue(code.contains("Paleta de 12 bits"), "El GG usa palabras de 12 bits, no bytes");
    }

    @Test
    void generate_ForC64Bitmap_CommentsTheGlobalBackground() {
        int width = 8;
        int height = 8;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xFF000000);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (i % 8) % 2 == 0 ? 0xFF000000 : 0xFFB8C76F;
        }
        pixels[0] = 0xFFFFFFFF;
        RetroImageConversion conversion = converter.convertPixels(pixels, RetroImageMode.C64_BITMAP, width, height);
        int background = RetroImagePacker.c64BitmapBackgroundPosition(conversion.indices(),
                conversion.hardwareIndices());

        String code = generator.generate(conversion, RetroExportOptions.defaults("screen"));

        assertTrue(code.contains("unsigned char screen_bitmap[] = {"));
        assertTrue(code.contains("unsigned char screen_screen[] = {"));
        assertTrue(code.contains("unsigned char screen_colors[] = {"));
        assertTrue(code.contains(String.format("Fondo global: escribir %d en $D021 (indice %d, #%06X)",
                conversion.hardwareIndex(background), background,
                conversion.palette()[background] & 0xFFFFFF)),
                "Hay que indicar el color de fondo que usa el valor 00 de los pixeles");
    }

    @Test
    void generate_WithoutComments_OmitsThem() {
        int[] pixels = new int[8];
        Arrays.fill(pixels, 0xFF000000);
        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.CPC_MODE_0, 8, 1);

        String code = generator.generate(conversion,
                new RetroExportOptions("datos", true, false, false, null));

        assertFalse(code.contains("#define"));
        assertFalse(code.contains("Paleta usada"));
    }

    @Test
    void sanitizeSymbol_ReplacesInvalidCharacters() {
        assertEquals("sprite_1", RetroCGenerator.sanitizeSymbol("sprite 1"));
        assertEquals("_9lives", RetroCGenerator.sanitizeSymbol("9lives"));
        assertEquals(RetroExportOptions.DEFAULT_ARRAY_NAME, RetroCGenerator.sanitizeSymbol("   "));
        assertEquals(RetroExportOptions.DEFAULT_ARRAY_NAME, RetroCGenerator.sanitizeSymbol(null));
    }

    @Test
    void generate_WithNullArguments_Throws() {
        int[] pixels = new int[8];
        Arrays.fill(pixels, 0xFF000000);
        RetroImageConversion conversion =
                converter.convertPixels(pixels, RetroImageMode.CPC_MODE_0, 8, 1);

        assertThrows(IllegalArgumentException.class, () -> generator.generate(null, RetroExportOptions.defaults()));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(conversion, null));
    }
}
