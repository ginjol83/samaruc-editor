package com.retroeditor.service.converter;

/**
 * Paletas de hardware (RGB) usadas por el conversor PNG a C.
 *
 * A diferencia de las paletas artisticas del editor de tiles (pensadas para
 * dibujar rapido), aqui los valores son los reales de cada chip para que el
 * indice generado corresponda al color que la maquina muestra:
 *
 * <ul>
 *   <li>CPC: 27 colores posibles (RGB de 3 niveles: 0 / 128 / 255). La tabla
 *       es la de los numeros de tinta del firmware (0-15) del CPC464, con su
 *       color de hardware equivalente.</li>
 *   <li>C64: VIC-II, 16 colores (2 bits de color + 2 bits de luminancia).</li>
 *   <li>Atari: GTIA, 16 colores. Los valores son la aproximacion de uso habitual
 *       en emuladores, porque la paleta exacta depende del modelo de
 *       television y de las resistencias de cada unidad.</li>
 *   <li>Master System: TMS9918, 16 colores de 6 bits (2 bits por canal,
 *       niveles 0/85/170/255). El color 0 es transparente en sprites.</li>
 *   <li>Game Gear: 12 bits (3 bits por canal, 8 niveles).</li>
 * </ul>
 */
public final class RetroHardwarePalette {
    private static final int HALF = 128;

    private RetroHardwarePalette() {
    }

    /**
     * CPC Mode 0: 2 colores. El CPC no tiene un blanco puro, asi que el segundo
     * color es la tinta mas clara de la tabla (14, azul pastel).
     */
    public static int[] cpcMode0() {
        int[] inks = cpc16();
        return new int[] { inks[0], inks[14] };
    }

    /**
     * CPC Mode 1: 4 colores, las cuatro primeras tintas de la tabla del
     * firmware (negro, azul, azul brillante y rojo).
     */
    public static int[] cpcMode1() {
        int[] inks = cpc16();
        return new int[] { inks[0], inks[1], inks[2], inks[3] };
    }

    /**
     * CPC: los 16 numeros de tinta del firmware con su color de hardware
     * equivalente (0 = negro, 1 = azul, 2 = azul brillante, 3 = rojo, ...).
     */
    public static int[] cpc16() {
        return new int[] {
            rgb(0x000000), // 0  negro
            rgb(0x000080), // 1  azul
            rgb(0x0000FF), // 2  azul brillante
            rgb(0x800000), // 3  rojo
            rgb(0x800080), // 4  magenta
            rgb(0x8000FF), // 5  malva
            rgb(0xFF0000), // 6  rojo brillante
            rgb(0xFF0080), // 7  morado
            rgb(0xFF00FF), // 8  magenta brillante
            rgb(0x008000), // 9  verde
            rgb(0x008080), // 10 cyan
            rgb(0x0080FF), // 11 azul cielo
            rgb(0x808000), // 12 amarillo
            rgb(0x808080), // 13 blanco (gris medio)
            rgb(0x8080FF), // 14 azul pastel
            rgb(0xFF8000)  // 15 naranja
        };
    }

    /**
     * C64: 16 colores del VIC-II (mismos valores que usa el editor de tiles).
     */
    public static int[] c64() {
        return new int[] {
            rgb(0x000000), rgb(0xFFFFFF), rgb(0x68372B), rgb(0x70A4B2),
            rgb(0x6F3D86), rgb(0x588D43), rgb(0x352879), rgb(0xB8C76F),
            rgb(0x6F4F25), rgb(0x433900), rgb(0x9A6759), rgb(0x444444),
            rgb(0x6C6C6C), rgb(0x9AD284), rgb(0x6C5EB5), rgb(0x959595)
        };
    }

    /**
     * Atari 8-bit: 4 colores para graficos 0/1 (ANTIC 2). Son los cuatro
     * primeros registros de color de la paleta GTIA.
     */
    public static int[] atari4() {
        return new int[] {
            rgb(0x000000), rgb(0x3B3B3B), rgb(0x757575), rgb(0xADADAD)
        };
    }

    /** Atari 8-bit: 16 colores GTIA. */
    public static int[] atari16() {
        return new int[] {
            rgb(0x000000), rgb(0x3B3B3B), rgb(0x757575), rgb(0xADADAD),
            rgb(0xE0E0E0), rgb(0xFFFFFF), rgb(0xFF5555), rgb(0xFFAA55),
            rgb(0xFFFF55), rgb(0x55FF55), rgb(0x55FFFF), rgb(0x5555FF),
            rgb(0xFF55FF), rgb(0x8B0000), rgb(0x00008B), rgb(0x006400)
        };
    }

    /**
     * Master System: paleta estandar de 16 colores del TMS9918.
     *
     * El VDP guarda 2 bits por canal en formato {@code %00BBGGRR} con niveles
     * 0/85/170/255. El color 0 es transparente en sprites.
     */
    public static int[] masterSystem() {
        return new int[] {
            rgb(0x000000), // 0  transparente
            rgb(0x000000), // 1  negro
            rgb(0x00AA00), // 2  verde medio
            rgb(0x00FF00), // 3  verde claro
            rgb(0x000055), // 4  azul oscuro
            rgb(0x0000FF), // 5  azul claro
            rgb(0x550000), // 6  rojo oscuro
            rgb(0x00FFFF), // 7  cyan
            rgb(0xAA0000), // 8  rojo medio
            rgb(0xFF0000), // 9  rojo claro
            rgb(0x555500), // 10 amarillo oscuro
            rgb(0xFFFF00), // 11 amarillo claro
            rgb(0x005500), // 12 verde oscuro
            rgb(0xFF00FF), // 13 magenta
            rgb(0x555555), // 14 gris
            rgb(0xFFFFFF)  // 15 blanco
        };
    }

    /**
     * Game Gear: 16 colores de 12 bits (3 bits por canal, 8 niveles).
     * Se usan los mismos colores base que el Master System; cada entrada de
     * CRAM es una palabra de 12 bits, no un byte.
     */
    public static int[] gameGear() {
        return masterSystem();
    }

    /**
     * Byte de CRAM del Master System para un color de la paleta.
     *
     * El registro es {@code %00BBGGRR} con dos bits por canal, y esos dos bits
     * son el numero de nivel (0 = apagado, 1 = 85, 2 = 170, 3 = 255), no dos
     * umbrales independientes: por eso el verde 170 es {@code %10} y no
     * {@code %11}.
     */
    public static int masterSystemCramByte(int argb) {
        int r = level2((argb >> 16) & 0xFF);
        int g = level2((argb >> 8) & 0xFF);
        int b = level2(argb & 0xFF);
        return (b << 4) | (g << 2) | r;
    }

    /** Cuantiza un canal de 0-255 a los 4 niveles de 2 bits del TMS9918. */
    private static int level2(int component) {
        return Math.min(3, (component + 42) / 85);
    }

    /**
     * Valor de CRAM del Game Gear para un color de la paleta. Cada palabra de 16
     * bits guarda tres bits por canal: {@code %LLLLL BBB GGGR RRR}, es decir
     * rojo en los bits 0-2, verde en 4-6 y azul en 8-10, y los cinco bits altos
     * el numero de linea de paleta. Se escribe en little endian, asi que el byte
     * bajo es {@code value & 0xFF} y el alto {@code value >> 8}.
     */
    public static int gameGearCramValue(int argb) {
        int r = level3((argb >> 16) & 0xFF);
        int g = level3((argb >> 8) & 0xFF);
        int b = level3(argb & 0xFF);
        return (b << 8) | (g << 4) | r;
    }

    /** Cuantiza un canal de 0-255 a los 8 niveles del registro de 3 bits. */
    private static int level3(int component) {
        int value = component & 0xFF;
        int best = 0;
        for (int level = 1; level <= 7; level++) {
            if (Math.abs(LEVEL3[level] - value) <= Math.abs(LEVEL3[best] - value)) {
                best = level;
            }
        }
        return best;
    }

    /** Los 8 niveles de 3 bits que puede escribir la CRAM del Game Gear. */
    private static final int[] LEVEL3 = { 0, 36, 73, 109, 146, 182, 219, 255 };

    private static int rgb(int value) {
        return 0xFF000000 | value;
    }
}
