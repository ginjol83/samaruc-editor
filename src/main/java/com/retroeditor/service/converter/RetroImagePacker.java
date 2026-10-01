package com.retroeditor.service.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
            case NES_2BPP -> packNES(indices, palette, hardwareIndices, width, height);
            case C64_CHARSET -> packC64Charset(indices, hardwareIndices, width, height);
            case C64_BITMAP -> packC64Bitmap(indices, hardwareIndices, width, height);
            case MSX_TILES -> packMsxTiles(indices, hardwareIndices, width, height);
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
     * NES: tiles 8x8 de 2bpp en tres capas.
     *
     * Cada tile ocupa 16 bytes: los 8 primeros el plano 0 (bit 0 de cada
     * pixel) y los 8 siguientes el plano 1 (bit 1), de la fila 0 a la 7. El
     * pixel vale de 0 a 3 y es el indice dentro de la subpaleta de la celda.
     *
     * El PPU elige los colores por celda de 16x16: cada una escoge hasta 4
     * colores de la paleta de 64 entradas y los guarda en una de las 4
     * subpaletas. Si ninguna subpaleta libre cabe con los colores de la celda,
     * se busca la que deje un error de color minimo y los pixeles se llevan a
     * sus tonos mas cercanos.
     *
     * @return capas {@code tiles}, {@code attributes} (2 bits por cuadrante de
     *         8x8 con el numero de subpaleta) y {@code palette} (los 16 bytes
     *         de paleta RAM del PPU, {@code 0xFF} para los huecos sin usar)
     */
    public static List<RetroImageLayer> packNES(int[] indices, int[] palette, int[] hardwareIndices,
                                                int width, int height) {
        int tileSize = RetroImageMode.TILE_SIZE;
        int tilesX = width / tileSize;
        int tilesY = height / tileSize;
        int cellsX = (width + 15) / 16;
        int cellsY = (height + 15) / 16;
        int paletteSize = hardwareIndices.length;

        byte[] patterns = new byte[tilesX * tilesY * tileSize * 2];
        byte[] attributes = new byte[cellsX * cellsY];
        byte[] paletteRam = new byte[16];
        Arrays.fill(paletteRam, (byte) 0xFF);
        int[][] subpalettes = new int[4][4];
        for (int[] subpalette : subpalettes) {
            Arrays.fill(subpalette, -1);
        }
        int[] fill = new int[4];
        // Color ARGB de cada entrada (0-63) de la paleta del PPU.
        int[] ppuArgb = new int[64];
        for (int i = 0; i < hardwareIndices.length; i++) {
            if (hardwareIndices[i] >= 0 && hardwareIndices[i] < ppuArgb.length) {
                ppuArgb[hardwareIndices[i]] = palette[i];
            }
        }

        for (int cellY = 0; cellY < cellsY; cellY++) {
            for (int cellX = 0; cellX < cellsX; cellX++) {
                int originX = cellX * 16;
                int originY = cellY * 16;
                int availWidth = Math.min(16, width - originX);
                int availHeight = Math.min(16, height - originY);
                int[] cellPositions = extractCell(indices, width, originX, originY, availWidth, availHeight);
                int[] chosen = RetroQuantizer.mostUsedOrdered(cellPositions, paletteSize, 4);
                int[] chosenPpu = new int[chosen.length];
                for (int i = 0; i < chosen.length; i++) {
                    chosenPpu[i] = hardwareIndices[chosen[i]];
                }

                int[] resolved = resolveNesSubpalette(chosenPpu, subpalettes, fill, paletteRam, ppuArgb);
                int subpalette = resolved[0];
                int[] stored = subpalettes[subpalette];
                boolean placed = resolved[1] == 1;

                // Slot de cada posicion de la paleta efectiva dentro de la subpaleta.
                int[] slotOfPpu = new int[64];
                Arrays.fill(slotOfPpu, -1);
                for (int i = 0; i < chosenPpu.length; i++) {
                    slotOfPpu[chosenPpu[i]] = placed ? storedSlotFor(stored, chosenPpu[i], ppuArgb) : i;
                }
                for (int position = 0; position < paletteSize; position++) {
                    int ppu = hardwareIndices[position];
                    if (slotOfPpu[ppu] < 0 && appearsIn(cellPositions, position)) {
                        slotOfPpu[ppu] = storedSlotFor(stored, ppu, ppuArgb);
                    }
                }

                for (int subY = 0; subY < 2; subY++) {
                    for (int subX = 0; subX < 2; subX++) {
                        int tileX = cellX * 2 + subX;
                        int tileY = cellY * 2 + subY;
                        if (tileX >= tilesX || tileY >= tilesY) {
                            continue;
                        }
                        byte[] tile = packNesTile(indices, hardwareIndices, slotOfPpu, width,
                                tileX * tileSize, tileY * tileSize);
                        System.arraycopy(tile, 0, patterns, (tileY * tilesX + tileX) * tileSize * 2, tile.length);
                    }
                }
                int quadrant = subpalette | (subpalette << 2) | (subpalette << 4) | (subpalette << 6);
                attributes[cellY * cellsX + cellX] = (byte) quadrant;
            }
        }
        return List.of(
                new RetroImageLayer("tiles", "tiles 2bpp: plano 0 en los 8 primeros bytes y plano 1 en los 8 siguientes", patterns),
                new RetroImageLayer("attributes", "atributos: 2 bits por cuadrante de 8x8 con el numero de subpaleta", attributes),
                new RetroImageLayer("palette", "paleta PPU para $3F00: 4 subpaletas de 4 colores (0xFF = sin usar)", paletteRam));
    }

    /**
     * Busca una subpaleta para los colores de una celda de 16x16.
     *
     * @return array de 2 elementos: el numero de subpaleta y 1 si los colores
     *         se han escrito en ella (0 si la celda se ha llevado a los tonos
     *         mas cercanos porque ninguna subpaleta cabia)
     */
    private static int[] resolveNesSubpalette(int[] chosenPpu, int[][] subpalettes, int[] fill,
                                              byte[] paletteRam, int[] ppuArgb) {
        // Reutilizar una subpaleta que ya tenga exactamente esos colores.
        for (int subpalette = 0; subpalette < 4; subpalette++) {
            int[] stored = subpalettes[subpalette];
            boolean reusable = true;
            for (int i = 0; i < chosenPpu.length; i++) {
                if (stored[i] != chosenPpu[i]) {
                    reusable = false;
                    break;
                }
            }
            if (reusable) {
                return new int[] { subpalette, 1 };
            }
        }

        // Si no, la subpaleta mas compacta donde quepan sin conflictos.
        int bestPlace = -1;
        int bestFill = Integer.MAX_VALUE;
        for (int subpalette = 0; subpalette < 4; subpalette++) {
            int[] stored = subpalettes[subpalette];
            boolean placeable = true;
            int newSlots = 0;
            for (int i = 0; i < chosenPpu.length; i++) {
                if (stored[i] != -1 && stored[i] != chosenPpu[i]) {
                    placeable = false;
                    break;
                }
                if (stored[i] == -1) {
                    newSlots++;
                }
            }
            if (placeable && fill[subpalette] + newSlots <= 4) {
                int used = subpalettes[subpalette].length - freeSlots(subpalettes[subpalette]);
                if (used < bestFill) {
                    bestFill = used;
                    bestPlace = subpalette;
                }
            }
        }
        if (bestPlace >= 0) {
            int[] stored = subpalettes[bestPlace];
            for (int i = 0; i < chosenPpu.length; i++) {
                if (stored[i] == -1) {
                    stored[i] = chosenPpu[i];
                    paletteRam[bestPlace * 4 + i] = (byte) chosenPpu[i];
                    fill[bestPlace]++;
                }
            }
            return new int[] { bestPlace, 1 };
        }

        // Ninguna subpaleta libre: usar la que pierda menos color de media.
        int bestSubpalette = 0;
        double bestError = Double.MAX_VALUE;
        for (int subpalette = 0; subpalette < 4; subpalette++) {
            int[] stored = subpalettes[subpalette];
            int filled = 4 - freeSlots(stored);
            if (filled == 0) {
                continue;
            }
            double error = 0;
            for (int ppu : chosenPpu) {
                error += colorDistance(ppuArgb[ppu], ppuArgb[nearestStoredPpu(stored, ppu, ppuArgb)]);
            }
            if (error < bestError) {
                bestError = error;
                bestSubpalette = subpalette;
            }
        }
        return new int[] { bestSubpalette, 0 };
    }

    /** Slot de {@code stored} cuyo color esta mas cerca de {@code ppu}. */
    private static int storedSlotFor(int[] stored, int ppu, int[] ppuArgb) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int slot = 0; slot < stored.length; slot++) {
            if (stored[slot] < 0) {
                continue;
            }
            double distance = colorDistance(ppuArgb[ppu], ppuArgb[stored[slot]]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = slot;
            }
        }
        return stored[best] >= 0 ? best : 0;
    }

    /** Entrada de {@code stored} mas cercana a {@code ppu}. */
    private static int nearestStoredPpu(int[] stored, int ppu, int[] ppuArgb) {
        int slot = storedSlotFor(stored, ppu, ppuArgb);
        return stored[slot] >= 0 ? stored[slot] : 0;
    }

    private static int freeSlots(int[] stored) {
        int free = 0;
        for (int entry : stored) {
            if (entry < 0) {
                free++;
            }
        }
        return free;
    }

    /** Empaqueta un tile 8x8 de 2bpp: 16 bytes, plano 0 abajo y plano 1 arriba. */
    private static byte[] packNesTile(int[] indices, int[] hardwareIndices, int[] slotOfPpu,
                                      int width, int originX, int originY) {
        byte[] data = new byte[16];
        for (int row = 0; row < RetroImageMode.TILE_SIZE; row++) {
            int lowPlane = 0;
            int highPlane = 0;
            for (int column = 0; column < RetroImageMode.TILE_SIZE; column++) {
                int position = indices[(originY + row) * width + originX + column];
                int slot = slotOfPpu[hardwareIndices[position]];
                if (slot < 0) {
                    slot = 0;
                }
                int bit = 1 << (7 - column);
                if ((slot & 0x01) != 0) {
                    lowPlane |= bit;
                }
                if ((slot & 0x02) != 0) {
                    highPlane |= bit;
                }
            }
            data[row] = (byte) lowPlane;
            data[8 + row] = (byte) highPlane;
        }
        return data;
    }

    /** Distancia de color igual que {@code RetroQuantizer} (pesos de luminancia). */
    private static double colorDistance(int one, int other) {
        double dr = ((one >> 16) & 0xFF) - ((other >> 16) & 0xFF);
        double dg = ((one >> 8) & 0xFF) - ((other >> 8) & 0xFF);
        double db = (one & 0xFF) - (other & 0xFF);
        return 0.299 * dr * dr + 0.587 * dg * dg + 0.114 * db * db;
    }

    private static boolean appearsIn(int[] cellPositions, int position) {
        for (int candidate : cellPositions) {
            if (candidate == position) {
                return true;
            }
        }
        return false;
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
        return packCharset1bpp(indices, hardwareIndices, width, height,
                "chars", "caracteres 8x8 en 1bpp",
                "colors", "color RAM por caracter: nibble alto = primer plano, bajo = fondo");
    }

    /**
     * MSX1 SCREEN 1 (el TMS9918 en modo 0, 32x24). El VDP no guarda la
     * pantalla como una foto: la reconstruye con tres tablas y por eso el
     * resultado no puede ser un byte por celda.
     *
     * <ul>
     *   <li><b>patterns</b>: tabla de generadores, 256 patrones de 8 bytes en
     *       1bpp (un byte por fila, pixel izquierdo en el bit alto). Como solo
     *       caben 256, los bloques iguales de la imagen se repiten una sola vez
     *       y el mapa decide donde se usa cada uno.</li>
     *   <li><b>map</b>: tabla de nombres, un byte por celda de 8x8 con el
     *       numero de patron (0-255).</li>
     *   <li><b>colors</b>: tabla de color, <b>un byte por cada 8
     *       caracteres</b>: el VDP divide el numero de caracter por 8, asi que
     *       los 256 patrones caben en 32 bytes. Nibble alto = primer plano,
     *       nibble bajo = fondo, y un nibble a 0 es transparente.</li>
     * </ul>
     *
     * <p>Como la tabla de color es por grupos de 8, los numeros de caracter se
     * reordenan por color antes de repartirlos: asi los que comparten color caen
     * en el mismo grupo y el byte de color les sirve a los ocho. Solo el ultimo
     * grupo de cada color puede mezclarlo con el color siguiente, y ahi se
     * lleva el color que mas se repite.</p>
     *
     * <p>El TMS9918 solo tiene dos colores por bloque 8x8, asi que se eligen
     * los dos mas usados del bloque.</p>
     */
    public static List<RetroImageLayer> packMsxTiles(int[] indices, int[] hardwareIndices, int width, int height) {
        int tileSize = RetroImageMode.TILE_SIZE;
        int charsX = width / tileSize;
        int charsY = height / tileSize;
        int cells = charsX * charsY;
        int paletteSize = hardwareIndices.length;

        List<byte[]> uniquePatterns = new ArrayList<>();
        List<Integer> uniqueColours = new ArrayList<>();
        int[] cellPattern = new int[cells];

        for (int cell = 0; cell < cells; cell++) {
            int charX = cell % charsX;
            int charY = cell / charsX;
            int[] charIndices = extractCell(indices, width, charX * tileSize, charY * tileSize, tileSize);
            int[] selected = RetroQuantizer.mostUsedOrdered(charIndices, paletteSize, Math.min(2, paletteSize));
            int background = valueAt(selected, 0);
            int foreground = valueAt(selected, 1);
            int[] remapped = RetroQuantizer.remap(charIndices, selected);

            byte[] pattern = new byte[tileSize];
            for (int row = 0; row < tileSize; row++) {
                int value = 0;
                for (int column = 0; column < tileSize; column++) {
                    if (remapped[row * tileSize + column] == 1) {
                        value |= 1 << (7 - column);
                    }
                }
                pattern[row] = (byte) value;
            }
            int colour = (hardwareIndex(hardwareIndices, foreground) << 4)
                    | hardwareIndex(hardwareIndices, background);

            int existing = indexOfPattern(uniquePatterns, pattern);
            if (existing < 0) {
                if (uniquePatterns.size() >= RetroImageMode.MSX_SCREEN1_PATTERNS) {
                    throw new IllegalArgumentException("SCREEN 1 del MSX1 solo tiene "
                            + RetroImageMode.MSX_SCREEN1_PATTERNS
                            + " patrones de 8 bytes (2048 bytes de VRAM) y la imagen necesita mas: "
                            + "usa una imagen mas pequena.");
                }
                existing = uniquePatterns.size();
                uniquePatterns.add(pattern);
                uniqueColours.add(colour);
            }
            cellPattern[cell] = existing;
        }

        MsxCharset charset = groupByColour(uniqueColours);
        byte[] patterns = new byte[uniquePatterns.size() * tileSize];
        for (int pattern = 0; pattern < uniquePatterns.size(); pattern++) {
            System.arraycopy(uniquePatterns.get(pattern), 0, patterns,
                    charset.newCharacter()[pattern] * tileSize, tileSize);
        }

        byte[] map = new byte[cells];
        for (int cell = 0; cell < cells; cell++) {
            map[cell] = (byte) charset.newCharacter()[cellPattern[cell]];
        }

        return List.of(
                new RetroImageLayer("patterns", "tabla de generadores: 8 bytes de 1bpp por patron (un byte por "
                        + "fila, pixel izquierdo en el bit alto), " + patterns.length + " bytes", patterns),
                new RetroImageLayer("map", "tabla de nombres: un byte por celda de 8x8 con el numero de patron", map),
                new RetroImageLayer("colors", "tabla de color: un byte por cada 8 caracteres (el VDP divide el "
                        + "numero de caracter por 8), nibble alto = primer plano, bajo = fondo, 0 = transparente",
                        charset.colours()));
    }

    /**
     * Reparte los patrones en los grupos de la tabla de color, que son de 8
     * caracteres: el numero de caracter de un patron es su posicion, y el byte
     * del grupo se lleva el color que mas usan los miembros del grupo.
     *
     * <p>Ordenando antes los patrones por color, los del mismo color caen
     * juntos y cada grupo es de un solo color salvo, como mucho, en el ultimo
     * grupo de cada color. Asi el color de lamayoria de los bloques es exacto y
     * el numero de grupos siempre es el minimo,ceil(patrones / 8), que es lo que
     * hace que los datos quepan en los 32 bytes de la tabla.</p>
     *
     * @param colours color (nibble alto = primer plano, bajo = fondo) de cada
     *        patron, en el orden en que se han ido encontrando
     * @return el numero de caracter final de cada patron y el color de cada
     *         grupo de la tabla de color
     */
    private static MsxCharset groupByColour(List<Integer> colours) {
        int patterns = colours.size();
        int perGroup = RetroImageMode.MSX_SCREEN1_CHARS_PER_COLOUR;
        List<Integer> sorted = new ArrayList<>(patterns);
        for (int pattern = 0; pattern < patterns; pattern++) {
            sorted.add(pattern);
        }
        sorted.sort(Comparator.comparingInt(colours::get));

        int[] newCharacter = new int[patterns];
        for (int character = 0; character < patterns; character++) {
            newCharacter[sorted.get(character)] = character;
        }
        byte[] groups = new byte[msxColourTableLength(patterns)];
        for (int group = 0; group < groups.length; group++) {
            groups[group] = (byte) dominantColour(colours, sorted, group * perGroup, perGroup, patterns);
        }
        return new MsxCharset(newCharacter, groups);
    }

    /** Color que mas se repite en un grupo de la tabla de color. */
    private static int dominantColour(List<Integer> colours, List<Integer> sorted, int from, int perGroup, int patterns) {
        int end = Math.min(from + perGroup, patterns);
        int best = colours.get(sorted.get(from));
        int bestCount = 0;
        for (int position = from; position < end; position++) {
            int colour = colours.get(sorted.get(position));
            int count = 0;
            for (int other = from; other < end; other++) {
                if (colours.get(sorted.get(other)) == colour) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = colour;
            }
        }
        return best;
    }

    /** Bytes de la tabla de color de SCREEN 1: uno por cada 8 caracteres. */
    public static int msxColourTableLength(int patterns) {
        return (patterns + RetroImageMode.MSX_SCREEN1_CHARS_PER_COLOUR - 1)
                / RetroImageMode.MSX_SCREEN1_CHARS_PER_COLOUR;
    }

    /**
     * Numeros de caracter de los patrones de MSX ya agrupados por color.
     *
     * @param newCharacter numero de caracter final de cada patron original
     * @param colours color de cada grupo de la tabla de color
     */
    private record MsxCharset(int[] newCharacter, byte[] colours) {
    }

    private static int indexOfPattern(List<byte[]> patterns, byte[] pattern) {
        for (int i = 0; i < patterns.size(); i++) {
            if (Arrays.equals(patterns.get(i), pattern)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Caracteres de 8x8 en 1bpp con un byte de color por caracter. Lo usa el
     * charset del C64, que guarda el color en el color RAM con el primer plano
     * en el nibble alto y el fondo en el bajo.
     */
    private static List<RetroImageLayer> packCharset1bpp(int[] indices, int[] hardwareIndices, int width, int height,
                                                          String patternsName, String patternsDescription,
                                                          String colorsName, String colorsDescription) {
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
                new RetroImageLayer(patternsName, patternsDescription, chars),
                new RetroImageLayer(colorsName, colorsDescription, colors));
    }

    /**
     * Copia una celda de {@code size}x{@code size} pixeles. Los indices de la
     * imagen van por filas, asi que hay que reunirlos fila a fila: copiar un
     * bloque contiguo solo seria correcto si la imagen tuviera una sola celda
     * por fila.
     */
    private static int[] extractCell(int[] indices, int width, int originX, int originY, int size) {
        return extractCell(indices, width, originX, originY, size, size);
    }

    /** Igual pero con el ancho y el alto efectivos (para las celdas del borde). */
    private static int[] extractCell(int[] indices, int width, int originX, int originY, int availWidth,
                                     int availHeight) {
        int[] cell = new int[availWidth * availHeight];
        for (int row = 0; row < availHeight; row++) {
            System.arraycopy(indices, (originY + row) * width + originX, cell, row * availWidth, availWidth);
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
            case MSX_TILES -> List.of(baseName + "_patterns", baseName + "_map", baseName + "_colors");
            case NES_2BPP -> List.of(baseName, baseName + "_attributes", baseName + "_palette");
            default -> List.of(baseName);
        };
    }
}
