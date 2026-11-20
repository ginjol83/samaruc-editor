package com.retroeditor.util;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
        runOnFxAndWait(() -> {
            CodeArea codeArea = new CodeArea();
            codeArea.replaceText("Line 1\nLine 2\nLine 3\n");
            codeArea.moveTo(0);

            MultiCursorManager.attach(codeArea);
            MultiCursorManager.addCursorBelow(codeArea);

            Assertions.assertEquals(Set.of(codeArea.position(1, 0).toOffset()),
                    MultiCursorManager.getExtraCursors(codeArea));

            MultiCursorManager.addCursorBelow(codeArea);
            Assertions.assertEquals(Set.of(codeArea.position(1, 0).toOffset(),
                            codeArea.position(2, 0).toOffset()),
                    MultiCursorManager.getExtraCursors(codeArea));

            MultiCursorManager.clearCursors(codeArea);
            Assertions.assertTrue(MultiCursorManager.getExtraCursors(codeArea).isEmpty());
        });
    }

    @Test
    void testTypingAtAllCarets() {
        runOnFxAndWait(() -> {
            CodeArea codeArea = new CodeArea();
            codeArea.replaceText("Line 1\nLine 2\nLine 3\n");
            codeArea.moveTo(0);

            MultiCursorManager.attach(codeArea);
            MultiCursorManager.addCursorBelow(codeArea);
            fireKey(codeArea, KeyCode.X, "x");

            Assertions.assertEquals("xLine 1\nxLine 2\nLine 3\n", codeArea.getText());
            Assertions.assertEquals(Set.of(codeArea.position(1, 1).toOffset()),
                    MultiCursorManager.getExtraCursors(codeArea));
        });
    }

    @Test
    void testBackspaceAtAllCarets() {
        runOnFxAndWait(() -> {
            CodeArea codeArea = new CodeArea();
            codeArea.replaceText("abc\ndef\n");
            codeArea.moveTo(1);

            MultiCursorManager.attach(codeArea);
            MultiCursorManager.addCursorBelow(codeArea);
            fireKey(codeArea, KeyCode.BACK_SPACE, "");

            Assertions.assertEquals("bc\nef\n", codeArea.getText());
        });
    }

    @Test
    void testArrowMovesAllCarets() {
        runOnFxAndWait(() -> {
            CodeArea codeArea = new CodeArea();
            codeArea.replaceText("abc\ndef\n");
            codeArea.moveTo(1);

            MultiCursorManager.attach(codeArea);
            MultiCursorManager.addCursorBelow(codeArea);
            fireKey(codeArea, KeyCode.RIGHT, "");

            Assertions.assertEquals(2, codeArea.getCaretPosition());
            Assertions.assertEquals(Set.of(codeArea.position(1, 2).toOffset()),
                    MultiCursorManager.getExtraCursors(codeArea));
        });
    }

    @Test
    void testEscapeClearsCursors() {
        runOnFxAndWait(() -> {
            CodeArea codeArea = new CodeArea();
            codeArea.replaceText("Line 1\nLine 2\n");
            codeArea.moveTo(0);

            MultiCursorManager.attach(codeArea);
            MultiCursorManager.addCursorBelow(codeArea);
            fireKey(codeArea, KeyCode.ESCAPE, "");

            Assertions.assertTrue(MultiCursorManager.getExtraCursors(codeArea).isEmpty());
        });
    }

    @Test
    void testColumnSelectionBlock() {
        runOnFxAndWait(() -> {
            CodeArea codeArea = new CodeArea();
            codeArea.replaceText("Line 1\nLine 2\nLine 3\n");

            MultiCursorManager.selectColumnBlock(codeArea, 0, 0, 1, 4);
            Assertions.assertTrue(codeArea.getSelection().getLength() > 0);
        });
    }

    private static void fireKey(CodeArea codeArea, KeyCode code, String text) {
        codeArea.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code,
                false, false, false, false));
    }

    private static void runOnFxAndWait(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                Assertions.fail("Timeout esperando al hilo de JavaFX");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assertions.fail("Interrumpido esperando al hilo de JavaFX");
        }
        if (error.get() != null) {
            Assertions.fail(error.get());
        }
    }
}
