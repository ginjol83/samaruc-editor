package com.retroeditor.service.converter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Cuantizador de color para el conversor PNG a C.
 *
 * Reduce cada pixel a un indice de la paleta del modo. Los modos con menos
 * colores de los que tiene la paleta (CPC modes 0 y 1, Atari, Master System o
 * Game Gear) se quedan con los indices mas usados de la imagen; el C64, que
 * elige sus colores por celda, mapea contra los 16 colores del VIC-II y es el
 * empaquetador el que decide los colores de cada caracter o celda.
 */
public final class RetroQuantizer {
    private static final int ALPHA_TRANSPARENT = 128;
    private static final double[] WEIGHTS = { 0.299, 0.587, 0.114 };

    private RetroQuantizer() {
    }

    /**
     * Indice de la paleta mas cercano a un color ARGB.
     *
     * @param argb color del pixel
     * @param palette paleta de destino
     * @return indice en {@code [0, palette.length)}
     */
    public static int nearestIndex(int argb, int[] palette) {
        Objects.requireNonNull(palette, "palette");
        if (palette.length == 0) {
            throw new IllegalArgumentException("La paleta no puede estar vacia.");
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int candidate = palette[i];
            double dr = r - ((candidate >> 16) & 0xFF);
            double dg = g - ((candidate >> 8) & 0xFF);
            double db = b - (candidate & 0xFF);
            double distance = WEIGHTS[0] * dr * dr + WEIGHTS[1] * dg * dg + WEIGHTS[2] * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * Indice mas cercano a un color ARGB saltandose el primero.
     *
     * La paleta del Master System y el Game Gear repite el negro en los indices
     * 0 y 1, porque el color 0 es el transparente de los sprites. Si la imagen
     * tiene pixeles transparentes, un negro opaco tiene que acabar en el indice
     * 1: si se quedara en el 0, en la pantalla seria transparente.
     */
    public static int nearestIndexExceptFirst(int argb, int[] palette) {
        Objects.requireNonNull(palette, "palette");
        if (palette.length == 0) {
            throw new IllegalArgumentException("La paleta no puede estar vacia.");
        }
        if (palette.length == 1) {
            return 0;
        }
        return nearestIndex(argb, Arrays.copyOfRange(palette, 1, palette.length)) + 1;
    }

    private static boolean containsTransparentPixel(int[] argbPixels) {
        for (int argb : argbPixels) {
            if (((argb >>> 24) & 0xFF) < ALPHA_TRANSPARENT) {
                return true;
            }
        }
        return false;
    }

    /** Cuantiza una imagen (fila a fila) contra la paleta indicada. */
    public static int[] quantize(int[] argbPixels, int[] palette) {
        Objects.requireNonNull(argbPixels, "argbPixels");
        int[] result = new int[argbPixels.length];
        for (int i = 0; i < argbPixels.length; i++) {
            result[i] = nearestIndex(argbPixels[i], palette);
        }
        return result;
    }

    /**
     * Cuantiza respetando la transparencia: en los modos con color 0
     * transparente (tiles de SMS/GG) los pixeles con alfa menor de 128 se
     * fuerzan al indice 0; en el resto de modos el alfa se ignora.
     */
    public static int[] quantize(int[] argbPixels, int[] palette, boolean transparentFirstColor) {
        Objects.requireNonNull(argbPixels, "argbPixels");
        boolean hasTransparentPixels = transparentFirstColor && containsTransparentPixel(argbPixels);
        int[] result = new int[argbPixels.length];
        for (int i = 0; i < argbPixels.length; i++) {
            int argb = argbPixels[i];
            if (transparentFirstColor && ((argb >>> 24) & 0xFF) < ALPHA_TRANSPARENT) {
                result[i] = 0;
            } else {
                result[i] = hasTransparentPixels ? nearestIndexExceptFirst(argb, palette) : nearestIndex(argb, palette);
            }
        }
        return result;
    }

    /**
     * Devuelve los {@code count} indices de paleta mas usados, ordenados por
     * frecuencia (y por indice en caso de empate). Sirve para reducir la
     * paleta de 16 colores del C64 a los 2 o 4 colores que puede mostrar.
     *
     * @param quantizedIndices indices ya cuantizados a la paleta completa
     * @param paletteSize numero de colores de la paleta completa
     * @param count numero de colores a conservar
     * @return indices seleccionados, sin repetir y en orden creciente
     */
    public static int[] mostUsedIndices(int[] quantizedIndices, int paletteSize, int count) {
        Objects.requireNonNull(quantizedIndices, "quantizedIndices");
        if (count <= 0) {
            throw new IllegalArgumentException("El numero de colores debe ser mayor que 0.");
        }
        int[] order = orderByFrequency(countFrequencies(quantizedIndices, paletteSize), paletteSize);
        int[] selected = new int[Math.min(count, paletteSize)];
        System.arraycopy(order, 0, selected, 0, selected.length);
        Arrays.sort(selected);
        return selected;
    }

    /**
     * Devuelve los indices realmente usados ordenados de mas a menos frecuente
     * (en caso de empate, de menor a mayor). Es lo que necesitan los modos del
     * C64, donde la posicion en la lista es el valor del pixel: el primero es el
     * color de fondo y los siguientes son los colores de primer plano.
     *
     * @param quantizedIndices indices ya cuantizados a la paleta completa
     * @param paletteSize numero de colores de la paleta completa
     * @param count maximo de colores a devolver
     * @return indices usados, sin repetir y ordenados por frecuencia; puede
     *         tener menos de {@code count} elementos si la imagen usa menos
     *         colores
     */
    public static int[] mostUsedOrdered(int[] quantizedIndices, int paletteSize, int count) {
        return mostUsedFromFrequencies(countFrequencies(
                Objects.requireNonNull(quantizedIndices, "quantizedIndices"), paletteSize), paletteSize, count);
    }

    /**
     * Igual que {@link #mostUsedOrdered(int[], int, int)} pero descartando un
     * color concreto. Lo necesita el bitmap multicolor del C64: el valor 00 de
     * los pixeles usa un unico color de fondo global ($D021) para toda la
     * imagen, asi que ese color no puede competir por las tres posiciones de
     * nibble alto, nibble bajo y color RAM de cada celda.
     *
     * @param quantizedIndices indices ya cuantizados a la paleta completa
     * @param paletteSize numero de colores de la paleta completa
     * @param count maximo de colores a devolver
     * @param excluded indice a ignorar, o un valor fuera de rango para no
     *        excluir ninguno
     * @return indices usados, sin repetir y ordenados por frecuencia
     */
    public static int[] mostUsedOrderedExcluding(int[] quantizedIndices, int paletteSize, int count, int excluded) {
        int[] frequency = countFrequencies(
                Objects.requireNonNull(quantizedIndices, "quantizedIndices"), paletteSize);
        if (excluded >= 0 && excluded < paletteSize) {
            frequency[excluded] = 0;
        }
        return mostUsedFromFrequencies(frequency, paletteSize, count);
    }

    private static int[] mostUsedFromFrequencies(int[] frequency, int paletteSize, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("El numero de colores debe ser mayor que 0.");
        }
        int[] order = orderByFrequency(frequency, paletteSize);
        int[] result = new int[Math.min(count, paletteSize)];
        int size = 0;
        for (int index : order) {
            if (size == result.length || frequency[index] == 0) {
                break;
            }
            result[size++] = index;
        }
        return Arrays.copyOf(result, size);
    }

    private static int[] countFrequencies(int[] quantizedIndices, int paletteSize) {
        int[] frequency = new int[paletteSize];
        for (int index : quantizedIndices) {
            if (index >= 0 && index < paletteSize) {
                frequency[index]++;
            }
        }
        return frequency;
    }

    private static int[] orderByFrequency(int[] frequency, int paletteSize) {
        Integer[] order = new Integer[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int byFrequency = Integer.compare(frequency[b], frequency[a]);
            return byFrequency != 0 ? byFrequency : Integer.compare(a, b);
        });
        int[] result = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            result[i] = order[i];
        }
        return result;
    }

    /**
     * Renumera los indices para que suministren valores desde 0 hasta
     * {@code selected.length - 1}.
     *
     * @return array con la misma longitud que la entrada
     */
    public static int[] remap(int[] quantizedIndices, int[] selected) {
        Objects.requireNonNull(quantizedIndices, "quantizedIndices");
        Objects.requireNonNull(selected, "selected");
        int[] lookup = new int[Math.max(selected.length == 0 ? 0 : selected[selected.length - 1] + 1, 1)];
        Arrays.fill(lookup, -1);
        for (int i = 0; i < selected.length; i++) {
            if (selected[i] < lookup.length) {
                lookup[selected[i]] = i;
            }
        }
        int[] result = new int[quantizedIndices.length];
        for (int i = 0; i < quantizedIndices.length; i++) {
            int mapped = quantizedIndices[i] < lookup.length ? lookup[quantizedIndices[i]] : -1;
            result[i] = mapped < 0 ? 0 : mapped;
        }
        return result;
    }

    /** Extrae los colores de la paleta selected, en el mismo orden. */
    public static int[] selectColors(int[] palette, int[] selected) {
        int[] result = new int[selected.length];
        for (int i = 0; i < selected.length; i++) {
            result[i] = palette[selected[i]];
        }
        return result;
    }
}
