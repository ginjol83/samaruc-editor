package com.retroeditor.model;

import java.util.List;

/**
 * Fábrica de los modos de apariencia incorporados.
 *
 * <p>Los valores reproducen exactamente los colores y fuentes que hoy definen
 * {@code css/c-syntax.css} (modo oscuro) y {@code css/c-syntax-classic.css}
 * (modo clásico), de modo que la salida generada sea idéntica a la actual.</p>
 */
public final class BuiltInThemes {

    public static final String MODERN_DARK_ID = "modern_dark";
    public static final String CLASSIC_ID     = "classic";

    private static final String MONO_FALLBACK = ", \"Cascadia Mono\", Consolas, \"DejaVu Sans Mono\", monospace";

    private static final List<EditorTheme> THEMES = List.of(
        modernDark(),
        classic()
    );

    private BuiltInThemes() {
    }

    public static List<EditorTheme> all() {
        return THEMES;
    }

    public static EditorTheme get(String id) {
        if (id == null) return null;
        for (EditorTheme theme : THEMES) {
            if (id.equals(theme.getId())) {
                return theme;
            }
        }
        return null;
    }

    public static boolean isBuiltIn(String id) {
        return get(id) != null;
    }

    public static boolean isModernDark(String id) {
        return MODERN_DARK_ID.equals(id);
    }

    /**
     * Modo oscuro por defecto (equivalente a {@code css/c-syntax.css}).
     */
    public static EditorTheme modernDark() {
        EditorTheme t = base(MODERN_DARK_ID, "Modern Dark", true, "JetBrains Mono");
        t.setColor(EditorTheme.KEY_EDITOR_BACKGROUND,       "#0f172a");
        t.setColor(EditorTheme.KEY_EDITOR_TEXT,             "#e5e7eb");
        t.setColor(EditorTheme.KEY_HIGHLIGHT_FILL,          "#1e3a8a");
        t.setColor(EditorTheme.KEY_HIGHLIGHT_TEXT_FILL,     "#f8fafc");
        t.setColor(EditorTheme.KEY_CARET,                   "#f8fafc");
        t.setColor(EditorTheme.KEY_LINENO,                  "#94a3b8");
        t.setColor(EditorTheme.KEY_LINENO_BACKGROUND,       "#111827");
        t.setColor(EditorTheme.KEY_SELECTION,               "rgba(59, 130, 246, 0.35)");
        t.setColor(EditorTheme.KEY_CURRENT_LINE,            "rgba(255, 255, 255, 0.03)");

        t.setColor(EditorTheme.KEY_SYNTAX_PREPROCESSOR,     "#7dd3fc");
        t.setColor(EditorTheme.KEY_SYNTAX_KEYWORD,          "#c084fc");
        t.setColor(EditorTheme.KEY_SYNTAX_COMMENT,          "#94a3b8");
        t.setColor(EditorTheme.KEY_SYNTAX_STRING,           "#fda4af");
        t.setColor(EditorTheme.KEY_SYNTAX_CHARACTER,        "#fda4af");
        t.setColor(EditorTheme.KEY_SYNTAX_OPERATOR,         "#f59e0b");
        t.setColor(EditorTheme.KEY_SYNTAX_ZX_FUNCTION,      "#60a5fa");
        t.setColor(EditorTheme.KEY_SYNTAX_GBDK_FUNCTION,    "#38bdf8");

        t.setColor(EditorTheme.KEY_SYNTAX_ASM_LABEL,        "#f97316");
        t.setColor(EditorTheme.KEY_SYNTAX_ASM_DIRECTIVE,    "#c084fc");
        t.setColor(EditorTheme.KEY_SYNTAX_ASM_INSTRUCTION,  "#60a5fa");
        t.setColor(EditorTheme.KEY_SYNTAX_ASM_REGISTER,     "#f87171");
        t.setColor(EditorTheme.KEY_SYNTAX_ASM_NUMBER,       "#fbbf24");

        t.setColor(EditorTheme.KEY_SYNTAX_MD_HEADING,       "#93c5fd");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_BLOCKQUOTE,    "#94a3b8");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_LIST,          "#60a5fa");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CHECKLIST,     "#4ade80");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CHECKLIST_PENDING, "#fbbf24");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CHECKLIST_DONE, "#4ade80");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_TABLE,         "#c084fc");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_LINK,          "#38bdf8");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_BOLD,          "#e5e7eb");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_ITALIC,        "#e5e7eb");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CODE,          "#fda4af");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CODE_BLOCK,    "#fda4af");

        t.setColor(EditorTheme.KEY_SYNTAX_JSON_KEY,         "#93c5fd");
        t.setColor(EditorTheme.KEY_SYNTAX_JSON_NUMBER,      "#fbbf24");
        t.setColor(EditorTheme.KEY_SYNTAX_JSON_LITERAL,     "#4ade80");

        t.setColor(EditorTheme.KEY_COMPILE_ERROR_LINE,      "rgba(239, 68, 68, 0.12)");
        t.setColor(EditorTheme.KEY_COMPILE_WARNING_LINE,    "rgba(245, 158, 11, 0.12)");
        t.setColor(EditorTheme.KEY_GUTTER_ERROR,            "#f87171");
        t.setColor(EditorTheme.KEY_GUTTER_WARNING,          "#fbbf24");
        return t;
    }

    /**
     * Modo clásico (equivalente a {@code css/c-syntax-classic.css}).
     */
    public static EditorTheme classic() {
        EditorTheme t = base(CLASSIC_ID, "Classic", false, "Source Code Pro");
        t.setColor(EditorTheme.KEY_EDITOR_BACKGROUND,       "#f8fafc");
        t.setColor(EditorTheme.KEY_EDITOR_TEXT,             "#111827");
        t.setColor(EditorTheme.KEY_HIGHLIGHT_FILL,          "#bfdbfe");
        t.setColor(EditorTheme.KEY_HIGHLIGHT_TEXT_FILL,     "#0f172a");
        t.setColor(EditorTheme.KEY_CARET,                   "#111827");
        t.setColor(EditorTheme.KEY_LINENO,                  "#6b7280");
        t.setColor(EditorTheme.KEY_LINENO_BACKGROUND,       "#e5e7eb");
        t.setColor(EditorTheme.KEY_SELECTION,               "rgba(59, 130, 246, 0.25)");
        t.setColor(EditorTheme.KEY_CURRENT_LINE,            "rgba(15, 23, 42, 0.05)");

        t.setColor(EditorTheme.KEY_SYNTAX_PREPROCESSOR,     "#0369a1");
        t.setColor(EditorTheme.KEY_SYNTAX_KEYWORD,          "#7c3aed");
        t.setColor(EditorTheme.KEY_SYNTAX_COMMENT,          "#64748b");
        t.setColor(EditorTheme.KEY_SYNTAX_STRING,           "#b45309");
        t.setColor(EditorTheme.KEY_SYNTAX_CHARACTER,        "#b45309");
        t.setColor(EditorTheme.KEY_SYNTAX_OPERATOR,         "#1d4ed8");
        t.setColor(EditorTheme.KEY_SYNTAX_ZX_FUNCTION,      "#2563eb");
        t.setColor(EditorTheme.KEY_SYNTAX_GBDK_FUNCTION,    "#0284c7");

        t.setColor(EditorTheme.KEY_SYNTAX_ASM_LABEL,        "#7c2d12");
        t.setColor(EditorTheme.KEY_SYNTAX_ASM_DIRECTIVE,    "#7e22ce");
        t.setColor(EditorTheme.KEY_SYNTAX_ASM_INSTRUCTION,  "#1d4ed8");
        t.setColor(EditorTheme.KEY_SYNTAX_ASM_REGISTER,     "#c2410c");
        t.setColor(EditorTheme.KEY_SYNTAX_ASM_NUMBER,       "#0891b2");

        t.setColor(EditorTheme.KEY_SYNTAX_MD_HEADING,       "#1e40af");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_BLOCKQUOTE,    "#475569");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_LIST,          "#1f2937");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CHECKLIST,     "#475569");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CHECKLIST_PENDING, "#b45309");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CHECKLIST_DONE, "#166534");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_TABLE,         "#0f172a");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_LINK,          "#1d4ed8");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_BOLD,          "#0f172a");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_ITALIC,        "#334155");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CODE,          "#7c2d12");
        t.setColor(EditorTheme.KEY_SYNTAX_MD_CODE_BLOCK,    "#7c2d12");

        t.setColor(EditorTheme.KEY_SYNTAX_JSON_KEY,         "#1d4ed8");
        t.setColor(EditorTheme.KEY_SYNTAX_JSON_NUMBER,      "#0891b2");
        t.setColor(EditorTheme.KEY_SYNTAX_JSON_LITERAL,     "#166534");

        t.setColor(EditorTheme.KEY_COMPILE_ERROR_LINE,      "rgba(239, 68, 68, 0.12)");
        t.setColor(EditorTheme.KEY_COMPILE_WARNING_LINE,    "rgba(245, 158, 11, 0.12)");
        t.setColor(EditorTheme.KEY_GUTTER_ERROR,            "#dc2626");
        t.setColor(EditorTheme.KEY_GUTTER_WARNING,          "#d97706");
        return t;
    }

    private static EditorTheme base(String id, String name, boolean darkBase, String fontFamily) {
        EditorTheme t = new EditorTheme();
        t.setId(id);
        t.setName(name);
        t.setBuiltIn(true);
        t.setDarkBase(darkBase);
        t.setFontFamily(fontFamily + MONO_FALLBACK);
        t.setFontSize(13.0);
        t.setLineNumberSize(12.0);
        return t;
    }
}