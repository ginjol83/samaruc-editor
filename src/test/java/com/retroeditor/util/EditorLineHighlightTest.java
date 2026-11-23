package com.retroeditor.util;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EditorLineHighlightTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    void movingHighlightPreservesDiagnostics() {
        runOnFxAndWait(() -> {
            CodeArea area = new CodeArea();
            area.replaceText("alpha\nbeta\ngamma");
            area.setParagraphStyle(0, List.of("compile-error-line", EditorLineHighlight.STYLE));
            area.setParagraphStyle(1, List.of("compile-warning-line"));

            EditorLineHighlight.move(area, 0, 1);

            Assertions.assertTrue(area.getParagraph(0).getParagraphStyle().contains("compile-error-line"));
            Assertions.assertFalse(area.getParagraph(0).getParagraphStyle().contains(EditorLineHighlight.STYLE));
            Assertions.assertTrue(area.getParagraph(1).getParagraphStyle().contains("compile-warning-line"));
            Assertions.assertTrue(area.getParagraph(1).getParagraphStyle().contains(EditorLineHighlight.STYLE));
        });
    }

    @Test
    void compilerDiagnosticsKeepCurrentLine() {
        runOnFxAndWait(() -> {
            CodeArea area = new CodeArea();
            area.replaceText("alpha\nbeta\ngamma");
            EditorLineHighlight.ensure(area, 1);

            new CompilationDiagnosticsUiHelper().apply(area, Set.of(3), Set.of(2));

            Assertions.assertTrue(area.getParagraph(1).getParagraphStyle().contains(EditorLineHighlight.STYLE));
            Assertions.assertTrue(area.getParagraph(1).getParagraphStyle().contains("compile-warning-line"));
            Assertions.assertTrue(area.getParagraph(2).getParagraphStyle().contains("compile-error-line"));
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
        Assertions.assertTrue(await(latch), "JavaFX task timed out");
        if (error.get() != null) {
            Assertions.fail(error.get());
        }
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
