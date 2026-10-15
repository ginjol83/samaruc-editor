package com.retroeditor.service.zxspectrum.basic;

import java.util.List;

/**
 * Tabla de tokens de Sinclair BASIC (ZX Spectrum 48K/128K). Los codigos son los
 * del juego de caracteres del Spectrum: los tokens ocupan el rango
 * {@code 0xA5}-{@code 0xFF}; los caracteres por debajo ({@code 0x20}-{@code 0xA4})
 * son ASCII/UDG y se almacenan literalmente.
 *
 * <p>El orden de {@link #orderedKeywords()} es deliberadamente el inverso al
 * valor del token (de {@code 0xFF} hacia abajo). Ese es el orden en que se debe
 * intentar el reconocimiento para que construcciones con espacio o prefijo no se
 * descompongan por tokens mas cortos contenidos en otras (por ejemplo
 * {@code GO TO} antes que {@code TO}, {@code DEF FN} antes que {@code FN},
 * {@code VAL$} antes que {@code VAL}). Varios tokens comparten valor con su forma
 * alternativa (p. ej. {@code GO TO}/{@code GOTO}, {@code RANDOMIZE}/{@code RANDOMISE}).</p>
 */
public final class SinclairBasicTokens {

    /** Token de {@code REM} (0xEA). Todo lo que le sigue en la linea es literal. */
    public static final int REM = 0xEA;
    /** Token de {@code BIN} (0xC4); el numero que le sigue es binario, no decimal. */
    public static final int BIN = 0xC4;
    /** Token de {@code DEF FN} (0xCE). */
    public static final int DEF_FN = 0xCE;

    /** Un texto reconocible y el byte de token con el que se sustituye. */
    public record Keyword(String text, int value) {
    }

    private static final List<Keyword> ORDERED = List.of(
            new Keyword("copy", 0xFF),
            new Keyword("return", 0xFE),
            new Keyword("clear", 0xFD),
            new Keyword("draw", 0xFC),
            new Keyword("cls", 0xFB),
            new Keyword("if", 0xFA),
            new Keyword("randomize", 0xF9),
            new Keyword("randomise", 0xF9),
            new Keyword("save", 0xF8),
            new Keyword("run", 0xF7),
            new Keyword("plot", 0xF6),
            new Keyword("print", 0xF5),
            new Keyword("poke", 0xF4),
            new Keyword("next", 0xF3),
            new Keyword("pause", 0xF2),
            new Keyword("let", 0xF1),
            new Keyword("list", 0xF0),
            new Keyword("load", 0xEF),
            new Keyword("input", 0xEE),
            new Keyword("go sub", 0xED),
            new Keyword("gosub", 0xED),
            new Keyword("go to", 0xEC),
            new Keyword("goto", 0xEC),
            new Keyword("for", 0xEB),
            new Keyword("rem", REM),
            new Keyword("dim", 0xE9),
            new Keyword("continue", 0xE8),
            new Keyword("border", 0xE7),
            new Keyword("new", 0xE6),
            new Keyword("restore", 0xE5),
            new Keyword("data", 0xE4),
            new Keyword("read", 0xE3),
            new Keyword("stop", 0xE2),
            new Keyword("llist", 0xE1),
            new Keyword("lprint", 0xE0),
            new Keyword("out", 0xDF),
            new Keyword("over", 0xDE),
            new Keyword("inverse", 0xDD),
            new Keyword("bright", 0xDC),
            new Keyword("flash", 0xDB),
            new Keyword("paper", 0xDA),
            new Keyword("ink", 0xD9),
            new Keyword("circle", 0xD8),
            new Keyword("beep", 0xD7),
            new Keyword("verify", 0xD6),
            new Keyword("merge", 0xD5),
            new Keyword("close #", 0xD4),
            new Keyword("close#", 0xD4),
            new Keyword("open #", 0xD3),
            new Keyword("open#", 0xD3),
            new Keyword("erase", 0xD2),
            new Keyword("move", 0xD1),
            new Keyword("format", 0xD0),
            new Keyword("cat", 0xCF),
            new Keyword("def fn", DEF_FN),
            new Keyword("deffn", DEF_FN),
            new Keyword("step", 0xCD),
            new Keyword("to", 0xCC),
            new Keyword("then", 0xCB),
            new Keyword("line", 0xCA),
            new Keyword("<>", 0xC9),
            new Keyword(">=", 0xC8),
            new Keyword("<=", 0xC7),
            new Keyword("and", 0xC6),
            new Keyword("or", 0xC5),
            new Keyword("bin", BIN),
            new Keyword("not", 0xC3),
            new Keyword("chr$", 0xC2),
            new Keyword("str$", 0xC1),
            new Keyword("usr", 0xC0),
            new Keyword("in", 0xBF),
            new Keyword("peek", 0xBE),
            new Keyword("abs", 0xBD),
            new Keyword("sgn", 0xBC),
            new Keyword("sqr", 0xBB),
            new Keyword("int", 0xBA),
            new Keyword("exp", 0xB9),
            new Keyword("ln", 0xB8),
            new Keyword("atn", 0xB7),
            new Keyword("acs", 0xB6),
            new Keyword("asn", 0xB5),
            new Keyword("tan", 0xB4),
            new Keyword("cos", 0xB3),
            new Keyword("sin", 0xB2),
            new Keyword("len", 0xB1),
            new Keyword("val$", 0xAE),
            new Keyword("code", 0xAF),
            new Keyword("val", 0xB0),
            new Keyword("tab", 0xAD),
            new Keyword("at", 0xAC),
            new Keyword("attr", 0xAB),
            new Keyword("screen$", 0xAA),
            new Keyword("point", 0xA9),
            new Keyword("fn", 0xA8),
            new Keyword("pi", 0xA7),
            new Keyword("inkey$", 0xA6),
            new Keyword("rnd", 0xA5),
            new Keyword("play", 0xA4),
            new Keyword("spectrum", 0xA3));

    private SinclairBasicTokens() {
    }

    /** Tokens en el orden en que deben reconocerse (del valor mas alto al mas bajo). */
    public static List<Keyword> orderedKeywords() {
        return ORDERED;
    }
}
