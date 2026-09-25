package com.retroeditor.util;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Iconos de tipo de archivo (item 5 del plan de mejoras): traduce nombre o
 * extensión de un archivo en un {@code FontIcon} de Ikonli (pack FontAwesome 5
 * solid), con una clase CSS por categoría para colorearlos según el tema.
 */
public final class FileTypeIcons {

    public enum Kind {
        C, HEADER, ASM, MARKDOWN, TEXT, JSON, TILE, SMT, IMAGE, ROM, AUDIO,
        MAKE, DIRECTORY, GENERIC
    }

    private enum Css {
        C("file-icon-c"), HEADER("file-icon-header"), ASM("file-icon-asm"),
        MARKDOWN("file-icon-md"), TEXT("file-icon-text"), JSON("file-icon-json"),
        TILE("file-icon-tile"), SMT("file-icon-smt"), IMAGE("file-icon-image"),
        ROM("file-icon-rom"), AUDIO("file-icon-audio"), MAKE("file-icon-make"),
        DIRECTORY("file-icon-dir"), GENERIC("file-icon-generic");

        private final String name;

        Css(String name) { this.name = name; }
    }

    private FileTypeIcons() {
    }

    private static final int ICON_SIZE = 13;

    private static final Map<Kind, String> KIND_GLYPH = new TreeMap<>();
    private static final Map<String, Kind> EXT_KIND = new TreeMap<>();
    private static final Set<String> MAKEFILE_NAMES = Set.of("makefile", "gnumakefile");

    static {
        KIND_GLYPH.put(Kind.C, "fas-file-code");
        KIND_GLYPH.put(Kind.HEADER, "fas-file-code");
        KIND_GLYPH.put(Kind.ASM, "fas-microchip");
        KIND_GLYPH.put(Kind.MARKDOWN, "fas-file-alt");
        KIND_GLYPH.put(Kind.TEXT, "fas-file-alt");
        KIND_GLYPH.put(Kind.JSON, "fas-cog");
        KIND_GLYPH.put(Kind.TILE, "fas-th-large");
        KIND_GLYPH.put(Kind.SMT, "fas-language");
        KIND_GLYPH.put(Kind.IMAGE, "fas-file-image");
        KIND_GLYPH.put(Kind.ROM, "fas-gamepad");
        KIND_GLYPH.put(Kind.AUDIO, "fas-music");
        KIND_GLYPH.put(Kind.MAKE, "fas-tasks");
        KIND_GLYPH.put(Kind.DIRECTORY, "fas-folder");
        KIND_GLYPH.put(Kind.GENERIC, "fas-file");

        for (String ext : new String[]{"c"}) EXT_KIND.put(ext, Kind.C);
        for (String ext : new String[]{"h"}) EXT_KIND.put(ext, Kind.HEADER);
        for (String ext : new String[]{"asm", "s", "inc", "z80"}) EXT_KIND.put(ext, Kind.ASM);
        for (String ext : new String[]{"md", "markdown"}) EXT_KIND.put(ext, Kind.MARKDOWN);
        for (String ext : new String[]{"txt", "log", "lst"}) EXT_KIND.put(ext, Kind.TEXT);
        for (String ext : new String[]{"json", "cfg", "properties", "ini", "toml"}) EXT_KIND.put(ext, Kind.JSON);
        EXT_KIND.put("tile", Kind.TILE);
        EXT_KIND.put("smt", Kind.SMT);
        for (String ext : new String[]{"png", "jpg", "jpeg", "gif", "bmp", "svg", "ico", "tga"}) EXT_KIND.put(ext, Kind.IMAGE);
        for (String ext : new String[]{"gb", "gbc", "rom", "bin", "tap", "tzx", "sna", "crt", "dsk", "edsk", "cdt", "hex"}) EXT_KIND.put(ext, Kind.ROM);
        for (String ext : new String[]{"wav", "vgm", "mod", "s3m", "xm", "sfx", "ay"}) EXT_KIND.put(ext, Kind.AUDIO);
    }

    /** Clasifica un nombre de archivo (mayúsculas/minúsculas indiferentes). */
    public static Kind kindForFileName(String name) {
        if (name == null) return Kind.GENERIC;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (MAKEFILE_NAMES.contains(lower) || lower.endsWith(".mk") || lower.endsWith(".mak")) {
            return Kind.MAKE;
        }
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) return Kind.GENERIC;
        return EXT_KIND.getOrDefault(lower.substring(dot + 1), Kind.GENERIC);
    }

    /** Glifo Ikonli asociado a la categoría. */
    public static String glyphForKind(Kind kind) {
        return KIND_GLYPH.getOrDefault(kind != null ? kind : Kind.GENERIC, KIND_GLYPH.get(Kind.GENERIC));
    }

    /**
     * {@code FontIcon} para un nombre de archivo, sin estado del tema: el color
     * se resuelve por CSS usando la clase {@code file-icon-*} de la categoría.
     */
    public static org.kordamp.ikonli.javafx.FontIcon iconForFileName(String name) {
        Kind kind = kindForFileName(name);
        return iconForKind(kind);
    }

    /** {@code FontIcon} para directorios (carpeta cerrada). */
    public static org.kordamp.ikonli.javafx.FontIcon iconForDirectory() {
        return iconForKind(Kind.DIRECTORY);
    }

    public static org.kordamp.ikonli.javafx.FontIcon iconForFile(File file) {
        if (file == null) return iconForKind(Kind.GENERIC);
        if (file.isDirectory()) return iconForDirectory();
        return iconForFileName(file.getName());
    }

    private static org.kordamp.ikonli.javafx.FontIcon iconForKind(Kind kind) {
        org.kordamp.ikonli.javafx.FontIcon icon =
            new org.kordamp.ikonli.javafx.FontIcon(glyphForKind(kind));
        icon.setIconSize(ICON_SIZE);
        icon.getStyleClass().add("file-icon");
        icon.getStyleClass().add(cssFor(kind));
        return icon;
    }

    private static String cssFor(Kind kind) {
        try {
            return Css.valueOf(kind.name()).name;
        } catch (Exception ex) {
            return Css.GENERIC.name;
        }
    }
}