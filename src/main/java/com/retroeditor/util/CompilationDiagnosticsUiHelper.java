package com.retroeditor.util;

import java.util.Set;

import org.fxmisc.richtext.CodeArea;

/**
 * Aplica estilos visuales de diagnosticos de compilacion sobre CodeArea.
 */
public class CompilationDiagnosticsUiHelper {

    private static final String STYLE_COMPILE_ERROR_LINE = "compile-error-line";
    private static final String STYLE_COMPILE_WARNING_LINE = "compile-warning-line";

    public void clear(CodeArea codeArea) {
        clear(codeArea, true);
    }

    public void clear(CodeArea codeArea, boolean showLineNumbers) {
        if (codeArea == null) return;

        EditorViewport.Snapshot viewport = EditorViewport.capture(codeArea);
        try {
            int paragraphs = codeArea.getParagraphs().size();
            for (int i = 0; i < paragraphs; i++) {
                codeArea.setParagraphStyle(i, java.util.Collections.emptyList());
            }

            LineNumberGutter.apply(codeArea, showLineNumbers);
        } finally {
            EditorViewport.restore(codeArea, viewport);
        }
    }

    public void apply(CodeArea codeArea, Set<Integer> errorLines, Set<Integer> warningLines) {
        apply(codeArea, errorLines, warningLines, true);
    }

    public void apply(CodeArea codeArea, Set<Integer> errorLines, Set<Integer> warningLines,
                      boolean showLineNumbers) {
        if (codeArea == null) return;

        EditorViewport.Snapshot viewport = EditorViewport.capture(codeArea);
        try {
            clear(codeArea, showLineNumbers);

            Set<Integer> safeErrors = (errorLines != null) ? errorLines : java.util.Collections.emptySet();
            Set<Integer> safeWarnings = (warningLines != null) ? warningLines : java.util.Collections.emptySet();

            int maxParagraphs = codeArea.getParagraphs().size();
            for (Integer line : safeWarnings) {
                if (line == null) continue;
                int paragraphIndex = line - 1;
                if (paragraphIndex < 0 || paragraphIndex >= maxParagraphs) continue;
                codeArea.setParagraphStyle(paragraphIndex, java.util.Collections.singleton(STYLE_COMPILE_WARNING_LINE));
            }

            for (Integer line : safeErrors) {
                if (line == null) continue;
                int paragraphIndex = line - 1;
                if (paragraphIndex < 0 || paragraphIndex >= maxParagraphs) continue;
                codeArea.setParagraphStyle(paragraphIndex, java.util.Collections.singleton(STYLE_COMPILE_ERROR_LINE));
            }

            LineNumberGutter.apply(codeArea, showLineNumbers, safeErrors, safeWarnings);
        } finally {
            EditorViewport.restore(codeArea, viewport);
        }
    }
}
