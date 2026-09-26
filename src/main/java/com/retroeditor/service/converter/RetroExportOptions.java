package com.retroeditor.service.converter;

/**
 * Opciones de exportacion del conversor PNG a C.
 *
 * @param arrayName nombre base del simbolo en C
 * @param includeConst anteponer {@code const} a los arrays
 * @param includeDefines emitir {@code #define} de ancho, alto y bytes
 * @param includePaletteComment incluir la tabla de paleta y los bytes de CRAM
 * @param headerComment comentario de cabecera (puede ser null)
 */
public record RetroExportOptions(String arrayName, boolean includeConst, boolean includeDefines,
                                 boolean includePaletteComment, String headerComment) {
    public static final String DEFAULT_ARRAY_NAME = "sprite_data";

    public static RetroExportOptions defaults() {
        return new RetroExportOptions(DEFAULT_ARRAY_NAME, true, true, true, "Generado por Samaruc");
    }

    public static RetroExportOptions defaults(String arrayName) {
        return new RetroExportOptions(arrayName, true, true, true, "Generado por Samaruc");
    }

    public RetroExportOptions {
        if (arrayName == null || arrayName.isBlank()) {
            arrayName = DEFAULT_ARRAY_NAME;
        }
    }

    public void validate() {
        if (arrayName.isBlank()) {
            throw new IllegalArgumentException("El nombre del array no puede estar vacio.");
        }
    }
}
