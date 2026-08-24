package com.retroeditor.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.TwoDimensional;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Gestor de múltiples cursores y selección de columnas para CodeArea.
 */
public class MultiCursorManager {

    private static final WeakHashMap<CodeArea, Set<Integer>> extraCursorsMap = new WeakHashMap<>();

    public static void attach(CodeArea codeArea) {
        if (codeArea == null) return;
        codeArea.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.isAltDown()) {
                if (event.getCode() == KeyCode.UP) {
                    addCursorAbove(codeArea);
                    event.consume();
                } else if (event.getCode() == KeyCode.DOWN) {
                    addCursorBelow(codeArea);
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                clearCursors(codeArea);
                event.consume();
            } else if (isEditingKey(event)) {
                Set<Integer> extras = extraCursorsMap.get(codeArea);
                if (extras != null && !extras.isEmpty()) {
                    handleMultiEdit(codeArea, extras, event);
                    event.consume();
                }
            }
        });
    }

    private static boolean isEditingKey(KeyEvent event) {
        if (event.isControlDown() || event.isMetaDown() || event.isAltDown()) {
            return false;
        }
        KeyCode code = event.getCode();
        String text = event.getText();
        return (text != null && !text.isEmpty() && !text.chars().allMatch(Character::isISOControl))
                || code == KeyCode.BACK_SPACE
                || code == KeyCode.DELETE
                || code == KeyCode.ENTER;
    }

    public static void addCursorAbove(CodeArea codeArea) {
        int primaryCaret = codeArea.getCaretPosition();
        var pos = codeArea.offsetToPosition(primaryCaret, TwoDimensional.Bias.Forward);
        int p = pos.getMajor();
        int col = pos.getMinor();
        if (p > 0) {
            int targetP = p - 1;
            int maxCol = codeArea.getParagraphLength(targetP);
            int targetCol = Math.min(col, maxCol);
            int newOffset = codeArea.position(targetP, targetCol).toOffset();
            extraCursorsMap.computeIfAbsent(codeArea, k -> new HashSet<>()).add(newOffset);
            codeArea.requestLayout();
        }
    }

    public static void addCursorBelow(CodeArea codeArea) {
        int primaryCaret = codeArea.getCaretPosition();
        var pos = codeArea.offsetToPosition(primaryCaret, TwoDimensional.Bias.Forward);
        int p = pos.getMajor();
        int col = pos.getMinor();
        int maxP = codeArea.getParagraphs().size();
        if (p < maxP - 1) {
            int targetP = p + 1;
            int maxCol = codeArea.getParagraphLength(targetP);
            int targetCol = Math.min(col, maxCol);
            int newOffset = codeArea.position(targetP, targetCol).toOffset();
            extraCursorsMap.computeIfAbsent(codeArea, k -> new HashSet<>()).add(newOffset);
            codeArea.requestLayout();
        }
    }

    public static void clearCursors(CodeArea codeArea) {
        Set<Integer> extras = extraCursorsMap.get(codeArea);
        if (extras != null) {
            extras.clear();
        }
        codeArea.requestLayout();
    }

    private static void handleMultiEdit(CodeArea codeArea, Set<Integer> extras, KeyEvent event) {
        List<Integer> allPositions = new ArrayList<>(extras);
        allPositions.add(codeArea.getCaretPosition());
        allPositions.sort((a, b) -> Integer.compare(b, a));

        KeyCode code = event.getCode();
        String text = event.getText();

        try {
            for (int pos : allPositions) {
                if (code == KeyCode.BACK_SPACE) {
                    if (pos > 0) {
                        codeArea.deleteText(pos - 1, pos);
                    }
                } else if (code == KeyCode.DELETE) {
                    if (pos < codeArea.getLength()) {
                        codeArea.deleteText(pos, pos + 1);
                    }
                } else if (code == KeyCode.ENTER) {
                    codeArea.insertText(pos, "\n");
                } else if (text != null && !text.isEmpty()) {
                    codeArea.insertText(pos, text);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static Set<Integer> getExtraCursors(CodeArea codeArea) {
        return extraCursorsMap.getOrDefault(codeArea, Collections.emptySet());
    }

    public static void selectColumnBlock(CodeArea codeArea, int startP, int startCol, int endP, int endCol) {
        if (codeArea == null) return;
        int minP = Math.min(startP, endP);
        int maxP = Math.max(startP, endP);
        int minCol = Math.min(startCol, endCol);
        int maxCol = Math.max(startCol, endCol);

        int startOffset = codeArea.position(minP, minCol).toOffset();
        int endOffset = codeArea.position(maxP, maxCol).toOffset();
        codeArea.selectRange(startOffset, endOffset);
    }
}
