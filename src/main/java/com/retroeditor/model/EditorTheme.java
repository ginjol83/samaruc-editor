package com.retroeditor.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Modo de apariencia (tema) del editor.
 *
 * <p>Agrupa los colores del editor, la fuente y el tamaño de letra. Una
 * instancia puede representar un modo incorporado ({@code builtIn=true},
 * definido en {@link BuiltInThemes} y no editable) o un modo personalizado
 * persistido por {@code ThemeRepository}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EditorTheme {

    // ---- Colores base del editor ----
    public static final String KEY_EDITOR_BACKGROUND   = "editor_background";
    public static final String KEY_EDITOR_TEXT         = "editor_text";
    public static final String KEY_HIGHLIGHT_FILL      = "highlight_fill";
    public static final String KEY_HIGHLIGHT_TEXT_FILL = "highlight_text_fill";
    public static final String KEY_CARET               = "caret";
    public static final String KEY_LINENO              = "lineno";
    public static final String KEY_LINENO_BACKGROUND   = "lineno_background";
    public static final String KEY_SELECTION           = "selection";
    public static final String KEY_CURRENT_LINE        = "current_line";

    // ---- Colores del resaltado de sintaxis (C) ----
    public static final String KEY_SYNTAX_KEYWORD      = "syntax.keyword";
    public static final String KEY_SYNTAX_PREPROCESSOR = "syntax.preprocessor";
    public static final String KEY_SYNTAX_COMMENT      = "syntax.comment";
    public static final String KEY_SYNTAX_STRING       = "syntax.string";
    public static final String KEY_SYNTAX_CHARACTER    = "syntax.character";
    public static final String KEY_SYNTAX_OPERATOR     = "syntax.operator";
    public static final String KEY_SYNTAX_ZX_FUNCTION  = "syntax.zx_function";
    public static final String KEY_SYNTAX_GBDK_FUNCTION = "syntax.gbdk_function";

    // ---- Colores del resaltado de sintaxis (ASM) ----
    public static final String KEY_SYNTAX_ASM_LABEL      = "syntax.asm_label";
    public static final String KEY_SYNTAX_ASM_DIRECTIVE  = "syntax.asm_directive";
    public static final String KEY_SYNTAX_ASM_INSTRUCTION = "syntax.asm_instruction";
    public static final String KEY_SYNTAX_ASM_REGISTER   = "syntax.asm_register";
    public static final String KEY_SYNTAX_ASM_NUMBER     = "syntax.asm_number";

    // ---- Colores del resaltado de sintaxis (Markdown) ----
    public static final String KEY_SYNTAX_MD_HEADING        = "syntax.md_heading";
    public static final String KEY_SYNTAX_MD_BLOCKQUOTE     = "syntax.md_blockquote";
    public static final String KEY_SYNTAX_MD_LIST           = "syntax.md_list";
    public static final String KEY_SYNTAX_MD_CHECKLIST      = "syntax.md_checklist";
    public static final String KEY_SYNTAX_MD_CHECKLIST_PENDING = "syntax.md_checklist_pending";
    public static final String KEY_SYNTAX_MD_CHECKLIST_DONE = "syntax.md_checklist_done";
    public static final String KEY_SYNTAX_MD_TABLE          = "syntax.md_table";
    public static final String KEY_SYNTAX_MD_LINK           = "syntax.md_link";
    public static final String KEY_SYNTAX_MD_BOLD           = "syntax.md_bold";
    public static final String KEY_SYNTAX_MD_ITALIC         = "syntax.md_italic";
    public static final String KEY_SYNTAX_MD_CODE           = "syntax.md_code";
    public static final String KEY_SYNTAX_MD_CODE_BLOCK     = "syntax.md_code_block";

    // ---- Colores del resaltado de sintaxis (JSON) ----
    public static final String KEY_SYNTAX_JSON_KEY     = "syntax.json_key";
    public static final String KEY_SYNTAX_JSON_NUMBER  = "syntax.json_number";
    public static final String KEY_SYNTAX_JSON_LITERAL = "syntax.json_literal";

    // ---- Colores del resaltado de sintaxis (Atari BASIC) ----
    public static final String KEY_SYNTAX_BASIC_LINE_NUMBER = "syntax.basic_line_number";
    public static final String KEY_SYNTAX_BASIC_KEYWORD     = "syntax.basic_keyword";
    public static final String KEY_SYNTAX_BASIC_FUNCTION    = "syntax.basic_function";
    public static final String KEY_SYNTAX_BASIC_STRING      = "syntax.basic_string";
    public static final String KEY_SYNTAX_BASIC_NUMBER      = "syntax.basic_number";
    public static final String KEY_SYNTAX_BASIC_COMMENT     = "syntax.basic_comment";
    public static final String KEY_SYNTAX_BASIC_OPERATOR    = "syntax.basic_operator";
    public static final String KEY_SYNTAX_BASIC_VARIABLE    = "syntax.basic_variable";

    // ---- Marcas de diagnostico ----
    public static final String KEY_COMPILE_ERROR_LINE    = "compile_error_line";
    public static final String KEY_COMPILE_WARNING_LINE  = "compile_warning_line";
    public static final String KEY_GUTTER_ERROR          = "gutter_error";
    public static final String KEY_GUTTER_WARNING        = "gutter_warning";

    private static final double DEFAULT_FONT_SIZE        = 13.0;
    private static final double DEFAULT_LINE_NUMBER_SIZE = 12.0;

    private String id;
    private String name;
    private boolean builtIn;
    private boolean darkBase;
    private String fontFamily;
    private double fontSize = DEFAULT_FONT_SIZE;
    private double lineNumberSize = DEFAULT_LINE_NUMBER_SIZE;
    private Map<String, String> colors = new HashMap<>();

    public EditorTheme() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public boolean isDarkBase() {
        return darkBase;
    }

    public void setDarkBase(boolean darkBase) {
        this.darkBase = darkBase;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
    }

    public double getLineNumberSize() {
        return lineNumberSize;
    }

    public void setLineNumberSize(double lineNumberSize) {
        this.lineNumberSize = lineNumberSize;
    }

    /**
     * Tamaño de letra de los números de línea. Si no se definió uno explícito,
     * se deriva como {@code fontSize - 1} para que acompañe a la fuente del editor.
     */
    public double getEffectiveLineNumberSize() {
        return lineNumberSize > 0 ? lineNumberSize : Math.max(1, fontSize - 1);
    }

    public Map<String, String> getColors() {
        return colors;
    }

    public void setColors(Map<String, String> colors) {
        this.colors = colors != null ? colors : new HashMap<>();
    }

    /**
     * Devuelve el color de una clave, o un color vacío si no está definido.
     */
    public String getColor(String key) {
        if (colors == null) return "";
        String value = colors.get(key);
        return value != null ? value : "";
    }

    /**
     * Define el color de una clave. {@code null} elimina la clave.
     */
    public void setColor(String key, String value) {
        if (colors == null) {
            colors = new HashMap<>();
        }
        if (value == null) {
            colors.remove(key);
        } else {
            colors.put(key, value);
        }
    }

    public boolean hasColor(String key) {
        return colors != null && colors.get(key) != null;
    }

    /**
     * Copia profunda del modo (mismo id y nombre, mapa de colores independiente).
     */
    public EditorTheme copy() {
        EditorTheme copy = new EditorTheme();
        copy.setId(id);
        copy.setName(name);
        copy.setBuiltIn(builtIn);
        copy.setDarkBase(darkBase);
        copy.setFontFamily(fontFamily);
        copy.setFontSize(fontSize);
        copy.setLineNumberSize(lineNumberSize);
        copy.setColors(colors != null ? new HashMap<>(colors) : new HashMap<>());
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EditorTheme)) return false;
        EditorTheme that = (EditorTheme) o;
        return builtIn == that.builtIn
            && darkBase == that.darkBase
            && Double.compare(that.fontSize, fontSize) == 0
            && Double.compare(that.lineNumberSize, lineNumberSize) == 0
            && Objects.equals(id, that.id)
            && Objects.equals(name, that.name)
            && Objects.equals(fontFamily, that.fontFamily)
            && Objects.equals(colors, that.colors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, builtIn, darkBase, fontFamily, fontSize, lineNumberSize, colors);
    }

    @Override
    public String toString() {
        return "EditorTheme{id=" + id + ", name=" + name + ", builtIn=" + builtIn + "}";
    }
}