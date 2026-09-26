package com.retroeditor.service.converter;

import java.util.List;
import java.util.Objects;

/**
 * Empaqueta los indices de color de una imagen en el formato de memoria de
 * cada maquina.
 *
 * El C64 separa los pixeles del color, de modo que sus modos generan varias
 * capas: el charset produce datos y color RAM por caracter, y el bitmap
 * multicolor datos, matriz de pantalla y color RAM por celda de 8x8. El resto
 * de modos genera una sola capa. Los bytes se producen sin dependencias de
 * JavaFX para poder reutilizar el servicio en plugins o en linea de comandos.
 */
public final class RetroImagePacker {
    /** PRIOR usado en los modos GTIA 7-9 para mostrar los cuatro playfields. */
    public static final int GTIA_PRIOR_ALL = 3;

    private RetroImagePacker() {
    }

    /**
     * Empaqueta los indices de color en las capas del modo indicado.
     *
     * @param mode modo de video destino
     * @param indices indices de color por pixel (fila a fila), ya reducidos a
     *        {@code [0, palette.length)}
     * @param palette colores ARGB usados por los indices
     * @param hardwareIndices numero de color de hardware de cada entrada de
     *        {@code palette}; el C64 lo usa para escribir el color RAM
     * @param width ancho en pixeles
     * @param height alto en pixeles
     * @return capas generadas, en orden
     */
    public static List<RetroImageLayer> pack(RetroImageMode mode, int[] indices, int[] palette,
                                             int[] hardwareIndices, int width, int height) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(indices, "indices");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(hardwareIndices, "hardwareIndices");
        mode.validateDimensions(width, height);
        if (indices.length != width * height) {
            throw new IllegalArgumentException("El numero de indices no coincide con las dimensiones.");
        }
        if (hardwareIndices.length != palette.length) {
            throw new IllegalArgumentException("Cada color de la paleta necesita su indice de hardware.");
        }

        return switch (mode.getLayout()) {
            case LINEAR -> List.of(new RetroImageLayer("", "datos de la imagen", packLinear(mode, indices, width, height)));
            case GTIA_MODE_7 -> List.of(new RetroImageLayer("", "datos de la imagen (PRIOR 3)", packGti7(indices, width, height)));
            case TILE_PLANAR -> List.of(new RetroImageLayer("", "tiles de " + (width / 8) + "x" + (height / 8),
                    packTilePlanar(indices, width, height)));
            case C64_CHARSET -> packC64Charset(indices, hardwareIndices, width, height);
            case C64_BITMAP -> packC64Bitmap(indices, hardwareIndices, width, height);
        };
    }

    /** Filas continuas, el pixel de la izquierda en el bit mas alto. */
    public static byte[] packLinear(RetroImageMode mode, int[] indices, int width, int height) {
        int bitsPerPixel = mode.getBitsPerPixel();
        int pixelsPerByte = 8 / bitsPerPixel;
        int bytesPerRow = width / pixelsPerByte;
        int mask = (1 << bitsPerPixel) - 1;
        byte[] data = new byte[bytesPerRow * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = indices[y * width + x] & mask;
                int bitOffset = 8 - bitsPerPixel - (x % pixelsPerByte) * bitsPerPixel;
                int byteIndex = y * bytesPerRow + x / pixelsPerByte;
                data[byteIndex] |= (byte) (color << bitOffset);
            }
        }
        return data;
    }

    /**
     * GTIA modos 7-9: 4bpp con 2 pixeles por byte. El pixel de la izquierda
     * ocupa el nibble alto y el de la derecha el bajo, y en ambos los dos bits
     * mas altos son el color (PRIOR).
     */
    public static byte[] packGti7(int[] indices, int width, int height) {
        int bytesPerRow = width / 2;
        byte[] data = new byte[bytesPerRow * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x += 2) {
                int left = indices[y * width + x] & 0x03;
                int right = indices[y * width + x + 1] & 0x03;
                data[y * bytesPerRow + x / 2] = (byte) (GTIA_PRIOR_ALL << 6 | left | (right << 2));
            }
        }
        return data;
    }

    /**
     * Tiles de 4bpp al estilo SMS/GG: por cada columna del tile se escriben
     * dos bytes, primero el plano de los bits bajos y luego el de los altos.
     * Un tile de 8x8 ocupa 32 bytes.
     */
    public static byte[] packTilePlanar(int[] indices, int width, int height) {
        int tileSize = RetroImageMode.TILE_SIZE;
        int tilesX = width / tileSize;
        int tilesY = height / tileSize;
        byte[] data = new byte[tilesX * tilesY * tileSize * 4];
        int tileOffset = 0;
        for (int tileY = 0; tileY < tilesY; tileY++) {
            for (int tileX = 0; tileX < tilesX; tileX++) {
                for (int column = 0; column < tileSize; column++) {
                    int lowPlane = 0;
                    int highPlane = 0;
                    for (int row = 0; row < tileSize; row++) {
                        int color = indices[(tileY * tileSize + row) * width + tileX * tileSize + column] & 0x0F;
                        int bit = 7 - row;
                        if ((color & 0x01) != 0) {
                            lowPlane |= 1 << bit;
                        }
                        if ((color & 0x02) != 0) {
                            highPlane |= 1 << bit;
                        }
                    }
                    data[tileOffset++] = (byte) lowPlane;
                    data[tileOffset++] = (byte) highPlane;
                }
            }
        }
        return data;
    }

    /**
     * C64 bitmap multicolor (160x200, 2bpp). En este modo el VIC-II toma los
     * cuatro colores de cada celda de 8x8 de sitios distintos:
     *
     * <ul>
     *   <li>00: color de fondo global ($D021)</li>
     *   <li>01: nibble alto de la matriz de pantalla de la celda</li>
     *   <li>10: nibble bajo de la matriz de pantalla de la celda</li>
     *   <li>11: nibble bajo del color RAM de la celda</li>
     * </ul>
     *
     * Por eso se generan tres capas: pixeles (2bpp), matriz de pantalla (1
     * byte por celda) y color RAM (1 byte por celda). El valor 00 usa un unico
     * color de fondo global ($D021) para toda la imagen, asi que se elige el
     * color mas usado de la imagen completa y cada celda reparte sus otros tres
     * colores entre el nibble alto, el nibble bajo y el color RAM.
     */
    public static List<RetroImageLayer> packC64Bitmap(int[] indices, int[] hardwareIndices, int width, int height) {
        int tileSize = RetroImageMode.TILE_SIZE;
        int cellsX = width / tileSize;
        int cellsY = height / tileSize;
        int paletteSize = hardwareIndices.length;
        int bitmapBytesPerRow = width / 4;
        byte[] bitmap = new byte[bitmapBytesPerRow * height];
        byte[] screen = new byte[cellsX * cellsY];
        byte[] colors = new byte[cellsX * cellsY];
        int background = c64BitmapBackgroundPosition(indices, hardwareIndices);

        int cellIndex = 0;
        for (int cellY = 0; cellY < cellsY; cellY++) {
            for (int cellX = 0; cellX < cellsX; cellX++) {
                int[] cellIndices = extractCell(indices, width, cellX * tileSize, cellY * tileSize, tileSize);
                int[] others = RetroQuantizer.mostUsedOrderedExcluding(cellIndices, paletteSize, 3, background);
                int[] selected = new int[1 + others.length];
                selected[0] = background;
                System.arraycopy(others, 0, selected, 1, others.length);
                int screenHigh = valueAt(selected, 1);
                int screenLow = valueAt(selected, 2);
                int colorRam = valueAt(selected, 3);
                int[] remapped = RetroQuantizer.remap(cellIndices, selected);

                for (int row = 0; row < tileSize; row++) {
                    for (int column = 0; column < tileSize; column++) {
                        int pixel = remapped[row * tileSize + column] & 0x03;
                        int bitOffset = 6 - (column % 4) * 2;
                        int offset = (cellY * tileSize + row) * bitmapBytesPerRow
                                + (cellX * tileSize + column) / 4;
                        bitmap[offset] |= (byte) (pixel << bitOffset);
                    }
                }
                screen[cellIndex] = (byte) ((hardwareIndex(hardwareIndices, screenHigh) << 4)
                        | hardwareIndex(hardwareIndices, screenLow));
                colors[cellIndex] = (byte) hardwareIndex(hardwareIndices, colorRam);
                cellIndex++;
            }
        }
        return List.of(
                new RetroImageLayer("bitmap", "pixeles 2bpp (0 fondo, 1/2 pantalla, 3 color RAM)", bitmap),
                new RetroImageLayer("screen", "matriz de pantalla: nibble alto = 01, nibble bajo = 10", screen),
                new RetroImageLayer("colors", "color RAM: nibble bajo = 11", colors));
    }

    /**
     * C64 juego de caracteres: por cada caracter de 8x8, 8 bytes de 1bpp y un
     * byte de color RAM con el primer plano en el nibble alto y el fondo en el
     * bajo. Los dos colores se eligen por caracter usando los mas usados.
     */
    public static List<RetroImageLayer> packC64Charset(int[] indices, int[] hardwareIndices, int width, int height) {
        int tileSize = RetroImageMode.TILE_SIZE;
        int charsX = width / tileSize;
        int charsY = height / tileSize;
        int paletteSize = hardwareIndices.length;
        byte[] chars = new byte[charsX * charsY * tileSize];
        byte[] colors = new byte[charsX * charsY];

        int charIndex = 0;
        for (int charY = 0; charY < charsY; charY++) {
            for (int charX = 0; charX < charsX; charX++) {
                int[] charIndices = extractCell(indices, width, charX * tileSize, charY * tileSize, tileSize);
                int[] selected = RetroQuantizer.mostUsedOrdered(charIndices, paletteSize, Math.min(2, paletteSize));
                int background = valueAt(selected, 0);
                int foreground = valueAt(selected, 1);
                int[] remapped = RetroQuantizer.remap(charIndices, selected);

                for (int row = 0; row < tileSize; row++) {
                    int value = 0;
                    for (int column = 0; column < tileSize; column++) {
                        if (remapped[row * tileSize + column] == 1) {
                            value |= 1 << (7 - column);
                        }
                    }
                    chars[charIndex * tileSize + row] = (byte) value;
                }
                colors[charIndex] = (byte) ((hardwareIndex(hardwareIndices, foreground) << 4)
                        | hardwareIndex(hardwareIndices, background));
                charIndex++;
            }
        }
        return List.of(
                new RetroImageLayer("chars", "caracteres 8x8 en 1bpp", chars),
                new RetroImageLayer("colors", "color RAM por caracter: nibble alto = primer plano, bajo = fondo", colors));
    }

    /**
     * Copia una celda de {@code size}x{@code size} pixeles. Los indices de la
     * imagen van por filas, asi que hay que reunirlos fila a fila: copiar un
     * bloque contiguo solo seria correcto si la imagen tuviera una sola celda
     * por fila.
     */
    private static int[] extractCell(int[] indices, int width, int originX, int originY, int size) {
        int[] cell = new int[size * size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(indices, (originY + row) * width + originX, cell, row * size, size);
        }
        return cell;
    }

    /**
     * Posicion en la paleta efectiva del color de fondo global del bitmap
     * multicolor del C64, es decir, el que hay que escribir en $D021. Como el
     * valor 00 de los pixeles lo usa el VIC-II en todas las celdas, el fondo
     * tiene que ser el mismo para toda la imagen: se toma el color mas usado.
     */
    public static int c64BitmapBackgroundPosition(int[] indices, int[] hardwareIndices) {
        int[] selected = RetroQuantizer.mostUsedOrdered(indices, hardwareIndices.length, 1);
        if (selected.length == 0) {
            throw new IllegalArgumentException("La imagen no tiene pixeles que empaquetar.");
        }
        return selected[0];
    }

    private static int valueAt(int[] values, int position) {
        if (values.length == 0) {
            throw new IllegalArgumentException("No hay colores seleccionados.");
        }
        return values[Math.min(position, values.length - 1)];
    }

    /** Numero de color de hardware (0-15) que hay que escribir en el VIC-II. */
    private static int hardwareIndex(int[] hardwareIndices, int position) {
        return hardwareIndices[position] & 0x0F;
    }
    /** Nombres de las capas generadas por el modo, con sufijo opcional. */
    public static List<String> layerNames(RetroImageMode mode, String baseName) {
        return switch (mode.getLayout()) {
            case C64_CHARSET -> List.of(baseName + "_chars", baseName + "_colors");
            case C64_BITMAP -> List.of(baseName + "_bitmap", baseName + "_screen", baseName + "_colors");
            default -> List.of(baseName);
        };
    }
}
