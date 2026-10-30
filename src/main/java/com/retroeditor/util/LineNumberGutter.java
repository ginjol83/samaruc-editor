package com.retroeditor.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/**
 * Instala el gutter de numeros de linea de los editores.
 *
 * <p>El ancho del gutter se reserva para un minimo fijo de digitos, de modo que
 * el texto no cambia de posicion horizontal cuando el programa crece y pasa de
 * dos a tres cifras (o de tres a cuatro). Las marcas de diagnostico de
 * compilacion se mantienen aunque los numeros esten ocultos.</p>
 */
public final class LineNumberGutter {

    /** Digitos reservados como minimo para evitar saltos de alineacion. */
    public static final int MIN_DIGITS = 4;

    private LineNumberGutter() {
    }

    /**
     * Aplica el gutter sin marcas de diagnostico.
     *
     * @param area editor sobre el que actuar; puede ser {@code null}
     * @param showLineNumbers {@code true} para mostrar los numeros de linea
     */
    public static void apply(CodeArea area, boolean showLineNumbers) {
        apply(area, showLineNumbers, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Aplica el gutter respetando las lineas con errores o avisos.
     *
     * @param area editor sobre el que actuar; puede ser {@code null}
     * @param showLineNumbers {@code true} para mostrar los numeros de linea
     * @param errorLines lineas (base 1) con errores de compilacion
     * @param warningLines lineas (base 1) con avisos de compilacion
     */
    public static void apply(CodeArea area, boolean showLineNumbers,
                             Set<Integer> errorLines, Set<Integer> warningLines) {
        if (area == null) {
            return;
        }

        Set<Integer> errors = errorLines != null ? errorLines : Collections.emptySet();
        Set<Integer> warnings = warningLines != null ? warningLines : Collections.emptySet();
        boolean hasMarkers = !errors.isEmpty() || !warnings.isEmpty();

        if (!showLineNumbers && !hasMarkers) {
            area.setParagraphGraphicFactory(null);
            return;
        }

        IntFunction<Node> lineNumbers = showLineNumbers
                ? LineNumberFactory.get(area, digits -> "%1$" + Math.max(digits, MIN_DIGITS) + "s")
                : null;

        if (!hasMarkers) {
            area.setParagraphGraphicFactory(lineNumbers);
            return;
        }

        Set<Integer> safeErrors = new HashSet<>(errors);
        Set<Integer> safeWarnings = new HashSet<>(warnings);

        area.setParagraphGraphicFactory(paragraphIndex -> {
            Node lineNumberNode = lineNumbers != null ? lineNumbers.apply(paragraphIndex) : null;
            int line = paragraphIndex + 1;

            Label marker = new Label(" ");
            marker.getStyleClass().add("diag-gutter-marker");

            if (safeErrors.contains(line)) {
                marker.setText("\u25CF");
                marker.getStyleClass().add("diag-gutter-marker-error");
            } else if (safeWarnings.contains(line)) {
                marker.setText("\u25CF");
                marker.getStyleClass().add("diag-gutter-marker-warning");
            }

            if (lineNumberNode == null) {
                return marker;
            }

            HBox gutter = new HBox(4, marker, lineNumberNode);
            gutter.setAlignment(Pos.CENTER_RIGHT);
            return gutter;
        });
    }
}
