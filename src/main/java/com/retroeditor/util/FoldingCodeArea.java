package com.retroeditor.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.fxmisc.richtext.CodeArea;

/**
 * {@link CodeArea} extendido con plegado de rangos de lÃ­neas.
 *
 * <p>RichTextFX no expone mecanismo para ocultar pÃ¡rrafos a nivel de celda, por lo
 * que el plegado se implementa sustituyendo el texto de un rango de lÃ­neas por un
 * marcador que contiene un token Ãºnico. El texto original (oculto) se conserva en
 * memoria y se restaura al desplegar.</p>
 *
 * <p>Los rangos se localizan siempre por su token â€” nunca por offsets â€” de modo
 * que las ediciones previas a un marcador no desincronizan el plegado. Si el
 * usuario borra o corrompe un marcador, la regiÃ³n asociada se descarta.</p>
 *
 * <p>Los consumidores que extraigan texto (guardar, compilar, autosave, formatear,
 * renombrar...) deben llamar a {@link #expandAll()} antes de leer el buffer para
 * no persistir la forma plegada.</p>
 */
public class FoldingCodeArea extends CodeArea {

    public static final class FoldResult {
        private final int startOffset;
        private final int endOffset;

        FoldResult(int startOffset, int endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public int getStartOffset() { return startOffset; }
        public int getEndOffset()   { return endOffset; }
    }

    private static final class FoldRegion {
        final String token;
        final String hidden;

        FoldRegion(String token, String hidden) {
            this.token = token;
            this.hidden = hidden;
        }
    }

    private final List<FoldRegion> regions = new ArrayList<>();
    private final String ownerToken = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private int nextId = 0;

    public FoldingCodeArea() {
        super();
        attachOrphanGuard();
    }

    public FoldingCodeArea(String text) {
        super(text);
        attachOrphanGuard();
    }

    /** Desplazamiento (0-based) del inicio de la lÃ­nea dada, lÃ­neas 1-based. */
    static int lineStartOffset(String text, int line) {
        if (text == null || line <= 1) return text == null ? 0 : 0;
        int pos = 0;
        int current = 1;
        while (current < line) {
            int nl = text.indexOf('\n', pos);
            if (nl < 0) return text.length();
            pos = nl + 1;
            current++;
        }
        return pos;
    }

    private static String leadingWhitespace(String text, int offset) {
        int lineStart = lineStartOffset(text, offsetToLine(text, offset));
        int i = lineStart;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        return text.substring(lineStart, i);
    }

    private static int offsetToLine(String text, int offset) {
        int idx = Math.max(0, Math.min(offset, text.length()));
        int line = 1;
        for (int i = 0; i < idx; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Computa el rango de sustituciÃ³n al plegar las lÃ­neas {@code startLine..endLine}
     * (inclusive, 1-based). El texto oculto es exactamente
     * {@code text.substring(startOffset, endOffset)} (incluye el salto de lÃ­nea
     * final de {@code endLine} cuando no es la Ãºltima lÃ­nea). LÃ³gica pura, sin
     * JavaFX, para poder comprobarla en tests.
     *
     * @return offsets de sustituciÃ³n, o {@code null} si el rango no es plegable
     */
public static FoldResult computeFold(String text, int startLine, int endLine) {
        if (text == null || startLine < 1 || endLine <= startLine) return null;
        int start = lineStartOffset(text, startLine);
        int end = lineStartOffset(text, endLine + 1);
        if (start >= text.length() || end <= start) return null;
        return new FoldResult(start, end);
    }

    /** Texto resultante de plegar, sustituyendo el rango por una Ãºnica lÃ­nea marcador. */
    public static String foldToText(String text, int startLine, int endLine, String marker) {
        FoldResult res = computeFold(text, startLine, endLine);
        if (res == null) return text;
        String indent = leadingWhitespace(text, res.getStartOffset());
        return text.substring(0, res.getStartOffset())
                + indent + marker + "\n"
                + text.substring(res.getEndOffset());
    }

    /**
     * Texto de la expansiÃ³n de un marcador: localiza {@code token} y sustituye la
     * lÃ­nea que lo contiene por el texto oculto {@code hidden}. LÃ³gica pura.
     *
     * @return el texto expandido, o {@code null} si el token no estÃ¡ presente
     */
    public static String expandToText(String text, String token, String hidden) {
        if (text == null || token == null || token.isEmpty()) return null;
        int markerPos = text.indexOf(token);
        if (markerPos < 0) return null;
        int lineStart = text.lastIndexOf('\n', markerPos - 1) + 1;
        int newline = text.indexOf('\n', markerPos);
        int lineEnd = newline < 0 ? text.length() : newline + 1;
        return text.substring(0, lineStart) + hidden + text.substring(lineEnd);
    }

    public boolean hasFolds() {
        return !regions.isEmpty();
    }

    public int getFoldCount() {
        return regions.size();
    }

    /**
     * Pliega las lÃ­neas {@code startLine..endLine} (inclusive, 1-based) bajo un
     * marcador Ãºnico. Si el rango solapa un plegado activo, es un no-op.
     *
     * @return {@code true} si se aplicÃ³ el plegado
     */
    public boolean foldRange(int startLine, int endLine, String title) {
        if (startLine < 1 || endLine <= startLine) return false;
        String text = getText();
        FoldResult res = computeFold(text, startLine, endLine);
        if (res == null || overlapsActiveFold(text, res.getStartOffset(), res.getEndOffset())) {
            return false;
        }
        int count = endLine - startLine + 1;
        String token = "@" + ownerToken + "-" + (++nextId);
        String label = title != null && !title.isBlank() ? title.trim() : "";
        String marker = "â–¾ " + label + (label.isEmpty() ? "" : " ") + "[" + count + " lÃ­neas] " + token;
        String indent = leadingWhitespace(text, res.getStartOffset());
        String folded = text.substring(0, res.getStartOffset())
                + indent + marker + "\n"
                + text.substring(res.getEndOffset());
        regions.add(new FoldRegion(token, text.substring(res.getStartOffset(), res.getEndOffset())));
        replaceText(0, text.length(), folded);
        int markerEnd = res.getStartOffset() + indent.length() + marker.length();
        moveTo(Math.min(markerEnd, folded.length()));
        return true;
    }

    private boolean overlapsActiveFold(String text, int start, int end) {
        for (FoldRegion r : regions) {
            int pos = text.indexOf(r.token);
            if (pos >= 0 && pos < end && pos + r.token.length() > start) return true;
        }
        return false;
    }

    /**
     * Despliega la regiÃ³n cuyo marcador estÃ© mÃ¡s cercano al caret.
     *
     * @return {@code true} si se desplegÃ³ alguna regiÃ³n
     */
    public boolean unfoldAtCaret() {
        String text = getText();
        int caret = getCaretPosition();
        String bestToken = null;
        int bestDist = Integer.MAX_VALUE;
        for (FoldRegion r : regions) {
            int pos = text.indexOf(r.token);
            if (pos >= 0) {
                int dist = Math.abs(pos - caret);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestToken = r.token;
                }
            }
        }
        return bestToken != null && expandByToken(bestToken);
    }

    /**
     * Expande todas las regiones activas del Ã¡rea dada (no-op si no es una
     * {@link FoldingCodeArea}). Los consumidores de texto deben invocarlo antes
     * de leer el buffer para no persistir la forma plegada.
     *
     * @return {@code true} si el Ã¡rea era plegable y tenÃ­a regiones activas
     */
    public static boolean expandAllFolds(CodeArea area) {
        return area instanceof FoldingCodeArea f && f.expandAll();
    }

    /** Despliega todas las regiones activas, restaurando el buffer completo. */
    public boolean expandAll() {
        boolean changed = false;
        while (!regions.isEmpty()) {
            FoldRegion r = regions.get(0);
            if (getText().contains(r.token)) {
                changed = expandByToken(r.token) || changed;
            } else {
                regions.remove(r);
            }
        }
        return changed;
    }

    private boolean expandByToken(String token) {
        FoldRegion region = null;
        for (FoldRegion r : regions) {
            if (r.token.equals(token)) {
                region = r;
                break;
            }
        }
        if (region == null) return false;
        String text = getText();
        if (!text.contains(region.token)) return false;
        String expanded = expandToText(text, region.token, region.hidden);
        if (expanded == null) return false;
        regions.remove(region);
        replaceText(0, text.length(), expanded);
        return true;
    }

    private void attachOrphanGuard() {
        multiPlainChanges().subscribe(changes -> {
            if (regions.isEmpty()) return;
            String text = getText();
            for (int i = regions.size() - 1; i >= 0; i--) {
                if (text.indexOf(regions.get(i).token) < 0) {
                    regions.remove(i);
                }
            }
        });
    }
}
