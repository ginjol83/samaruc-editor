package com.retroeditor.service.syntax;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Servicio para resaltado de sintaxis para C, Assembly y Markdown.
 */
public class SyntaxHighlighter {
    private static final String[] KEYWORDS = new String[] {
        "auto",  "break",    "case",    "char",     "const",     "continue",  "default",  "do",      "double", "_Thread_local",  "_Imaginary",
        "else",  "enum",     "extern",  "float",    "for",       "goto",      "restrict", "return",  "short",  "_Static_assert", "_Noreturn",
        "if",    "inline",   "int",     "long",     "register",  "signed",    "sizeof",   "static",  "struct", "switch",         "typedef",
        "union", "unsigned", "void",    "volatile", "while",     "_Alignas",  "_Alignof", "_Atomic", "_Bool",  "_Complex",       "_Generic"
    };

    private static final String[] ZX_FUNCTIONS = new String[] {
        "textcolor", "textbackground", "bordercolor", "clrscr",
        "gotoxy", "cputs", "kbhit", "getch", "putch"
    };

    private static final String[] GBDK_FUNCTIONS = new String[] {
        "set_sprite_data", "set_sprite_tile", "move_sprite", "joypad",
        "wait_vbl_done", "set_bkg_data", "set_bkg_tiles", "set_win_tiles",
        "set_sprite_prop", "scroll_sprite", "move_bkg", "move_win",
        "scroll_bkg", "scroll_win", "set_bkg_tile_xy", "set_win_tile_xy",
        "set_bkg_submap", "set_win_submap", "waitpad", "waitpadup"
    };

    private static final String[] ASM_INSTRUCTIONS = new String[] {
        "adc", "add", "and", "bit", "call", "ccf", "cp", "cpl", "daa", "dec",
        "di", "ei", "ex", "exx", "halt", "inc", "in", "jp", "jr", "ld", "ldd",
        "ldi", "ldh", "nop", "or", "out", "pop", "push", "res", "ret", "reti",
        "rl", "rla", "rlc", "rr", "rra", "rrc", "rst", "sbc", "scf", "set",
        "sla", "sra", "srl", "sub", "swap", "xor"
    };

    private static final String[] ASM_DIRECTIVES = new String[] {
        "db", "dw", "ds", "defb", "defw", "defm", "equ", "global",
        "include", "incbin", "macro", "org", "public", "section", "set",
        "if", "ifdef", "ifndef", "elif", "else", "endif", "endm", "rept", "endr"
    };

    private static final String[] ASM_REGISTERS = new String[] {
        "a", "b", "c", "d", "e", "h", "l", "af", "bc", "de", "hl", "ix", "iy", "sp", "i", "r"
    };

    private static final String  KEYWORD_PATTERN      = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String  ZX_FUNCTION_PATTERN  = "\\b(" + String.join("|", ZX_FUNCTIONS) + ")\\b(?=\\s*\\()";
    private static final String  GBDK_FUNCTION_PATTERN = "\\b(" + String.join("|", GBDK_FUNCTIONS) + ")\\b(?=\\s*\\()";
    private static final String  ASM_INSTRUCTION_PATTERN = "\\b(" + String.join("|", ASM_INSTRUCTIONS) + ")\\b";
    private static final String  ASM_DIRECTIVE_PATTERN   = "\\b(" + String.join("|", ASM_DIRECTIVES) + ")\\b";
    private static final String  ASM_REGISTER_PATTERN    = "\\b(" + String.join("|", ASM_REGISTERS) + ")\\b";
    private static final String  ASM_LABEL_PATTERN       = "(?m)^\\s*[A-Za-z_.$?][A-Za-z0-9_.$?]*:";
    private static final String  ASM_NUMBER_PATTERN      = "(?:0x[0-9A-Fa-f]+|\\$[0-9A-Fa-f]+|%[01]+|\\b[0-9]+\\b)";
    private static final String  ASM_COMMENT_PATTERN     = ";[^\\r\\n]*|//[^\\r\\n]*";
    private static final String  ASM_STRING_PATTERN      = "\"([^\\\\\"\\\\]|\\\\.)*\"";
    // [\\s\\S]*? en lugar de (.|\\R)*? para evitar retroceso exponencial en comentarios multilínea no cerrados
    private static final String  COMMENT_PATTERN      = "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/";
    private static final String  STRING_PATTERN       = "\"([^\\\\\"\\\\]|\\\\.)*\"";
    private static final String  CHARACTER_PATTERN    = "'([^'\\\\]|\\\\.)*'";
    private static final String  OPERATOR_PATTERN     = "[~!%^&*+=|?:<>/-]";
    private static final String  PREPROCESSOR_PATTERN = "^[ \\t]*#[^\\r\\n]*";

    private static final String  MD_FENCED_CODE_PATTERN = "```[\\s\\S]*?```";
    private static final String  MD_HEADING_PATTERN     = "(?m)^#{1,6}\\s.*$";
    private static final String  MD_BLOCKQUOTE_PATTERN  = "(?m)^\\s*>\\s.*$";
    private static final String  MD_CHECKLIST_PENDING_PATTERN = "(?m)^\\s*[-*+]\\s*\\[ \\]\\s.+$";
    private static final String  MD_CHECKLIST_DONE_PATTERN    = "(?m)^\\s*[-*+]\\s*\\[(?:x|X)\\]\\s.+$";
    private static final String  MD_TABLE_PATTERN       = "(?m)^\\s*\\|.+\\|\\s*$|^\\s*[:\\- ]+\\|[:\\- |]*$";
    private static final String  MD_LIST_PATTERN        = "(?m)^\\s*(?:[-*+]\\s.+|\\d+\\.\\s.+)$";
    private static final String  MD_LINK_PATTERN        = "\\[[^\\]\\r\\n]+\\]\\([^\\)\\r\\n]+\\)";
    private static final String  MD_BOLD_PATTERN        = "\\*\\*[^*\\r\\n]+\\*\\*|__[^_\\r\\n]+__";
    private static final String  MD_ITALIC_PATTERN      = "(?<!\\*)\\*[^*\\r\\n]+\\*(?!\\*)|(?<!_)_[^_\\r\\n]+_(?!_)";
    private static final String  MD_INLINE_CODE_PATTERN = "`[^`\\r\\n]+`";

    private static final Pattern PATTERN         = Pattern.compile(
                                                           "(?<PREPROCESSOR>" + PREPROCESSOR_PATTERN + ")"
                                                         + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                                                         + "|(?<STRING>" + STRING_PATTERN + ")"
                                                         + "|(?<CHARACTER>" + CHARACTER_PATTERN + ")"
                                                         + "|(?<OPERATOR>" + OPERATOR_PATTERN + ")"
                                                         + "|(?<ZXFUNCTION>" + ZX_FUNCTION_PATTERN + ")"
                                                         + "|(?<GBDKFUNCTION>" + GBDK_FUNCTION_PATTERN + ")"
                                                         + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")",
                                                     Pattern.MULTILINE
                                                     );

    private static final Pattern ASM_PATTERN = Pattern.compile(
                                                   "(?<ASMLABEL>" + ASM_LABEL_PATTERN + ")"
                                                 + "|(?<ASMDIRECTIVE>" + ASM_DIRECTIVE_PATTERN + ")"
                                                 + "|(?<ASMINSTRUCTION>" + ASM_INSTRUCTION_PATTERN + ")"
                                                 + "|(?<ASMREGISTER>" + ASM_REGISTER_PATTERN + ")"
                                                 + "|(?<ASMNUMBER>" + ASM_NUMBER_PATTERN + ")"
                                                 + "|(?<ASMCOMMENT>" + ASM_COMMENT_PATTERN + ")"
                                                 + "|(?<ASMSTRING>" + ASM_STRING_PATTERN + ")",
                                               Pattern.MULTILINE
                                               );

    private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
                                                           "(?<MDFENCE>" + MD_FENCED_CODE_PATTERN + ")"
                                                         + "|(?<MDHEADING>" + MD_HEADING_PATTERN + ")"
                                                         + "|(?<MDBLOCKQUOTE>" + MD_BLOCKQUOTE_PATTERN + ")"
                                                         + "|(?<MDCHECKLISTPENDING>" + MD_CHECKLIST_PENDING_PATTERN + ")"
                                                         + "|(?<MDCHECKLISTDONE>" + MD_CHECKLIST_DONE_PATTERN + ")"
                                                         + "|(?<MDTABLE>" + MD_TABLE_PATTERN + ")"
                                                         + "|(?<MDLIST>" + MD_LIST_PATTERN + ")"
                                                         + "|(?<MDLINK>" + MD_LINK_PATTERN + ")"
                                                         + "|(?<MDBOLD>" + MD_BOLD_PATTERN + ")"
                                                         + "|(?<MDITALIC>" + MD_ITALIC_PATTERN + ")"
                                                         + "|(?<MDINLINECODE>" + MD_INLINE_CODE_PATTERN + ")",
                                                      Pattern.MULTILINE
                                                      );

    /**
     * Computa el resaltado de sintaxis para el texto dado.
     * @param text Texto a resaltar.
     * @return Estilos de resaltado para el texto.
     */
    public StyleSpans<Collection<String>> computeHighlighting(String text) {
        return computeCHighlighting(text);
    }

    public StyleSpans<Collection<String>> computeHighlighting(String text, String fileName) {
        if (isMarkdownFile(fileName)) {
            return computeMarkdownHighlighting(text);
        }

        if (isAssemblyFile(fileName)) {
            return computeAssemblyHighlighting(text);
        }

        return computeCHighlighting(text);
    }

    private StyleSpans<Collection<String>> computeCHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            StyleSpansBuilder<Collection<String>> emptyBuilder = new StyleSpansBuilder<>();
            emptyBuilder.add(Collections.emptyList(), 0);
            return emptyBuilder.create();
        }

        Matcher matcher   = PATTERN.matcher(text);
        int     lastKwEnd = 0;

        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                    matcher.group("PREPROCESSOR") != null ? "preprocessor" :
                    matcher.group("COMMENT")       != null ? "comment"      :
                    matcher.group("STRING")        != null ? "string"       :
                    matcher.group("CHARACTER")     != null ? "character"    :
                    matcher.group("OPERATOR")      != null ? "operator"     :
                    matcher.group("ZXFUNCTION")    != null ? "zx-function"  :
                    matcher.group("GBDKFUNCTION")  != null ? "gbdk-function":
                    matcher.group("KEYWORD")       != null ? "keyword"      :
                    null;

            assert styleClass != null;

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());

            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);

        return spansBuilder.create();
    }

    private StyleSpans<Collection<String>> computeMarkdownHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            StyleSpansBuilder<Collection<String>> emptyBuilder = new StyleSpansBuilder<>();
            emptyBuilder.add(Collections.emptyList(), 0);
            return emptyBuilder.create();
        }

        Matcher matcher = MARKDOWN_PATTERN.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            Collection<String> styleClasses =
                matcher.group("MDFENCE") != null ? Collections.singleton("md-code-block") :
                matcher.group("MDHEADING") != null ? Collections.singleton("md-heading") :
                matcher.group("MDBLOCKQUOTE") != null ? Collections.singleton("md-blockquote") :
                matcher.group("MDCHECKLISTPENDING") != null ? java.util.List.of("md-checklist", "md-checklist-pending") :
                matcher.group("MDCHECKLISTDONE") != null ? java.util.List.of("md-checklist", "md-checklist-done") :
                matcher.group("MDTABLE") != null ? Collections.singleton("md-table") :
                matcher.group("MDLIST") != null ? Collections.singleton("md-list") :
                matcher.group("MDLINK") != null ? Collections.singleton("md-link") :
                matcher.group("MDBOLD") != null ? Collections.singleton("md-bold") :
                matcher.group("MDITALIC") != null ? Collections.singleton("md-italic") :
                matcher.group("MDINLINECODE") != null ? Collections.singleton("md-code") :
                null;

            assert styleClasses != null;

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(styleClasses, matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    private StyleSpans<Collection<String>> computeAssemblyHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            StyleSpansBuilder<Collection<String>> emptyBuilder = new StyleSpansBuilder<>();
            emptyBuilder.add(Collections.emptyList(), 0);
            return emptyBuilder.create();
        }

        Matcher matcher = ASM_PATTERN.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                matcher.group("ASMLABEL") != null ? "asm-label" :
                matcher.group("ASMDIRECTIVE") != null ? "asm-directive" :
                matcher.group("ASMINSTRUCTION") != null ? "asm-instruction" :
                matcher.group("ASMREGISTER") != null ? "asm-register" :
                matcher.group("ASMNUMBER") != null ? "asm-number" :
                matcher.group("ASMCOMMENT") != null ? "comment" :
                matcher.group("ASMSTRING") != null ? "string" :
                null;

            assert styleClass != null;

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    private boolean isMarkdownFile(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        String normalized = fileName.toLowerCase();
        return normalized.endsWith(".md") || normalized.endsWith(".markdown");
    }

    private boolean isAssemblyFile(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        String normalized = fileName.toLowerCase();
        return normalized.endsWith(".asm") || normalized.endsWith(".s");
    }
}
