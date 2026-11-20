package com.retroeditor.util;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EditorViewportTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    void preserveKeepsPartiallyScrolledViewport() {
        runOnFxAndWait(() -> {
            CodeArea area = new CodeArea();
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 400; i++) {
                text.append("line ").append(i).append('\n');
            }
            area.replaceText(text.toString());

            VirtualizedScrollPane<CodeArea> pane = new VirtualizedScrollPane<>(area);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 480, 160));
            try {
                stage.show();
                pump(area, pane);

                area.showParagraphAtTop(100);
                pump(area, pane);
                area.scrollYToPixel(area.estimatedScrollYProperty().getValue() + 7.5);
                pump(area, pane);

                EditorViewport.Snapshot snapshot = EditorViewport.capture(area);
                Assertions.assertNotNull(snapshot);
                double before = area.estimatedScrollYProperty().getValue();
                Assertions.assertTrue(before > 0, "El area deberia estar desplazada");

                EditorViewport.preserve(area, () -> area.setStyleSpans(0,
                        StyleSpans.singleton(Collections.emptyList(), area.getLength())));
                pump(area, pane);

                double after = area.estimatedScrollYProperty().getValue();
                Assertions.assertEquals(before, after, 0.5,
                        "Reaplicar estilos no debe reencuadrar el viewport");
            } finally {
                stage.close();
            }
        });
    }

    private static void pump(CodeArea area, VirtualizedScrollPane<CodeArea> pane) {
        area.applyCss();
        pane.applyCss();
        pane.layout();
        area.layout();
        area.applyCss();
        pane.layout();
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
            if (!latch.await(15, TimeUnit.SECONDS)) {
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
