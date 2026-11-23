package com.retroeditor.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.fxmisc.richtext.CodeArea;

public final class EditorLineHighlight {

    public static final String STYLE = "current-line";

    private static final Set<String> DIAGNOSTIC_STYLES = Set.of(
        "compile-error-line", "compile-warning-line");

    private EditorLineHighlight() {
    }

    public static List<String> withoutCurrentLine(CodeArea area, int paragraph) {
        List<String> styles = new ArrayList<>();
        if (!isValid(area, paragraph)) return styles;
        for (String style : area.getParagraph(paragraph).getParagraphStyle()) {
            if (!STYLE.equals(style)) styles.add(style);
        }
        return styles;
    }

    public static List<String> withoutDiagnostics(CodeArea area, int paragraph) {
        List<String> styles = new ArrayList<>();
        if (!isValid(area, paragraph)) return styles;
        for (String style : area.getParagraph(paragraph).getParagraphStyle()) {
            if (!DIAGNOSTIC_STYLES.contains(style)) styles.add(style);
        }
        return styles;
    }

    public static List<String> withStyle(List<String> styles, String style) {
        List<String> result = new ArrayList<>(styles);
        if (!result.contains(style)) result.add(style);
        return result;
    }

    public static void move(CodeArea area, int fromParagraph, int toParagraph) {
        if (!isValid(area, toParagraph)) return;
        EditorViewport.preserve(area, () -> {
            setStyle(area, fromParagraph, withoutCurrentLine(area, fromParagraph));
            setStyle(area, toParagraph, withStyle(withoutCurrentLine(area, toParagraph), STYLE));
        });
    }

    public static void ensure(CodeArea area, int paragraph) {
        if (!isValid(area, paragraph)) return;
        setStyle(area, paragraph, withStyle(withoutCurrentLine(area, paragraph), STYLE));
    }

    private static void setStyle(CodeArea area, int paragraph, List<String> styles) {
        if (isValid(area, paragraph)) area.setParagraphStyle(paragraph, styles);
    }

    private static boolean isValid(CodeArea area, int paragraph) {
        return area != null && paragraph >= 0 && paragraph < area.getParagraphs().size();
    }
}
