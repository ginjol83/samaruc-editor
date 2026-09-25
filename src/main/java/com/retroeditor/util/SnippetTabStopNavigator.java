package com.retroeditor.util;

import java.util.List;

import com.retroeditor.service.SnippetService;
import com.retroeditor.service.SnippetService.TabStop;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.fxmisc.richtext.CodeArea;

/**
 * Inserta snippets en un {@link CodeArea} y gestiona la navegación de tabstops:
 * tras insertar, el cursor salta al primer tabstop; Tab / Shift+Tab recorren los
 * tabstops restantes y Escape salta a la posición final ({@code $0}).
 * Si no hay tabstops activos, Tab no se consume (comportamiento por defecto).
 */
public final class SnippetTabStopNavigator {

    private static final String PROP_STOPS = "snippet.tabstops";
    private static final String PROP_INDEX = "snippet.tabstopIndex";
    private static final String PROP_FINAL = "snippet.finalCaret";
    private static final String PROP_INSTALLED = "snippet.navigatorInstalled";

    private SnippetTabStopNavigator() {
    }

    /** Estado de navegación activo (o null si no hay snippet en curso). */
    private record Session(List<TabStop> stops, int index, int finalCaret) {}

    /**
     * Reemplaza la selección actual del editor por el snippet y activa la
     * navegación de tabstops.
     *
     * @param area    editor activo.
     * @param service motor de snippets.
     * @param template plantilla con marcadores ({@code $1}, {@code ${1:def}}, {@code $0}).
     */
    public static void insert(CodeArea area, SnippetService service, String template) {
        if (area == null || service == null || template == null) return;

        int start = area.getSelection().getStart();
        int end = area.getSelection().getEnd();
        SnippetService.Insertion insertion = service.project(area.getText(), start, end, template);

        area.replaceText(0, area.getLength(), insertion.fullText());
        area.requestFollowCaret();

        List<TabStop> stops = insertion.tabStops();
        if (stops.isEmpty()) {
            area.getProperties().remove(PROP_STOPS);
            area.moveTo(Math.min(insertion.finalCaret(), area.getLength()));
            installIfMissing(area);
            return;
        }

        Session session = new Session(stops, 0, insertion.finalCaret());
        area.getProperties().put(PROP_STOPS, session);
        focusStop(area, session);
        installIfMissing(area);
    }

    private static void installIfMissing(CodeArea area) {
        if (Boolean.TRUE.equals(area.getProperties().get(PROP_INSTALLED))) return;
        area.getProperties().put(PROP_INSTALLED, Boolean.TRUE);
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            Object stored = area.getProperties().get(PROP_STOPS);
            if (!(stored instanceof Session session)) return;
            if (event.getCode() == KeyCode.TAB && !event.isControlDown() && !event.isAltDown()) {
                int next = session.index() + (event.isShiftDown() ? -1 : 1);
                if (next >= 0 && next < session.stops().size()) {
                    Session updated = new Session(session.stops(), next, session.finalCaret());
                    area.getProperties().put(PROP_STOPS, updated);
                    focusStop(area, updated);
                    event.consume();
                } else if (!event.isShiftDown() && next >= session.stops().size()) {
                    area.getProperties().remove(PROP_STOPS);
                    area.moveTo(Math.min(session.finalCaret(), area.getLength()));
                    area.requestFollowCaret();
                    event.consume();
                }
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                area.getProperties().remove(PROP_STOPS);
                area.moveTo(Math.min(session.finalCaret(), area.getLength()));
                area.requestFollowCaret();
                event.consume();
            }
        });
    }

    private static void focusStop(CodeArea area, Session session) {
        TabStop stop = session.stops().get(session.index());
        int start = Math.min(stop.start(), area.getLength());
        int end = Math.min(stop.end(), area.getLength());
        if (end > start) {
            area.selectRange(start, end);
        } else {
            area.moveTo(start);
        }
        area.requestFollowCaret();
    }
}