package com.retroeditor.util;

import org.fxmisc.richtext.CodeArea;

/**
 * Utilidades para conservar la posicion visible de un {@link CodeArea}.
 *
 * <p>RichTextFX puede recolocar el viewport cuando se reaplican estilos,
 * diagnosticos o se sustituye el documento. Estas utilidades capturan la
 * primera linea visible antes del cambio y la restauran despues, de modo que
 * el editor no salta al principio del archivo.</p>
 */
public final class EditorViewport {

    /** Fotografia del viewport en un momento concreto. */
    public static final class Snapshot {
        private final int firstVisibleParagraph;
        private final double estimatedScrollY;
        private final int caretPosition;

        private Snapshot(int firstVisibleParagraph, double estimatedScrollY, int caretPosition) {
            this.firstVisibleParagraph = firstVisibleParagraph;
            this.estimatedScrollY = estimatedScrollY;
            this.caretPosition = caretPosition;
        }

        public int getCaretPosition() {
            return caretPosition;
        }
    }

    private EditorViewport() {
    }

    /**
     * Captura la primera linea visible y la posicion vertical estimada.
     *
     * @param area editor del que tomar la instantanea
     * @return la instantanea, o {@code null} si el area no es valida
     */
    public static Snapshot capture(CodeArea area) {
        if (area == null) return null;
        try {
            int firstVisible = area.firstVisibleParToAllParIndex();
            Double scrollY = area.estimatedScrollYProperty().getValue();
            return new Snapshot(firstVisible, scrollY != null ? scrollY : 0.0, area.getCaretPosition());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Restaura la primera linea visible capturada previamente.
     *
     * @param area editor sobre el que restaurar
     * @param snapshot instantanea obtenida con {@link #capture(CodeArea)}
     */
    public static void restore(CodeArea area, Snapshot snapshot) {
        if (area == null || snapshot == null) return;
        try {
            int paragraphCount = area.getParagraphs().size();
            if (paragraphCount == 0) return;

            if (snapshot.firstVisibleParagraph >= 0) {
                int paragraph = Math.min(snapshot.firstVisibleParagraph, paragraphCount - 1);
                area.showParagraphAtTop(paragraph);
            } else if (snapshot.estimatedScrollY > 0) {
                area.estimatedScrollYProperty().setValue(snapshot.estimatedScrollY);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Ejecuta una operacion que puede alterar el viewport y lo restaura al
     * terminar, incluso si la operacion falla.
     *
     * @param area editor afectado
     * @param action operacion a ejecutar
     */
    public static void preserve(CodeArea area, Runnable action) {
        Snapshot snapshot = capture(area);
        try {
            action.run();
        } finally {
            restore(area, snapshot);
        }
    }
}
