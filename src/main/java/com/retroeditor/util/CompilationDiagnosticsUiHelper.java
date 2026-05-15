package com.retroeditor.util;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/**
 * Aplica estilos visuales de diagnosticos de compilacion sobre CodeArea.
 */
public class CompilationDiagnosticsUiHelper {

    private static final String STYLE_COMPILE_ERROR_LINE = "compile-error-line";
    private static final String STYLE_COMPILE_WARNING_LINE = "compile-warning-line";

    public void clear(CodeArea codeArea) {
        if (codeArea == null) return;

        int paragraphs = codeArea.getParagraphs().size();
        for (int i = 0; i < paragraphs; i++) {
            codeArea.setParagraphStyle(i, java.util.Collections.emptyList());
        }

        applyGutter(codeArea, java.util.Collections.emptySet(), java.util.Collections.emptySet());
    }

    public void apply(CodeArea codeArea, Set<Integer> errorLines, Set<Integer> warningLines) {
        if (codeArea == null) return;

        clear(codeArea);

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

        applyGutter(codeArea, safeErrors, safeWarnings);
    }

    private void applyGutter(CodeArea codeArea, Set<Integer> errorLines, Set<Integer> warningLines) {
        final Set<Integer> errors = new HashSet<>(errorLines != null ? errorLines : java.util.Collections.emptySet());
        final Set<Integer> warnings = new HashSet<>(warningLines != null ? warningLines : java.util.Collections.emptySet());

        IntFunction<Node> lineNumbers = LineNumberFactory.get(codeArea);
        codeArea.setParagraphGraphicFactory(paragraphIndex -> {
            Node lineNumberNode = lineNumbers.apply(paragraphIndex);
            int line = paragraphIndex + 1;

            Label marker = new Label(" ");
            marker.getStyleClass().add("diag-gutter-marker");

            if (errors.contains(line)) {
                marker.setText("●");
                marker.getStyleClass().add("diag-gutter-marker-error");
            } else if (warnings.contains(line)) {
                marker.setText("●");
                marker.getStyleClass().add("diag-gutter-marker-warning");
            }

            HBox gutter = new HBox(4, marker, lineNumberNode);
            gutter.setAlignment(Pos.CENTER_RIGHT);
            return gutter;
        });
    }
}

