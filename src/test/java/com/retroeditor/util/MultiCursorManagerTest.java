package com.retroeditor.util;

import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MultiCursorManagerTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    void testMultiCursorManagement() {
        Platform.runLater(() -> {
            CodeArea codeArea = new CodeArea();
            codeArea.replaceText("Line 1\nLine 2\nLine 3\n");
            codeArea.moveTo(0, 0);

            MultiCursorManager.attach(codeArea);
            MultiCursorManager.addCursorBelow(codeArea);

            Assertions.assertFalse(MultiCursorManager.getExtraCursors(codeArea).isEmpty());

            MultiCursorManager.clearCursors(codeArea);
            Assertions.assertTrue(MultiCursorManager.getExtraCursors(codeArea).isEmpty());
        });
    }

    @Test
    void testColumnSelectionBlock() {
        Platform.runLater(() -> {
            CodeArea codeArea = new CodeArea();
            codeArea.replaceText("Line 1\nLine 2\nLine 3\n");

            MultiCursorManager.selectColumnBlock(codeArea, 0, 0, 1, 4);
            Assertions.assertTrue(codeArea.getSelection().getLength() > 0);
        });
    }
}
