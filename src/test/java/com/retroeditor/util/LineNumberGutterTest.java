package com.retroeditor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

class LineNumberGutterTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    void hidesGutterWhenLineNumbersAreDisabled() throws Exception {
        runOnFx(area -> {
            area.replaceText("10 PRINT \"HI\"\n20 END");
            LineNumberGutter.apply(area, false);
            assertNull(area.getParagraphGraphicFactory());
        });
    }

    @Test
    void keepsDiagnosticMarkerWhenLineNumbersAreHidden() throws Exception {
        runOnFx(area -> {
            area.replaceText("a\nb\nc");
            LineNumberGutter.apply(area, false, Set.of(2), Set.of());

            Node graphic = area.getParagraphGraphicFactory().apply(1);
            Label marker = assertInstanceOf(Label.class, graphic);
            assertEquals("\u25CF", marker.getText());
            assertTrue(marker.getStyleClass().contains("diag-gutter-marker-error"));
        });
    }

    @Test
    void combinesLineNumbersAndDiagnosticMarkers() throws Exception {
        runOnFx(area -> {
            area.replaceText("a\nb\nc");
            LineNumberGutter.apply(area, true, Set.of(2), Set.of(3));

            var factory = area.getParagraphGraphicFactory();
            assertNotNull(factory);

            HBox gutter = assertInstanceOf(HBox.class, factory.apply(0));
            assertEquals(2, gutter.getChildren().size());
            assertTrue(gutter.getChildren().get(1) instanceof Label);
        });
    }

    private static void runOnFx(Consumer<CodeArea> action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.accept(new CodeArea());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "FX task timed out");
        if (error.get() != null) {
            fail("Unexpected exception on FX thread", error.get());
        }
    }
}
