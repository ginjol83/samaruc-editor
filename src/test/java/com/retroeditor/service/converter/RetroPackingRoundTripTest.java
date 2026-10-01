package com.retroeditor.service.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Convierte PNGs reales de cada modo y vuelve a leer los bytes generados como
 * si los descargara la maquina, para comprobar que el empaquetado no pierde
 * ningun pixel. Se comparan los numeros de color de hardware, que es lo que
 * acaba viendo el VIC-II, el CRAM o la matriz de pantalla.
 */
@DisplayName("RetroPackingRoundTrip Tests")
class RetroPackingRoundTripTest {
    private static final int WIDTH = 16;
    private static final int HEIGHT = 8;

    private final PngToRetroImageConverter converter = new PngToRetroImageConverter();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("CPC 0/1/2: los bpp se recuperan pixel a pixel")
    void linearModesAreLossless() throws IOException {
        for (RetroImageMode mode : new RetroImageMode[] {RetroImageMode.CPC_MODE_0, RetroImageMode.CPC_MODE_1,
                RetroImageMode.CPC_MODE_2, RetroImageMode.ATARI_PLAYFIELD}) {
            RetroImageConversion conversion = convert(mode, patronRico(mode));

            int bits = mode.getBitsPerPixel();
            int pixelsPerByte = 8 / bits;
            int bytesPerRow = WIDTH / pixelsPerByte;
            int mask = (1 << bits) - 1;
            byte[] data = conversion.layers().get(0).data();
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int shift = 8 - bits - (x % pixelsPerByte) * bits;
                    int value = (data[y * bytesPerRow + x / pixelsPerByte] >> shift) & mask;
                    assertIndex(conversion, x, y, value, mode);
                }
            }
        }
    }

    @Test
    @DisplayName("Atari GTIA 7: dos pixeles por byte con PRIOR 3")
    void gti7IsLossless() throws IOException {
        RetroImageMode mode = RetroImageMode.ATARI_MODE_7;
        RetroImageConversion conversion = convert(mode, patronRico(mode));

        byte[] data = conversion.layers().get(0).data();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x += 2) {
                int byteValue = data[y * (WIDTH / 2) + x / 2] & 0xFF;
                assertEquals(RetroImagePacker.GTIA_PRIOR_ALL, byteValue >> 6,
                        "PRIOR debe ser 3 para ver los cuatro playfields");
                assertIndex(conversion, x, y, byteValue & 0x03, mode);
                assertIndex(conversion, x + 1, y, (byteValue >> 2) & 0x03, mode);
            }
        }
    }

    @Test
    @DisplayName("Master System y Game Gear: los dos planos de cada columna")
    void tilePlanarIsLossless() throws IOException {
        for (RetroImageMode mode : new RetroImageMode[] {RetroImageMode.MASTER_SYSTEM_TILES,
                RetroImageMode.GAME_GEAR_TILES}) {
            RetroImageConversion conversion = convert(mode, patronRico(mode));

            byte[] data = conversion.layers().get(0).data();
            int tilesX = WIDTH / RetroImageMode.TILE_SIZE;
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int tile = (y / RetroImageMode.TILE_SIZE) * tilesX + x / RetroImageMode.TILE_SIZE;
                    int column = x % RetroImageMode.TILE_SIZE;
                    int row = y % RetroImageMode.TILE_SIZE;
                    int offset = (tile * RetroImageMode.TILE_SIZE + column) * 2;
                    int bit = 7 - row;
                    int low = (data[offset] >> bit) & 1;
                    int high = (data[offset + 1] >> bit) & 1;
                    assertIndex(conversion, x, y, low | (high << 1), mode);
                }
            }
        }
    }

    @Test
    @DisplayName("El negro opaco no se confunde con el color transparente del Master System")
    void opaqueBlackIsNotTheTransparentIndex(@TempDir Path dir) throws IOException {
        RetroImageMode mode = RetroImageMode.MASTER_SYSTEM_TILES;
        BufferedImage image = patronRico(mode);
        image.setRGB(3, 2, 0x00000000);
        image.setRGB(4, 2, 0xFF000000);

        File png = write(image, mode.name() + "-transparente");
        RetroImageConversion conversion = converter.convert(png, mode, WIDTH, HEIGHT);

        assertTrue(mode.hasTransparentFirstColor());
        assertEquals(0, conversion.indexAt(3, 2), "El canal alfa se decide antes de cuantizar");
        assertEquals(0, conversion.hardwareIndex(0), "El indice 0 es el color transparente del sprite");
        assertEquals(1, conversion.hardwareIndex(conversion.indexAt(4, 2)),
                "El negro opaco va al indice 1 del CRAM, no al 0, o desapareceria en pantalla");
    }

    @Test
    @DisplayName("C64 charset: el bit decide entre el nibble alto y el bajo del color RAM")
    void c64CharsetIsLossless() throws IOException {
        RetroImageMode mode = RetroImageMode.C64_CHARSET;
        RetroImageConversion conversion = convert(mode, dosColoresPorCelda(mode));

        byte[] chars = conversion.layers().get(0).data();
        byte[] colors = conversion.layers().get(1).data();
        int charsX = WIDTH / RetroImageMode.TILE_SIZE;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int charIndex = (y / RetroImageMode.TILE_SIZE) * charsX + x / RetroImageMode.TILE_SIZE;
                int bit = (chars[charIndex * RetroImageMode.TILE_SIZE + y % RetroImageMode.TILE_SIZE]
                        >> (7 - x % RetroImageMode.TILE_SIZE)) & 1;
                int colorRam = colors[charIndex] & 0xFF;
                assertHardware(conversion, x, y, bit == 1 ? colorRam >> 4 : colorRam & 0x0F, mode);
            }
        }
    }

    @Test
    @DisplayName("MSX SCREEN 1: el color sale del grupo de 8 caracteres al que pertenece el patron")
    void msxScreen1IsLossless() throws IOException {
        RetroImageMode mode = RetroImageMode.MSX_TILES;
        RetroImageConversion conversion = convert(mode, mismoColorEnCadaCelda(mode));

        byte[] patterns = conversion.layers().get(0).data();
        byte[] map = conversion.layers().get(1).data();
        byte[] colors = conversion.layers().get(2).data();
        int charsX = WIDTH / RetroImageMode.TILE_SIZE;
        assertEquals(1, colors.length, "Los dos patrones caben en un unico grupo de 8 caracteres");
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int character = map[(y / RetroImageMode.TILE_SIZE) * charsX + x / RetroImageMode.TILE_SIZE] & 0xFF;
                int bit = (patterns[character * RetroImageMode.TILE_SIZE + y % RetroImageMode.TILE_SIZE]
                        >> (7 - x % RetroImageMode.TILE_SIZE)) & 1;
                int group = colors[character / RetroImageMode.MSX_SCREEN1_CHARS_PER_COLOUR] & 0xFF;
                assertHardware(conversion, x, y, bit == 1 ? group >> 4 : group & 0x0F, mode);
            }
        }
    }

    @Test
    @DisplayName("C64 bitmap multicolor: 00 fondo, 01/10 pantalla y 11 color RAM")
    void c64BitmapIsLossless() throws IOException {
        RetroImageMode mode = RetroImageMode.C64_BITMAP;
        RetroImageConversion conversion = convert(mode, fondoGlobalYDosColoresPorCelda(mode));

        byte[] bitmap = conversion.layers().get(0).data();
        byte[] screen = conversion.layers().get(1).data();
        byte[] colors = conversion.layers().get(2).data();
        int cellsX = WIDTH / RetroImageMode.TILE_SIZE;
        int bytesPerRow = WIDTH / 4;
        int background = conversion.hardwareIndex(RetroImagePacker.c64BitmapBackgroundPosition(
                conversion.indices(), conversion.hardwareIndices()));
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int shift = 6 - (x % 4) * 2;
                int value = (bitmap[y * bytesPerRow + x / 4] >> shift) & 0x03;
                int cellIndex = (y / RetroImageMode.TILE_SIZE) * cellsX + x / RetroImageMode.TILE_SIZE;
                int matrix = screen[cellIndex] & 0xFF;
                int colorRam = colors[cellIndex] & 0x0F;
                int hardware = switch (value) {
                    case 0 -> background;
                    case 1 -> matrix >> 4;
                    case 2 -> matrix & 0x0F;
                    default -> colorRam;
                };
                assertHardware(conversion, x, y, hardware, mode);
            }
        }
    }

    @Test
    @DisplayName("El fondo global del bitmap es el mismo en todas las celdas")
    void c64BitmapUsesOneBackgroundForEveryCell() throws IOException {
        RetroImageMode mode = RetroImageMode.C64_BITMAP;
        RetroImageConversion conversion = convert(mode, fondoGlobalYDosColoresPorCelda(mode));

        int background = RetroImagePacker.c64BitmapBackgroundPosition(conversion.indices(),
                conversion.hardwareIndices());
        Set<Integer> backgrounds = new HashSet<>();
        backgrounds.add(conversion.hardwareIndex(background));
        assertEquals(1, backgrounds.size(), "$D021 solo admite un color para toda la imagen");
        assertTrue(conversion.totalBytes() == WIDTH * HEIGHT / 4 + 2 * (WIDTH / 8) * (HEIGHT / 8),
                "Bitmap 2bpp + 1 byte de pantalla y 1 de color RAM por celda");
    }

    @Test
    @DisplayName("NES: el 2bpp recupera el color de la subpaleta de la celda")
    void nes2BppIsLossless() throws IOException {
        RetroImageMode mode = RetroImageMode.NES_TILES;
        RetroImageConversion conversion = convert(mode, patronRico(mode));

        byte[] patterns = conversion.layers().get(0).data();
        byte[] attributes = conversion.layers().get(1).data();
        byte[] paletteRam = conversion.layers().get(2).data();
        int tilesX = WIDTH / RetroImageMode.TILE_SIZE;
        int subpalette = attributes[0] & 0x03;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int tile = (y / RetroImageMode.TILE_SIZE) * tilesX + x / RetroImageMode.TILE_SIZE;
                int row = y % RetroImageMode.TILE_SIZE;
                int column = x % RetroImageMode.TILE_SIZE;
                int offset = tile * 16;
                int bit = 7 - column;
                int plane0 = (patterns[offset + row] >> bit) & 1;
                int plane1 = (patterns[offset + 8 + row] >> bit) & 1;
                int slot = plane0 | (plane1 << 1);
                int ppu = paletteRam[subpalette * 4 + slot] & 0xFF;
                assertEquals(conversion.hardwareIndices()[conversion.indexAt(x, y)], ppu,
                        "El pixel (" + x + "," + y + ") no recupera su entrada de la paleta del PPU");
            }
        }
    }

    private void assertIndex(RetroImageConversion conversion, int x, int y, int packed, RetroImageMode mode) {
        assertEquals(conversion.indexAt(x, y), packed,
                "El indice del pixel (" + x + "," + y + ") de " + mode + " no se recupera de los bytes");
    }

    /**
     * Los modos del C64 guardan numeros de color del VIC-II, no posiciones de la
     * paleta, asi que hay que comparar con el numero de hardware.
     */
    private void assertHardware(RetroImageConversion conversion, int x, int y, int hardware, RetroImageMode mode) {
        assertEquals(conversion.hardwareIndex(conversion.indexAt(x, y)), hardware,
                "El pixel (" + x + "," + y + ") de " + mode + " no se recupera de los bytes");
    }

    private RetroImageConversion convert(RetroImageMode mode, BufferedImage image) throws IOException {
        return converter.convert(write(image, mode.name()), mode, WIDTH, HEIGHT);
    }

    private File write(BufferedImage image, String name) throws IOException {
        File png = tempDir.resolve(name + "-" + WIDTH + "x" + HEIGHT + ".png").toFile();
        ImageIO.write(image, "png", png);
        return png;
    }

    /** Hasta cuatro colores de la paleta del modo, repartidos en un patron de 4x2. */
    private BufferedImage patronRico(RetroImageMode mode) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int[] palette = mode.palette();
        int colors = Math.min(4, palette.length);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int index = ((x / 2) + y) % colors;
                image.setRGB(x, y, palette[index] | 0xFF000000);
            }
        }
        return image;
    }

    /**
     * Dos colores por celda de 8x8, con la dominante distinta en cada mitad, para
     * que el charset y el bitmap tengan que elegir sus colores por separado.
     */
    private BufferedImage dosColoresPorCelda(RetroImageMode mode) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int[] palette = mode.palette();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int dominant = x < 8 ? palette[0] : palette[2];
                int other = x < 8 ? palette[1] : palette[3];
                boolean otherPixel = (x < 8 ? (x % 2 == 1) : (x % 2 == 0)) && (y % 4 != 3);
                image.setRGB(x, y, (otherPixel ? other : dominant) | 0xFF000000);
            }
        }
        return image;
    }

    /**
     * Dos celdas con el mismo par de colores pero distinto patron, para el MSX1:
     * SCREEN 1 solo tiene un byte de color por cada 8 caracteres, asi que dos
     * celdas con colores distintos no podrian ir en la misma tabla de color.
     * Se usan los indices 0 y 2 de la paleta TMS9918 porque el indice 1 tambien
     * es negro.
     */
    private BufferedImage mismoColorEnCadaCelda(RetroImageMode mode) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int[] palette = mode.palette();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean otherPixel = (x < 8 ? x % 2 == 1 : x % 2 == 0) && (y % 4 != 3);
                image.setRGB(x, y, (otherPixel ? palette[2] : palette[0]) | 0xFF000000);
            }
        }
        return image;
    }

    /**
     * Igual que {@link #dosColoresPorCelda(RetroImageMode)} pero con un color
     * claramente mayoritario en toda la imagen, para que el fondo global del
     * bitmap C64 no dependa de un empate.
     */
    private BufferedImage fondoGlobalYDosColoresPorCelda(RetroImageMode mode) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int[] palette = mode.palette();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int color = palette[0];
                if (x < 8) {
                    color = y % 4 == 3 && x % 2 == 1 ? palette[1] : palette[0];
                } else {
                    color = y % 2 == 0 ? palette[2] : palette[3];
                }
                image.setRGB(x, y, color | 0xFF000000);
            }
        }
        return image;
    }
}
