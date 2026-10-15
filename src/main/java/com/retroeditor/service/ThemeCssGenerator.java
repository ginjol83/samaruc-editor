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

    /** Estilo de un token: clave de color en el tema, color de reserva y pesos. */
    private record Token(String key, String fallback, boolean bold, boolean italic) {
    }

    private static final Map<String, Token> C_TOKENS = Map.ofEntries(
        Map.entry("preprocessor",   new Token(EditorTheme.KEY_SYNTAX_PREPROCESSOR,   "#7dd3fc", true,  false)),
        Map.entry("keyword",        new Token(EditorTheme.KEY_SYNTAX_KEYWORD,        "#c084fc", true,  false)),
        Map.entry("comment",        new Token(EditorTheme.KEY_SYNTAX_COMMENT,        "#94a3b8", false, true)),
        Map.entry("string",         new Token(EditorTheme.KEY_SYNTAX_STRING,         "#fda4af", false, false)),
        Map.entry("character",      new Token(EditorTheme.KEY_SYNTAX_CHARACTER,      "#fda4af", false, false)),
        Map.entry("operator",       new Token(EditorTheme.KEY_SYNTAX_OPERATOR,       "#f59e0b", false, false)),
        Map.entry("zx-function",    new Token(EditorTheme.KEY_SYNTAX_ZX_FUNCTION,    "#60a5fa", true,  false)),
        Map.entry("gbdk-function",  new Token(EditorTheme.KEY_SYNTAX_GBDK_FUNCTION,  "#38bdf8", true,  false))
    );

    private static final Map<String, Token> ASM_TOKENS = Map.ofEntries(
        Map.entry("asm-label",       new Token(EditorTheme.KEY_SYNTAX_ASM_LABEL,       "#f97316", true,  false)),
        Map.entry("asm-directive",   new Token(EditorTheme.KEY_SYNTAX_ASM_DIRECTIVE,   "#c084fc", true,  false)),
        Map.entry("asm-instruction", new Token(EditorTheme.KEY_SYNTAX_ASM_INSTRUCTION, "#60a5fa", true,  false)),
        Map.entry("asm-register",    new Token(EditorTheme.KEY_SYNTAX_ASM_REGISTER,    "#f87171", true,  false)),
        Map.entry("asm-number",      new Token(EditorTheme.KEY_SYNTAX_ASM_NUMBER,      "#fbbf24", false, false))
    );

    private static final Map<String, Token> MD_TOKENS = Map.ofEntries(
        Map.entry("md-heading",          new Token(EditorTheme.KEY_SYNTAX_MD_HEADING,          "#93c5fd", true,  false)),
        Map.entry("md-blockquote",       new Token(EditorTheme.KEY_SYNTAX_MD_BLOCKQUOTE,       "#94a3b8", false, true)),
        Map.entry("md-list",             new Token(EditorTheme.KEY_SYNTAX_MD_LIST,             "#60a5fa", false, false)),
        Map.entry("md-checklist",        new Token(EditorTheme.KEY_SYNTAX_MD_CHECKLIST,        "#94a3b8", false, false)),
        Map.entry("md-checklist-pending",new Token(EditorTheme.KEY_SYNTAX_MD_CHECKLIST_PENDING,"#fbbf24", false, false)),
        Map.entry("md-checklist-done",   new Token(EditorTheme.KEY_SYNTAX_MD_CHECKLIST_DONE,   "#4ade80", true,  false)),
        Map.entry("md-table",            new Token(EditorTheme.KEY_SYNTAX_MD_TABLE,            "#c084fc", false, false)),
        Map.entry("md-link",             new Token(EditorTheme.KEY_SYNTAX_MD_LINK,             "#38bdf8", false, false)),
        Map.entry("md-bold",             new Token(EditorTheme.KEY_SYNTAX_MD_BOLD,             "#e2e8f0", true,  false)),
        Map.entry("md-italic",           new Token(EditorTheme.KEY_SYNTAX_MD_ITALIC,           "#e2e8f0", false, true))
    );

    private static final Map<String, Token> JSON_TOKENS = Map.ofEntries(
        Map.entry("json-key",     new Token(EditorTheme.KEY_SYNTAX_JSON_KEY,     "#93c5fd", true,  false)),
        Map.entry("json-number",  new Token(EditorTheme.KEY_SYNTAX_JSON_NUMBER,  "#fbbf24", false, false)),
        Map.entry("json-literal", new Token(EditorTheme.KEY_SYNTAX_JSON_LITERAL, "#4ade80", true,  false))
    );

    private static final Map<String, Token> BASIC_TOKENS = Map.ofEntries(
        Map.entry("basic-line-number", new Token(EditorTheme.KEY_SYNTAX_BASIC_LINE_NUMBER, "#64748b", false, false)),
        Map.entry("basic-keyword",     new Token(EditorTheme.KEY_SYNTAX_BASIC_KEYWORD,     "#c084fc", true,  false)),
        Map.entry("basic-function",    new Token(EditorTheme.KEY_SYNTAX_BASIC_FUNCTION,    "#38bdf8", true,  false)),
        Map.entry("basic-string",      new Token(EditorTheme.KEY_SYNTAX_BASIC_STRING,      "#fda4af", false, false)),
        Map.entry("basic-number",      new Token(EditorTheme.KEY_SYNTAX_BASIC_NUMBER,      "#fbbf24", false, false)),
        Map.entry("basic-comment",     new Token(EditorTheme.KEY_SYNTAX_BASIC_COMMENT,     "#94a3b8", false, true)),
        Map.entry("basic-operator",    new Token(EditorTheme.KEY_SYNTAX_BASIC_OPERATOR,    "#f59e0b", false, false)),
        Map.entry("basic-variable",    new Token(EditorTheme.KEY_SYNTAX_BASIC_VARIABLE,    "#e5e7eb", false, false))
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
        for (Map.Entry<String, Token> e : C_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), e.getValue());
        }

        // ---------- Tokens ASM ----------
        for (Map.Entry<String, Token> e : ASM_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), e.getValue());
        }

        // ---------- Tokens Markdown ----------
        for (Map.Entry<String, Token> e : MD_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), e.getValue());
        }

        // ---------- Tokens JSON ----------
        for (Map.Entry<String, Token> e : JSON_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), e.getValue());
        }

        // ---------- Tokens Atari BASIC ----------
        for (Map.Entry<String, Token> e : BASIC_TOKENS.entrySet()) {
            token(sb, active, base, e.getKey(), e.getValue());
        }

        return sb.toString();
    }

    private static void token(StringBuilder sb, EditorTheme active, EditorTheme base,
                              String cls, Token token) {
        sb.append(".code-area .").append(cls).append(" {\n");
        sb.append("    -fx-fill: ").append(c(active, base, token.key(), token.fallback())).append(";\n");
        if (token.bold()) {
            sb.append("    -fx-font-weight: bold;\n");
        }
        if (token.italic()) {
            sb.append("    -fx-font-style: italic;\n");
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
