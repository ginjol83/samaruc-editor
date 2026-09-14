package com.retroeditor.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.retroeditor.model.BuiltInThemes;
import com.retroeditor.model.EditorTheme;

/**
 * Genera la hoja de estilos del editor a partir de un {@link EditorTheme}.
 *
 * <p>La salida reproduce estructuralmente {@code /css/c-syntax.css} (y su
 * variante clara {@code /css/c-syntax-classic.css}), interpolando cada color
 * desde el modo activo. Cuando el modo personalizado no define una clave se
 * usa el valor del modo incorporado con la misma base (oscura o clara), de
 * modo que nunca se emite un selector sin color.</p>
 */
public final class ThemeCssGenerator {

    private ThemeCssGenerator() {
    }

    /** Selectores base del editor (regiones, selección, línea actual, caret). */
    private static final Map<String, Object[]> BASE = Map.ofEntries(
        Map.entry("editor_background",  new Object[]{EditorTheme.KEY_EDITOR_BACKGROUND, "#0f172a"}),
        Map.entry("editor_text",        new Object[]{EditorTheme.KEY_EDITOR_TEXT,       "#e5e7eb"}),
        Map.entry("lineno",             new Object[]{EditorTheme.KEY_LINENO,             "#94a3b8"}),
        Map.entry("lineno_background",  new Object[]{EditorTheme.KEY_LINENO_BACKGROUND,  "#111827"}),
        Map.entry("selection",          new Object[]{EditorTheme.KEY_SELECTION,          "rgba(59, 130, 246, 0.35)"}),
        Map.entry("current_line",       new Object[]{EditorTheme.KEY_CURRENT_LINE,       "rgba(255, 255, 255, 0.03)"}),
        Map.entry("caret",              new Object[]{EditorTheme.KEY_CARET,               "#f8fafc"})
    );

    private static final Map<String, Object[]> C_TOKENS = Map.ofEntries(
        Map.entry("preprocessor",   new Object[]{EditorTheme.KEY_SYNTAX_PREPROCESSOR,   "#7dd3fc", true}),
        Map.entry("keyword",        new Object[]{EditorTheme.KEY_SYNTAX_KEYWORD,        "#c084fc", true}),
        Map.entry("comment",        new Object[]{EditorTheme.KEY_SYNTAX_COMMENT,        "#94a3b8", false}),
        Map.entry("string",         new Object[]{EditorTheme.KEY_SYNTAX_STRING,         "#fda4af", false}),
        Map.entry("character",      new Object[]{EditorTheme.KEY_SYNTAX_CHARACTER,      "#fda4af", false}),
        Map.entry("operator",       new Object[]{EditorTheme.KEY_SYNTAX_OPERATOR,       "#f59e0b", false}),
        Map.entry("zx-function",    new Object[]{EditorTheme.KEY_SYNTAX_ZX_FUNCTION,    "#60a5fa", true}),
        Map.entry("gbdk-function",  new Object[]{EditorTheme.KEY_SYNTAX_GBDK_FUNCTION,  "#38bdf8", true})
    );

    private static final Map<String, Object[]> ASM_TOKENS = Map.ofEntries(
        Map.entry("asm-label",       new Object[]{EditorTheme.KEY_SYNTAX_ASM_LABEL,       "#f97316", true}),
        Map.entry("asm-directive",   new Object[]{EditorTheme.KEY_SYNTAX_ASM_DIRECTIVE,   "#c084fc", true}),
        Map.entry("asm-instruction", new Object[]{EditorTheme.KEY_SYNTAX_ASM_INSTRUCTION, "#60a5fa", true}),
        Map.entry("asm-register",    new Object[]{EditorTheme.KEY_SYNTAX_ASM_REGISTER,    "#f87171", true}),
        Map.entry("asm-number",      new Object[]{EditorTheme.KEY_SYNTAX_ASM_NUMBER,      "#fbbf24", false})
    );

    private static final Map<String, Object[]> MD_TOKENS = Map.ofEntries(
        Map.entry("md-heading",          new Object[]{EditorTheme.KEY_SYNTAX_MD_HEADING,          "#93c5fd", true}),
        Map.entry("md-blockquote",       new Object[]{EditorTheme.KEY_SYNTAX_MD_BLOCKQUOTE,       "#94a3b8", false}),
        Map.entry("md-list",             new Object[]{EditorTheme.KEY_SYNTAX_MD_LIST,             "#60a5fa", false}),
        Map.entry("md-checklist",        new Object[]{EditorTheme.KEY_SYNTAX_MD_CHECKLIST,        "#94a3b8", false}),
        Map.entry("md-checklist-pending",new Object[]{EditorTheme.KEY_SYNTAX_MD_CHECKLIST_PENDING,"#fbbf24", false}),
        Map.entry("md-checklist-done",   new Object[]{EditorTheme.KEY_SYNTAX_MD_CHECKLIST_DONE,   "#4ade80", true}),
        Map.entry("md-table",            new Object[]{EditorTheme.KEY_SYNTAX_MD_TABLE,            "#c084fc", false}),
        Map.entry("md-link",             new Object[]{EditorTheme.KEY_SYNTAX_MD_LINK,             "#38bdf8", false}),
        Map.entry("md-bold",             new Object[]{EditorTheme.KEY_SYNTAX_MD_BOLD,             "#e2e8f0", true}),
        Map.entry("md-italic",           new Object[]{EditorTheme.KEY_SYNTAX_MD_ITALIC,           "#e2e8f0", false})
    );

    private static final Map<String, Object[]> JSON_TOKENS = Map.ofEntries(
        Map.entry("json-key",     new Object[]{EditorTheme.KEY_SYNTAX_JSON_KEY,     "#93c5fd", true}),
        Map.entry("json-number",  new Object[]{EditorTheme.KEY_SYNTAX_JSON_NUMBER,  "#fbbf24", false}),
        Map.entry("json-literal", new Object[]{EditorTheme.KEY_SYNTAX_JSON_LITERAL, "#4ade80", true})
    );

    /**
     * Genera el CSS del editor para el modo activo dado.
     *
     * @param theme modo activo; {@code null} usa el modo oscuro incorporado
     * @return texto CSS completo, nunca {@code null}
     */
    public static String generateCss(EditorTheme theme) {
        EditorTheme active = theme != null ? theme : BuiltInThemes.modernDark();
        EditorTheme base   = BuiltInThemes.modernDark().isDarkBase() != active.isDarkBase()
            ? BuiltInThemes.classic()
            : BuiltInThemes.modernDark();

        StringBuilder sb = new StringBuilder(2048);

        sb.append("/* ThemeCssGenerator: modo \"").append(id(active)).append("\" */\n\n");
        sb.append(".code-area {\n");
        sb.append("    -fx-background-color: ").append(c(active, base, EditorTheme.KEY_EDITOR_BACKGROUND, "#0f172a")).append(";\n");
        sb.append("    -fx-control-inner-background: ").append(c(active, base, EditorTheme.KEY_EDITOR_BACKGROUND, "#0f172a")).append(";\n");
        sb.append("    -fx-highlight-fill: ").append(c(active, base, EditorTheme.KEY_HIGHLIGHT_FILL, "#1e3a8a")).append(";\n");
        sb.append("    -fx-highlight-text-fill: ").append(c(active, base, EditorTheme.KEY_HIGHLIGHT_TEXT_FILL, "#f8fafc")).append(";\n");
        sb.append("    -fx-caret-color: ").append(c(active, base, EditorTheme.KEY_CARET, "#f8fafc")).append(";\n");
        sb.append("    -fx-font-family: \"").append(escapeFontFamily(font(active, base))).append("\";\n");
        sb.append("    -fx-font-size: ").append(px(fontSize(active, base))).append(";\n");
        sb.append("}\n\n");

        sb.append(".code-area .content {\n");
        sb.append("    -fx-background-color: ").append(c(active, base, EditorTheme.KEY_EDITOR_BACKGROUND, "#0f172a")).append(";\n");
        sb.append("}\n\n");

        sb.append(".code-area .text,\n");
        sb.append(".code-area .paragraph-box,\n");
        sb.append(".code-area .paragraph-text {\n");
        sb.append("    -fx-background-color: ").append(c(active, base, EditorTheme.KEY_EDITOR_BACKGROUND, "#0f172a")).append(";\n");
        sb.append("    -fx-fill: ").append(c(active, base, EditorTheme.KEY_EDITOR_TEXT, "#e5e7eb")).append(";\n");
        sb.append("}\n\n");

        sb.append(".code-area .lineno,\n");
        sb.append(".code-area .paragraph-box .lineno,\n");
        sb.append(".code-area .paragraph-text .lineno {\n");
        sb.append("    -fx-text-fill: ").append(c(active, base, EditorTheme.KEY_LINENO, "#94a3b8")).append(";\n");
        sb.append("    -fx-background-color: ").append(c(active, base, EditorTheme.KEY_LINENO_BACKGROUND, "#111827")).append(";\n");
        sb.append("    -fx-padding: 0 10 0 10;\n");
        sb.append("    -fx-font-family: \"Source Code Pro\", Consolas, \"DejaVu Sans Mono\", monospace;\n");
        sb.append("    -fx-font-size: ").append(px(lineNumberSize(active, base))).append(";\n");
        sb.append("}\n\n");

        sb.append(".code-area .selection {\n");
        sb.append("    -fx-fill: ").append(c(active, base, EditorTheme.KEY_SELECTION, "rgba(59, 130, 246, 0.35)")).append(";\n");
        sb.append("}\n\n");

        sb.append(".code-area .current-line {\n");
        sb.append("    -fx-background-color: ").append(c(active, base, EditorTheme.KEY_CURRENT_LINE, "rgba(255, 255, 255, 0.03)")).append(";\n");
        sb.append("}\n\n");

        sb.append(".code-area .caret {\n");
        sb.append("    -fx-stroke: ").append(c(active, base, EditorTheme.KEY_CARET, "#f8fafc")).append(";\n");
        sb.append("}\n\n");

        // ---------- Tokens C ----------
        for (Map.Entry<String, Object[]> e : C_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), (String) e.getValue()[0], (String) e.getValue()[1], (Boolean) e.getValue()[2]);
        }

        // ---------- Tokens ASM ----------
        for (Map.Entry<String, Object[]> e : ASM_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), (String) e.getValue()[0], (String) e.getValue()[1], (Boolean) e.getValue()[2]);
        }

        // ---------- Tokens Markdown ----------
        for (Map.Entry<String, Object[]> e : MD_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), (String) e.getValue()[0], (String) e.getValue()[1], (Boolean) e.getValue()[2]);
        }

        // ---------- Tokens JSON ----------
        for (Map.Entry<String, Object[]> e : JSON_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), (String) e.getValue()[0], (String) e.getValue()[1], (Boolean) e.getValue()[2]);
        }

        return sb.toString();
    }

    private static void token(StringBuilder sb, EditorTheme active, EditorTheme base,
                              String cls, String key, String fallback, boolean bold) {
        sb.append(".code-area .").append(cls).append(" {\n");
        sb.append("    -fx-fill: ").append(c(active, base, key, fallback)).append(";\n");
        if (bold) {
            sb.append("    -fx-font-weight: bold;\n");
        }
        sb.append("}\n\n");
    }

    private static String id(EditorTheme t) {
        String v = t != null ? t.getId() : null;
        return v != null && !v.isBlank() ? v : "modern_dark";
    }

    /**
     * Devuelve el color de una clave del modo activo, o el del modo base si el
     * primero no lo define, o el {@code fallback} si ninguno lo tiene.
     */
    private static String c(EditorTheme active, EditorTheme base, String key, String fallback) {
        String v = active != null ? active.getColor(key) : null;
        if (v == null || v.isBlank()) v = base != null ? base.getColor(key) : null;
        if (v == null || v.isBlank()) v = fallback;
        return v;
    }

    private static String font(EditorTheme active, EditorTheme base) {
        String v = active != null ? active.getFontFamily() : null;
        if (v == null || v.isBlank()) v = base != null ? base.getFontFamily() : null;
        return v != null && !v.isBlank() ? v : "JetBrains Mono";
    }

    private static double fontSize(EditorTheme active, EditorTheme base) {
        double v = active != null ? active.getFontSize() : 0;
        if (v <= 0 && base != null) v = base.getFontSize();
        return v > 0 ? v : 13.0;
    }

private static double lineNumberSize(EditorTheme active, EditorTheme base) {
        double v = active != null && active.getLineNumberSize() > 0 ? active.getLineNumberSize() : 0;
        if (v <= 0 && base != null) v = base.getLineNumberSize();
        if (v <= 0) v = fontSize(active, base) - 1;
        return v;
    }

    private static String escapeFontFamily(String value) {
        if (value == null) return "monospace";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String px(double value) {
        return String.format(Locale.ROOT, "%.1fpx", value);
    }

    /**
     * Hash SHA-256 (12 primeros hex) de un contenido CSS; se usa como nombre de
     * archivo temporal para cachear la hoja generada.
     */
    public static String contentHash(String css) {
        if (css == null || css.isBlank()) return "empty";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(css.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * d.length);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 12);
        } catch (Exception ex) {
            return Integer.toHexString(css.hashCode());
        }
    }
}
