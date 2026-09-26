package com.retroeditor.service.converter;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de convertir una imagen al formato de una maquina.
 *
 * @param mode modo de video usado
 * @param width ancho en pixeles
 * @param height alto en pixeles
 * @param layers capas de datos generadas
 * @param palette colores ARGB usados por los indices (puede estar reducida en
 *        los modos con menos colores que la paleta de hardware)
 * @param hardwareIndices posicion de cada color de {@code palette} dentro de la
 *        paleta de hardware del modo; el C64 lo necesita porque su color RAM
 *        guarda el numero de color del VIC-II, no el color RGB
 * @param cram bytes de CRAM a escribir (solo SMS/Game Gear; null en el resto)
 * @param indices indices de color por pixel, en orden fila a fila
 */
public record RetroImageConversion(RetroImageMode mode, int width, int height,
                                   List<RetroImageLayer> layers, int[] palette,
                                   int[] hardwareIndices, int[] cram, int[] indices) {
    public RetroImageConversion {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(indices, "indices");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Las dimensiones deben ser mayores que 0.");
        }
        if (indices.length != width * height) {
            throw new IllegalArgumentException("El numero de indices no coincide con las dimensiones.");
        }
        hardwareIndices = hardwareIndices == null ? identity(palette.length) : hardwareIndices.clone();
        if (hardwareIndices.length != palette.length) {
            throw new IllegalArgumentException("Cada color de la paleta necesita su indice de hardware.");
        }
        layers = List.copyOf(layers);
        palette = palette.clone();
        indices = indices.clone();
        cram = cram == null ? null : cram.clone();
    }

    private static int[] identity(int length) {
        int[] indices = new int[length];
        for (int i = 0; i < length; i++) {
            indices[i] = i;
        }
        return indices;
    }

    public int totalBytes() {
        return layers.stream().mapToInt(layer -> layer.data().length).sum();
    }

    public int[] getIndices() {
        return indices.clone();
    }

    public int[] getPalette() {
        return palette.clone();
    }

    /** Numero de color de hardware de la entrada {@code position} de la paleta. */
    public int hardwareIndex(int position) {
        return hardwareIndices[position] & 0x0F;
    }

    /** Indice de color del pixel (x, y). */
    public int indexAt(int x, int y) {
        return indices[y * width + x];
    }
}
