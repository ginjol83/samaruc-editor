package com.retroeditor.service.syntax;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Servicio para resaltado de sintaxis para C.
 */
public class SyntaxHighlighter {
    private static final String[] KEYWORDS = new String[] {
        "auto",  "break",    "case",    "char",     "const",     "continue",  "default",  "do",      "double", "_Thread_local",  "_Imaginary",
        "else",  "enum",     "extern",  "float",    "for",       "goto",      "restrict", "return",  "short",  "_Static_assert", "_Noreturn",
        "if",    "inline",   "int",     "long",     "register",  "signed",    "sizeof",   "static",  "struct", "switch",         "typedef",
        "union", "unsigned", "void",    "volatile", "while",     "_Alignas",  "_Alignof", "_Atomic", "_Bool",  "_Complex",       "_Generic"
    };

    private static final String  KEYWORD_PATTERN      = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    // [\\s\\S]*? en lugar de (.|\\R)*? para evitar retroceso exponencial en comentarios multilínea no cerrados
    private static final String  COMMENT_PATTERN      = "//[^\\n]*|/\\*[\\s\\S]*?\\*/";
    private static final String  STRING_PATTERN       = "\"([^\\\\\"\\\\]|\\\\.)*\"";
    // (?m) activa MULTILINE para que ^ funcione al inicio de cada línea
    private static final String  PREPROCESSOR_PATTERN = "(?m)^[ \\t]*#[^\\n]*";

    private static final Pattern PATTERN         = Pattern.compile(
                                                           "(?<PREPROCESSOR>" + PREPROCESSOR_PATTERN + ")"
                                                         + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                                                         + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                                                         + "|(?<STRING>" + STRING_PATTERN + ")"
                                                    );

    /**
     * Computa el resaltado de sintaxis para el texto dado.
     * @param text Texto a resaltar.
     * @return Estilos de resaltado para el texto.
     */
    public StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher   = PATTERN.matcher(text);
        int     lastKwEnd = 0;

        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                    matcher.group("PREPROCESSOR") != null ? "preprocessor" :
                    matcher.group("KEYWORD")       != null ? "keyword"      :
                    matcher.group("COMMENT")       != null ? "comment"      :
                    matcher.group("STRING")        != null ? "string"       :
                    null;

            assert styleClass != null;

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());

            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);

        return spansBuilder.create();
    }
}
