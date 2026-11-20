package com.retroeditor.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.fxmisc.richtext.CaretNode;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.TwoDimensional;

import javafx.scene.control.IndexRange;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Gestor de múltiples cursores y selección de columnas para CodeArea.
 */
public class MultiCursorManager {

    private static final WeakHashMap<CodeArea, List<CaretNode>> extraCaretsMap = new WeakHashMap<>();
    private static final WeakHashMap<CodeArea, Integer> caretSequence = new WeakHashMap<>();
    private static final Set<CodeArea> attachedAreas =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static void attach(CodeArea codeArea) {
        if (codeArea == null || !attachedAreas.add(codeArea)) return;

        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            List<CaretNode> extras = activeCarets(codeArea);
            if (event.isControlDown() && event.isAltDown()) {
                if (event.getCode() == KeyCode.UP) {
                    addCursorAbove(codeArea);
                    event.consume();
                } else if (event.getCode() == KeyCode.DOWN) {
                    addCursorBelow(codeArea);
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                if (!extras.isEmpty()) {
                    clearCursors(codeArea);
                    event.consume();
                }
            } else if (!extras.isEmpty() && isNavigationKey(event)) {
                moveAllCarets(codeArea, extras, event);
                event.consume();
            } else if (!extras.isEmpty() && isEditingKey(event)) {
                handleMultiEdit(codeArea, extras, event);
                event.consume();
            }
        });

        codeArea.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) {
                return;
            }
            if (event.isControlDown() && !event.isAltDown()) {
                int offset = codeArea.hit(event.getX(), event.getY()).getInsertionIndex();
                addCursorAt(codeArea, offset);
                event.consume();
            } else if (!event.isAltDown() && !event.isShiftDown() && !activeCarets(codeArea).isEmpty()) {
                clearCursors(codeArea);
            }
        });
    }

    private static boolean isEditingKey(KeyEvent event) {
        if (event.isControlDown() || event.isMetaDown() || event.isAltDown()) {
            return false;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE
                || code == KeyCode.ENTER || code == KeyCode.TAB) {
            return true;
        }
        String text = event.getText();
        return text != null && !text.isEmpty()
                && !text.chars().allMatch(Character::isISOControl);
    }

    private static boolean isNavigationKey(KeyEvent event) {
        if (event.isControlDown() || event.isMetaDown() || event.isAltDown() || event.isShiftDown()) {
            return false;
        }
        KeyCode code = event.getCode();
        return code == KeyCode.LEFT || code == KeyCode.RIGHT
                || code == KeyCode.UP || code == KeyCode.DOWN
                || code == KeyCode.HOME || code == KeyCode.END;
    }

    public static void addCursorAbove(CodeArea codeArea) {
        if (codeArea == null) return;
        List<CaretNode> carets = activeCarets(codeArea);
        int primary = codeArea.getCaretPosition();
        int primaryParagraph = codeArea.offsetToPosition(primary, TwoDimensional.Bias.Forward).getMajor();
        int topParagraph = primaryParagraph;
        for (CaretNode caret : carets) {
            topParagraph = Math.min(topParagraph, caret.getParagraphIndex());
        }
        if (topParagraph <= 0) return;
        int targetParagraph = topParagraph - 1;
        int column = Math.min(columnAtParagraph(codeArea, carets, primary, topParagraph),
                codeArea.getParagraphLength(targetParagraph));
        addCursorAt(codeArea, codeArea.position(targetParagraph, column).toOffset());
    }

    public static void addCursorBelow(CodeArea codeArea) {
        if (codeArea == null) return;
        List<CaretNode> carets = activeCarets(codeArea);
        int primary = codeArea.getCaretPosition();
        int primaryParagraph = codeArea.offsetToPosition(primary, TwoDimensional.Bias.Forward).getMajor();
        int bottomParagraph = primaryParagraph;
        for (CaretNode caret : carets) {
            bottomParagraph = Math.max(bottomParagraph, caret.getParagraphIndex());
        }
        int lastParagraph = codeArea.getParagraphs().size() - 1;
        if (bottomParagraph >= lastParagraph) return;
        int targetParagraph = bottomParagraph + 1;
        int column = Math.min(columnAtParagraph(codeArea, carets, primary, bottomParagraph),
                codeArea.getParagraphLength(targetParagraph));
        addCursorAt(codeArea, codeArea.position(targetParagraph, column).toOffset());
    }

    private static int columnAtParagraph(CodeArea codeArea, List<CaretNode> carets,
                                         int primary, int paragraph) {
        if (codeArea.offsetToPosition(primary, TwoDimensional.Bias.Forward).getMajor() == paragraph) {
            return codeArea.offsetToPosition(primary, TwoDimensional.Bias.Forward).getMinor();
        }
        for (CaretNode caret : carets) {
            if (caret.getParagraphIndex() == paragraph) {
                return caret.getColumnPosition();
            }
        }
        return 0;
    }

    public static void addCursorAt(CodeArea codeArea, int offset) {
        if (codeArea == null) return;
        int length = codeArea.getLength();
        int target = Math.max(0, Math.min(offset, length));
        List<CaretNode> carets = activeCarets(codeArea);

        if (target == codeArea.getCaretPosition()) {
            return;
        }
        for (CaretNode caret : new ArrayList<>(carets)) {
            if (caret.getPosition() == target) {
                removeCaret(codeArea, caret);
                return;
            }
        }

        CaretNode caret = new CaretNode(nextCaretName(codeArea), codeArea, target);
        caret.getStyleClass().add("multi-caret");
        if (codeArea.addCaret(caret)) {
            carets.add(caret);
        }
    }

    private static String nextCaretName(CodeArea codeArea) {
        int next = caretSequence.getOrDefault(codeArea, 0) + 1;
        caretSequence.put(codeArea, next);
        return "multi-caret-" + next;
    }

    public static void clearCursors(CodeArea codeArea) {
        if (codeArea == null) return;
        List<CaretNode> carets = extraCaretsMap.get(codeArea);
        if (carets == null) return;
        for (CaretNode caret : new ArrayList<>(carets)) {
            codeArea.removeCaret(caret);
        }
        carets.clear();
    }

    private static void removeCaret(CodeArea codeArea, CaretNode caret) {
        if (codeArea.removeCaret(caret)) {
            List<CaretNode> carets = extraCaretsMap.get(codeArea);
            if (carets != null) carets.remove(caret);
        }
    }

    private static List<CaretNode> activeCarets(CodeArea codeArea) {
        List<CaretNode> carets = extraCaretsMap.computeIfAbsent(codeArea, key -> new ArrayList<>());
        carets.removeIf(caret -> caret.getPosition() < 0 || caret.getPosition() > codeArea.getLength());
        return carets;
    }

    private static void handleMultiEdit(CodeArea codeArea, List<CaretNode> carets, KeyEvent event) {
        int primary = codeArea.getCaretPosition();
        IndexRange selection = codeArea.getSelection();
        boolean primaryHadSelection = selection.getLength() > 0;
        if (primaryHadSelection) {
            primary = selection.getStart();
            codeArea.deleteText(selection.getStart(), selection.getEnd());
            codeArea.moveTo(primary);
        }

        List<Integer> positions = collectPositions(codeArea, primary, carets);
        KeyCode code = event.getCode();
        String text = event.getText();

        for (int pos : positions) {
            if (pos == primary && primaryHadSelection
                    && (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE)) {
                continue;
            }
            if (code == KeyCode.BACK_SPACE) {
                if (pos > 0) {
                    codeArea.deleteText(pos - 1, pos);
                    if (pos <= primary) primary--;
                }
            } else if (code == KeyCode.DELETE) {
                if (pos < codeArea.getLength()) {
                    codeArea.deleteText(pos, pos + 1);
                    if (pos < primary) primary--;
                }
            } else if (code == KeyCode.ENTER) {
                codeArea.insertText(pos, "\n");
                if (pos <= primary) primary++;
            } else if (code == KeyCode.TAB) {
                codeArea.insertText(pos, "\t");
                if (pos <= primary) primary++;
            } else if (text != null && !text.isEmpty()) {
                codeArea.insertText(pos, text);
                if (pos <= primary) primary += text.length();
            }
        }

        codeArea.moveTo(Math.max(0, Math.min(primary, codeArea.getLength())));
        codeArea.deselect();
        codeArea.requestFollowCaret();
        pruneOverlappingCarets(codeArea, carets);
    }

    private static List<Integer> collectPositions(CodeArea codeArea, int primary, List<CaretNode> carets) {
        Set<Integer> unique = new LinkedHashSet<>();
        unique.add(primary);
        for (CaretNode caret : carets) {
            int position = caret.getPosition();
            if (position >= 0 && position <= codeArea.getLength()) {
                unique.add(position);
            }
        }
        List<Integer> positions = new ArrayList<>(unique);
        positions.sort((a, b) -> Integer.compare(b, a));
        return positions;
    }

    private static void moveAllCarets(CodeArea codeArea, List<CaretNode> carets, KeyEvent event) {
        if (codeArea.getSelection().getLength() > 0) {
            clearCursors(codeArea);
            return;
        }
        int primary = moveOffset(codeArea, codeArea.getCaretPosition(), event.getCode());
        for (CaretNode caret : carets) {
            caret.moveTo(moveOffset(codeArea, caret.getPosition(), event.getCode()));
        }
        codeArea.moveTo(primary);
        codeArea.requestFollowCaret();
        pruneOverlappingCarets(codeArea, carets);
    }

    private static int moveOffset(CodeArea codeArea, int offset, KeyCode code) {
        int length = codeArea.getLength();
        int pos = Math.max(0, Math.min(offset, length));
        var position = codeArea.offsetToPosition(pos, TwoDimensional.Bias.Forward);
        int paragraph = position.getMajor();
        int column = position.getMinor();
        int paragraphs = codeArea.getParagraphs().size();

        if (code == KeyCode.LEFT) {
            return Math.max(0, pos - 1);
        }
        if (code == KeyCode.RIGHT) {
            return Math.min(length, pos + 1);
        }
        if (code == KeyCode.HOME) {
            return codeArea.position(paragraph, 0).toOffset();
        }
        if (code == KeyCode.END) {
            return codeArea.position(paragraph, codeArea.getParagraphLength(paragraph)).toOffset();
        }
        if (code == KeyCode.UP) {
            if (paragraph <= 0) return pos;
            int targetColumn = Math.min(column, codeArea.getParagraphLength(paragraph - 1));
            return codeArea.position(paragraph - 1, targetColumn).toOffset();
        }
        if (code == KeyCode.DOWN) {
            if (paragraph >= paragraphs - 1) return pos;
            int targetColumn = Math.min(column, codeArea.getParagraphLength(paragraph + 1));
            return codeArea.position(paragraph + 1, targetColumn).toOffset();
        }
        return pos;
    }

    private static void pruneOverlappingCarets(CodeArea codeArea, List<CaretNode> carets) {
        int primary = codeArea.getCaretPosition();
        Set<Integer> seen = new LinkedHashSet<>();
        for (CaretNode caret : new ArrayList<>(carets)) {
            int position = caret.getPosition();
            if (position == primary || !seen.add(position)) {
                removeCaret(codeArea, caret);
            }
        }
    }

    public static Set<Integer> getExtraCursors(CodeArea codeArea) {
        List<CaretNode> carets = extraCaretsMap.get(codeArea);
        if (carets == null) return Collections.emptySet();
        Set<Integer> positions = new LinkedHashSet<>();
        for (CaretNode caret : carets) {
            positions.add(caret.getPosition());
        }
        return positions;
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
