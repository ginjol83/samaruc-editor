package com.retroeditor.service.gameboy;

import java.awt.image.BufferedImage;

/**
 * Conversor de PNG a tilemap para Game Boy.
 * Divide la imagen en tiles de 8x8, deduplica y genera indices de mapa.
 */
public class PngToGameBoyTilemapConverter {
    private static final int TILE_SIZE = 8;
    private static final int TILES_WIDTH = 20;   // 160px / 8
    private static final int TILES_HEIGHT = 18;  // 144px / 8

    /**
     * Convierte una imagen PNG a tilemap para Game Boy.
     *
     * @param image imagen BufferedImage (esperada 160x144)
     * @param contrastLevel nivel de contraste
     * @param invertColors si true, invierte colores
     * @return GameBoyTilemapResult con mapData y tileData
     */
    public GameBoyTilemapResult convertImageToTilemap(BufferedImage image, int contrastLevel, boolean invertColors) {
        if (image.getWidth() < TILE_SIZE * TILES_WIDTH || image.getHeight() < TILE_SIZE * TILES_HEIGHT) {
            throw new IllegalArgumentException(
                String.format("Image too small: expected at least %dx%d, got %dx%d",
                    TILE_SIZE * TILES_WIDTH, TILE_SIZE * TILES_HEIGHT,
                    image.getWidth(), image.getHeight())
            );
        }

        TileDeduplicator deduplicator = new TileDeduplicator();
        byte[] mapData = new byte[TILES_WIDTH * TILES_HEIGHT];

        // Procesar cada tile en la imagen
        for (int tileY = 0; tileY < TILES_HEIGHT; tileY++) {
            for (int tileX = 0; tileX < TILES_WIDTH; tileX++) {
                // Extraer los píxeles de este tile
                int[][] tilePixels = extractTilePixels(image, tileX, tileY, contrastLevel, invertColors);

                // Convertir pixels a bytes 2bpp
                byte[] tileBytes = pixelsTo2bpp(tilePixels);

                // Registrar tile y obtener índice
                GameBoyTile tile = new GameBoyTile(tileBytes);
                int tileIndex = deduplicator.registerTile(tile);

                // Guardar índice en mapa
                mapData[tileY * TILES_WIDTH + tileX] = (byte) tileIndex;
            }
        }

        deduplicator.validate();

        // Generar array de datos de tiles
        byte[] tileData = generateTileData(deduplicator);

        return new GameBoyTilemapResult(
            mapData,
            tileData,
            deduplicator.getTileCount(),
            TILES_WIDTH,
            TILES_HEIGHT
        );
    }

    /**
     * Extrae los píxeles de un tile (8x8) de la imagen.
     * Retorna índices de color [0-3].
     */
    private int[][] extractTilePixels(BufferedImage image, int tileX, int tileY,
                                      int contrastLevel, boolean invertColors) {
        int[][] pixels = new int[TILE_SIZE][TILE_SIZE];
        GameBoyColorQuantizer quantizer = new GameBoyColorQuantizer();

        int startX = tileX * TILE_SIZE;
        int startY = tileY * TILE_SIZE;

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int imgX = startX + x;
                int imgY = startY + y;

                int rgb = image.getRGB(imgX, imgY);

                // Aplicar contraste
                rgb = applyContrast(rgb, contrastLevel);

                // Aplicar inversión
                if (invertColors) {
                    rgb = invertRgb(rgb);
                }

                byte colorIdx = quantizer.quantize(rgb);
                pixels[y][x] = colorIdx & 0x03;
            }
        }

        return pixels;
    }

    /**
     * Convierte una matriz de píxeles [0-3] a 16 bytes en formato 2bpp.
     */
    private byte[] pixelsTo2bpp(int[][] pixels) {
        byte[] result = new byte[16];
        int offset = 0;

        for (int row = 0; row < TILE_SIZE; row++) {
            int lowByte = 0;
            int highByte = 0;

            for (int col = 0; col < TILE_SIZE; col++) {
                int colorValue = pixels[row][col] & 0x03;
                int bitPos = 7 - col;

                if ((colorValue & 0x01) != 0) {
                    lowByte |= (1 << bitPos);
                }
                if ((colorValue & 0x02) != 0) {
                    highByte |= (1 << bitPos);
                }
            }

            result[offset++] = (byte) lowByte;
            result[offset++] = (byte) highByte;
        }

        return result;
    }

    /**
     * Genera el array de datos de tiles desde los tiles únicos.
     */
    private byte[] generateTileData(TileDeduplicator deduplicator) {
        byte[] result = new byte[deduplicator.getTileCount() * 16];
        int offset = 0;

        for (GameBoyTile tile : deduplicator.getUniqueTiles()) {
            byte[] tileBytes = tile.getData();
            System.arraycopy(tileBytes, 0, result, offset, 16);
            offset += 16;
        }

        return result;
    }

    /**
     * Aplica contraste a un color RGB.
     */
    private int applyContrast(int rgb, int contrastLevel) {
        if (contrastLevel == 0) return rgb;

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        double factor = 1.0 + (contrastLevel / 100.0);
        int centerValue = 128;

        r = clampByte((int) (centerValue + (r - centerValue) * factor));
        g = clampByte((int) (centerValue + (g - centerValue) * factor));
        b = clampByte((int) (centerValue + (b - centerValue) * factor));

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Invierte un color RGB.
     */
    private int invertRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        r = 255 - r;
        g = 255 - g;
        b = 255 - b;

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Limita un valor a [0, 255].
     */
    private int clampByte(int value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }
}
