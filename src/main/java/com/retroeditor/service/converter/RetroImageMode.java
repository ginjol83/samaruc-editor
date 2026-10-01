package com.retroeditor.service.converter;

import java.util.List;

/**
 * Modos de video hardware soportados por el conversor PNG a C.
 *
 * Cada modo fija:
 * <ul>
 *   <li>los bits por pixel y como se empaquetan en memoria,</li>
 *   <li>la paleta de destino,</li>
 *   <li>el numero de colores simultaneos,</li>
 *   <li>las capas de datos que hay que generar (algunas maquinas, como el
 *       C64 y el MSX, separan el pixel del color en dos arrays).</li>
 * </ul>
 */
public enum RetroImageMode {
    /** CPC Mode 0: 640x200, 2 colores, 1bpp (8 px por byte). */
    CPC_MODE_0(RetroImagePlatform.CPC, "CPC Mode 0 (2 colores, 1bpp)", 1, 640, 200, 2, Layout.LINEAR),

    /** CPC Mode 1: 320x200, 4 colores, 2bpp (4 px por byte). */
    CPC_MODE_1(RetroImagePlatform.CPC, "CPC Mode 1 (4 colores, 2bpp)", 2, 320, 200, 4, Layout.LINEAR),

    /** CPC Mode 2: 160x200, 16 colores, 4bpp (2 px por byte, izq. en nibble alto). */
    CPC_MODE_2(RetroImagePlatform.CPC, "CPC Mode 2 (16 colores, 4bpp)", 4, 160, 200, 16, Layout.LINEAR),

    /**
     * Atari graficos 0/1 (ANTIC 2): 4 colores de playfield, 2bpp. El GTIA
     * mezcla los dos bits con el color del jugador para dar los cuatro colores,
     * asi que hacen falta 2 bits por pixel en una sola capa de datos. (Con 1bpp
     * el ANTIC leeria dos planos en direcciones distintas y solo se podrian
     * guardar 2 de los 4 colores en el mismo array.)
     */
    ATARI_PLAYFIELD(RetroImagePlatform.ATARI, "Atari graficos 0/1 (4 colores, 2bpp)", 2, 320, 192, 4, Layout.LINEAR),

    /**
     * Atari GTIA modo 7: 320x192, 4bpp (2 px por byte). El nibble alto es el
     * indice PRIOR y el bajo el color, asi que en la practice se muestran 4
     * colores simultaneos (uno por playfield).
     */
    ATARI_MODE_7(RetroImagePlatform.ATARI, "Atari GTIA modo 7 (4 colores, 4bpp)", 4, 320, 192, 4, Layout.GTIA_MODE_7),

    /**
     * C64 juego de caracteres: 8x8, 1bpp + un byte de color RAM por caracter. Se
     * cuantiza contra los 16 colores del VIC-II porque cada caracter elige sus
     * dos colores por separado (fondo en el nibble bajo, primer plano en el alto).
     */
    C64_CHARSET(RetroImagePlatform.C64, "C64 caracteres 8x8 (1bpp + color RAM)", 1, 320, 200, 16, Layout.C64_CHARSET),

    /**
     * C64 bitmap multicolor: 160x200, 2bpp + matriz de pantalla + color RAM. Al
     * igual que el charset se cuantiza contra los 16 colores, ya que cada celda
     * de 8x8 escoge tres colores mas el fondo global.
     */
    C64_BITMAP(RetroImagePlatform.C64, "C64 bitmap multicolor (2bpp + color RAM)", 2, 160, 200, 16, Layout.C64_BITMAP),

    /** Master System modo 4: tiles 8x8 de 4bpp en dos planos por columna. */
    MASTER_SYSTEM_TILES(RetroImagePlatform.MASTER_SYSTEM, "Master System modo 4 (tiles 4bpp)", 4, 256, 224, 16, Layout.TILE_PLANAR),

    /** Game Gear modo 4: mismo empaquetado que el Master System, paleta GG. */
    GAME_GEAR_TILES(RetroImagePlatform.GAME_GEAR, "Game Gear modo 4 (tiles 4bpp)", 4, 256, 224, 16, Layout.TILE_PLANAR),

    /**
     * MSX1 SCREEN 1 (el TMS9918 en modo 0): 256x192 de 32x24 caracteres 8x8 en
     * 1bpp, con el pixel de la izquierda en el bit alto y dos colores por
     * caracter (nibble alto = primer plano, nibble bajo = fondo; un nibble a 0
     * es transparente).
     *
     * <p>El VDP reconstruye la pantalla con tres tablas, no con un byte por
     * celda: la de generadores (256 patrones de 8 bytes, 2048 bytes en total,
     * con los repetidos escritos una sola vez), la de nombres (un byte por
     * celda con el numero de patron) y la de color, que lleva <b>un byte por
     * cada 8 caracteres</b> porque el VDP divide el numero de caracter por 8.
     * Por eso el color de un bloque depende del grupo de 8 en el que caiga su
     * numero de patron.</p>
     *
     * <p>El TMS9918 no tiene ningun modo de tiles a 4bpp: sus dos planos de
     * bits por pixel solo existen en SCREEN 3, donde cada caracter son 8 bytes
     * de color por bloques de 4x4.</p>
     */
    MSX_TILES(RetroImagePlatform.MSX, "MSX SCREEN 1 (1bpp + tabla de color)", 1, 256, 192, 16, Layout.MSX_TILES),

    /**
     * NES: 256x240 de tiles 8x8 a 2bpp, 16 bytes por tile (los 8 primeros
     * bytes el plano 0 y los 8 siguientes el plano 1). Cada celda de 16x16
     * escoge una subpaleta de 4 colores de la paleta del PPU de 64 entradas,
     * como hace la tabla de atributos del NES.
     */
    NES_TILES(RetroImagePlatform.NES, "NES tiles 2bpp (PPU)", 2, 256, 240, 16, Layout.NES_2BPP);

    /** Tamano de tile en pixeles de los modos basados en tiles. */
    public static final int TILE_SIZE = 8;

    /**
     * Patrones que caben en la tabla de generadores de SCREEN 1 del MSX1:
     * 2048 bytes de VRAM a 8 bytes por patron. Los bloques repetidos de la
     * imagen comparten patron, asi que una imagen puede tener mas celdas que
     * patrones, pero no mas patrones que este numero.
     */
    public static final int MSX_SCREEN1_PATTERNS = 2048 / TILE_SIZE;

    /**
     * Caracteres que comparten un byte de la tabla de color de SCREEN 1 del
     * MSX1: el hardware obtiene el byte dividiendo el numero de caracter por 8.
     */
    public static final int MSX_SCREEN1_CHARS_PER_COLOUR = 8;

    /** Formato en el que se guardan los pixeles de cada modo. */
    public enum Layout {
        /** Filas continuas: 8 / bitsPerPixel pixeles por byte, el de la izquierda en el bit alto. */
        LINEAR,
        /** Atari GTIA 7-9: 4bpp con nibble alto = PRIOR (3) y nibble bajo = color. */
        GTIA_MODE_7,
        /** C64: 1bpp por caracter 8x8 (8 bytes) + 1 byte de color RAM por caracter. */
        C64_CHARSET,
        /** C64: 2bpp por pixel (4 px/byte) + 1 byte de pantalla y 1 de color RAM por celda 8x8. */
        C64_BITMAP,
        /** Tiles 8x8 de 4bpp: por columna, byte del plano bajo y byte del plano alto. */
        TILE_PLANAR,
        /** TMS9918: 1bpp por caracter 8x8 (8 bytes) + 1 byte de tabla de color por caracter. */
        MSX_TILES,
        /** NES: tiles 8x8 de 2bpp (16 bytes por tile, plano 0 y plano 1) + atributos por celda de 16x16. */
        NES_2BPP
    }

    private final RetroImagePlatform platform;
    private final String displayName;
    private final int bitsPerPixel;
    private final int defaultWidth;
    private final int defaultHeight;
    private final int maxColors;
    private final Layout layout;

    RetroImageMode(RetroImagePlatform platform, String displayName, int bitsPerPixel,
                   int defaultWidth, int defaultHeight, int maxColors, Layout layout) {
        this.platform = platform;
        this.displayName = displayName;
        this.bitsPerPixel = bitsPerPixel;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.maxColors = maxColors;
        this.layout = layout;
    }

    public RetroImagePlatform getPlatform() {
        return platform;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBitsPerPixel() {
        return bitsPerPixel;
    }

    public int getDefaultWidth() {
        return defaultWidth;
    }

    public int getDefaultHeight() {
        return defaultHeight;
    }

    public int getMaxColors() {
        return maxColors;
    }

    public Layout getLayout() {
        return layout;
    }

    /** Paleta de hardware completa del modo. */
    public int[] palette() {
        return switch (this) {
            case CPC_MODE_0 -> RetroHardwarePalette.cpcMode0();
            case CPC_MODE_1 -> RetroHardwarePalette.cpcMode1();
            case CPC_MODE_2 -> RetroHardwarePalette.cpc16();
            case ATARI_PLAYFIELD -> RetroHardwarePalette.atari4();
            case ATARI_MODE_7 -> RetroHardwarePalette.atari16();
            case C64_CHARSET, C64_BITMAP -> RetroHardwarePalette.c64();
            case MASTER_SYSTEM_TILES -> RetroHardwarePalette.masterSystem();
            case GAME_GEAR_TILES -> RetroHardwarePalette.gameGear();
            case MSX_TILES -> RetroHardwarePalette.msx();
            case NES_TILES -> RetroHardwarePalette.nes();
        };
    }

    /**
     * Valores de CRAM de la paleta (solo SMS/Game Gear; null en el resto). En el
     * Master System cada valor es el byte {@code %00BBGGRR} que se escribe en
     * {@code $C000}; en el Game Gear es la palabra de 12 bits
     * {@code 0BBBBBGGG GGGRRRRR}, que se escribe en little endian.
     *
     * <p>El MSX1 no entra aqui: su tabla de color de SCREEN 1 guarda indices de
     * 4 bits, no los 6 bits de la CRAM, y el registro 7 del TMS9918 solo define
     * el color de texto y el del borde.</p>
     *
     * @param colors colores ARGB que se han usado en la conversion, en el mismo
     *        orden que los indices de los pixeles
     */
    public int[] cramBytes(int[] colors) {
        return switch (this) {
            case MASTER_SYSTEM_TILES -> cram(colors, RetroHardwarePalette::masterSystemCramByte);
            case GAME_GEAR_TILES -> cram(colors, RetroHardwarePalette::gameGearCramValue);
            case NES_TILES -> null;
            default -> null;
        };
    }

    private int[] cram(int[] colors, java.util.function.IntUnaryOperator encoder) {
        int[] result = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            result[i] = encoder.applyAsInt(colors[i]) & 0xFF;
        }
        return result;
    }

    /** Pixels que caben en un byte segun el layout del modo. */
    public int pixelsPerByte() {
        return switch (layout) {
            case LINEAR, GTIA_MODE_7 -> 8 / bitsPerPixel;
            case C64_CHARSET, C64_BITMAP, MSX_TILES -> 8;
            case TILE_PLANAR -> 2;
            case NES_2BPP -> 4;
        };
    }

    /**
     * Bytes por linea de la capa de pixeles. En los modos por celda (C64, MSX)
     * una linea son los bytes de todos los caracteres o celdas de la fila.
     */
    public int bytesPerRow(int width) {
        return switch (layout) {
            case LINEAR, GTIA_MODE_7 -> width * bitsPerPixel / 8;
            case C64_CHARSET, MSX_TILES -> width / TILE_SIZE * TILE_SIZE;
            case C64_BITMAP -> width / 4;
            case TILE_PLANAR -> width / TILE_SIZE * (TILE_SIZE * 4);
            case NES_2BPP -> width / TILE_SIZE * (TILE_SIZE * 2);
        };
    }

    /** El modo obliga a que el alto y el ancho sean multiplos de 8. */
    public boolean requiresTileAligned() {
        return layout == Layout.TILE_PLANAR || layout == Layout.C64_CHARSET
                || layout == Layout.C64_BITMAP || layout == Layout.MSX_TILES
                || layout == Layout.NES_2BPP;
    }

    /** Valida las dimensiones para este modo. */
    public void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Las dimensiones deben ser mayores que 0.");
        }
        if (requiresTileAligned() && (width % TILE_SIZE != 0 || height % TILE_SIZE != 0)) {
            throw new IllegalArgumentException(
                displayName + " requiere dimensiones multiplos de " + TILE_SIZE + ".");
        }
        if (!requiresTileAligned() && width % pixelsPerByte() != 0) {
            throw new IllegalArgumentException(
                displayName + " requiere un ancho multiplos de " + pixelsPerByte() + " pixeles.");
        }
    }

    /**
     * Modos en los que el indice 0 de la paleta no es un color mas, sino el
     * transparente: en el Master System y el Game Gear es el color de sprite
     * transparente y en el MSX el nibble a 0 de la tabla de color, que deja ver
     * el fondo del borde. Sirve para que los pixeles transparentes de la imagen
     * se queden en esa posicion.
     */
    public boolean hasTransparentFirstColor() {
        return this == MASTER_SYSTEM_TILES || this == GAME_GEAR_TILES || this == MSX_TILES;
    }

    /** Modos disponibles, en el orden en que se muestran en la interfaz. */
    public static List<RetroImageMode> valuesInUiOrder() {
        return List.of(values());
    }

    @Override
    public String toString() {
        return displayName;
    }
}
