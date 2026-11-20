package com.retroeditor.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FXUtilsLineEditingTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    void duplicateCurrentLineInMiddle() {
        runOnFxAndWait(() -> {
            CodeArea area = new CodeArea();
            area.replaceText("alpha\nbeta\ngamma\n");
            area.moveTo(1, 2);

            new FXUtils().duplicateSelectedLines(area);

            Assertions.assertEquals("alpha\nbeta\nbeta\ngamma\n", area.getText());
            Assertions.assertEquals(2, area.getCurrentParagraph());
            Assertions.assertEquals(2, area.getCaretColumn());
        });
    }

    @Test
    void duplicateLastLineWithoutTrailingNewline() {
        runOnFxAndWait(() -> {
            CodeArea area = new CodeArea();
            area.replaceText("alpha\nbeta");
            area.moveTo(1, 4);

            new FXUtils().duplicateSelectedLines(area);

            Assertions.assertEquals("alpha\nbeta\nbeta", area.getText());
            Assertions.assertEquals(2, area.getCurrentParagraph());
            Assertions.assertEquals(4, area.getCaretColumn());
        });
    }

    @Test
    void duplicateLastLineWithTrailingNewlineKeepsSingleNewline() {
        runOnFxAndWait(() -> {
            CodeArea area = new CodeArea();
            area.replaceText("alpha\nbeta\n");
            area.moveTo(1, 4);

            new FXUtils().duplicateSelectedLines(area);

            Assertions.assertEquals("alpha\nbeta\nbeta\n", area.getText());
            Assertions.assertEquals(2, area.getCurrentParagraph());
            Assertions.assertEquals(4, area.getCaretColumn());
        });
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
