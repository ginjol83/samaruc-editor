package com.retroeditor.util;

import java.util.List;
import java.util.function.IntFunction;

import com.retroeditor.service.git.GitModels.DiffLineType;
import com.retroeditor.service.git.GitModels.GitDiffEntry;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import org.fxmisc.richtext.CodeArea;

/**
 * Vista de diff de un archivo en modo solo lectura (item 17): un {@link CodeArea}
 * no editable que muestra el diff de git línea a línea con un gutter que indica
 * el número de línea old/new y un prefijo {@code +}/{@code -}, y color por tipo
 * (ADD/REMOVE/HUNK/HEADER/CONTEXT). Los colores se resuelven con las clases CSS
 * {@code .code-area .paragraph-text.diff-add} etc. definidas en {@code app.css} y
 * {@code app-dark.css}.
 */
public final class GitDiffViewer {

    private GitDiffViewer() {
    }

    /**
     * Construye un {@link CodeArea} de solo lectura con el diff dado.
     *
     * @param entries líneas de diff clasificadas (incluido numero old/new).
     * @param fileLabel cabecera opcional (p.ej. "src/main.c") mostrada como primera línea.
     * @return CodeArea configurado (embébbolo en un {@code VirtualizedScrollPane}).
     */
    public static CodeArea create(List<GitDiffEntry> entries, String fileLabel) {
        CodeArea area = new CodeArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("code-area");

        StringBuilder text = new StringBuilder();
        if (fileLabel != null && !fileLabel.isBlank()) {
            text.append(fileLabel).append('\n');
        }
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(entries.get(i).line().text());
        }
        area.replaceText(text.toString());

        int offset = fileLabel != null && !fileLabel.isBlank() ? 1 : 0;
        for (int i = 0; i < entries.size(); i++) {
            String css = cssFor(entries.get(i).line().type());
            if (css != null) {
                area.setParagraphStyle(i + offset, java.util.Collections.singleton(css));
            }
        }
        if (offset == 1) {
            area.setParagraphStyle(0, java.util.Collections.singleton("diff-file-header"));
        }

        area.setParagraphGraphicFactory(paragraphGraphicFactory(entries, offset));
        return area;
    }

    private static IntFunction<Node> paragraphGraphicFactory(List<GitDiffEntry> entries, int offset) {
        return paragraphIndex -> {
            int entryIndex = paragraphIndex - offset;
            String gutter = "  ";
            String css = "diff-gutter-context";
            if (paragraphIndex == 0 && offset == 1) {
                gutter = "   ";
                css = "diff-gutter-plain";
            } else if (entryIndex >= 0 && entryIndex < entries.size()) {
                GitDiffEntry entry = entries.get(entryIndex);
                switch (entry.line().type()) {
                    case ADD ->
                        { gutter = "+" + entry.newLine(); css = "diff-gutter-add"; }
                    case REMOVE ->
                        { gutter = "-" + entry.oldLine(); css = "diff-gutter-remove"; }
                    case HUNK, HEADER ->
                        { gutter = "   "; css = "diff-gutter-plain"; }
                    default ->
                        { gutter = entry.oldLine() + "," + entry.newLine(); css = "diff-gutter-context"; }
                }
            }
            Label label = new Label(gutter);
            label.getStyleClass().add(css);
            HBox box = new HBox(label);
            box.setAlignment(Pos.CENTER_RIGHT);
            return box;
        };
    }

    private static String cssFor(DiffLineType type) {
        switch (type) {
            case ADD:
                return "diff-add";
            case REMOVE:
                return "diff-remove";
            case HUNK:
                return "diff-hunk";
            case HEADER:
                return "diff-header";
            default:
                return "diff-context";
        }
    }
}