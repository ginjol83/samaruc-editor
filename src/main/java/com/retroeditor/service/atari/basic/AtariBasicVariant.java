package com.retroeditor.service.atari.basic;

public enum AtariBasicVariant {
    ATARI_BASIC,
    XL,
    XE;

    /**
     * Resuelve la variante guardada en configuracion. Cualquier valor vacio o
     * desconocido cae en {@link #ATARI_BASIC}. Actualmente las tres variantes
     * comparten tabla de tokens; el valor se persiste para futuras revisiones.
     */
    public static AtariBasicVariant fromConfig(String value) {
        if (value == null) {
            return ATARI_BASIC;
        }
        String trimmed = value.trim();
        for (AtariBasicVariant variant : values()) {
            if (variant.name().equalsIgnoreCase(trimmed)) {
                return variant;
            }
        }
        return ATARI_BASIC;
    }
}
