package com.retroeditor.service.basic;

import java.util.Locale;

/**
 * Dialectos BASIC de 8 bits. La extensión {@code .bas} es compartida por varios
 * dialectos, así que el dialecto de un fichero no puede deducirse solo del nombre:
 * se resuelve con {@link BasicDialectResolver} (sobrescritura del proyecto +
 * plataforma del proyecto + generic).
 */
public enum BasicDialect {

    ATARI("Atari BASIC"),
    ZX("Sinclair BASIC (ZX)"),
    CPC("Locomotive BASIC (CPC)"),
    C64("Commodore BASIC (C64)"),
    MSX("MSX BASIC"),
    GENERIC("BASIC gen\u00e9rico");

    private final String defaultLabel;

    BasicDialect(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    /** Etiqueta por defecto (usada como fallback de i18n). */
    public String getDefaultLabel() {
        return defaultLabel;
    }

    /** True si es un dialecto concreto de una m\u00e1quina real; {@link #GENERIC} no lo es. */
    public boolean isConcrete() {
        return this != GENERIC;
    }

    /** True si Samaruc integra hoy tokenizador/compilador/emulaci\u00f3n para este dialecto. */
    public boolean hasNativeToolchain() {
        return this == ATARI || this == ZX;
    }

    /** Nombre persistible en el sidecar del proyecto. */
    public String configName() {
        return name();
    }

    public static BasicDialect fromName(String name) {
        if (name == null || name.isBlank()) return GENERIC;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GENERIC;
        }
    }
}