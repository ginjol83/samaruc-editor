package com.retroeditor.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;

/**
 * Drag & drop de pestañas (item 12 del plan): permite arrastrar una pestaña
 * dentro de su {@code TabPane} (reordenación) y entre panes divididos
 * ({@code tabPane} &harr; {@code secondaryTabPane}). No se usa
 * {@code TabDragPolicy} nativo: se implementa con DnD de JavaFX para que la
 * reordenación y el movimiento entre panes compartan el mismo mecanismo.
 */
public final class TabPaneDragSupport {

    public static final DataFormat TAB_FORMAT = new DataFormat("application/x-samaruc-tab");

    private static final Set<TabPane> PANES = new HashSet<>();
    private static final Map<String, Tab> DRAGS = new HashMap<>();
    private static final AtomicLong SEQ = new AtomicLong();

    private TabPaneDragSupport() {
    }

    /**
     * Instala los manejadores de DnD en un {@code TabPane}. Idempotente: cada
     * pane se instala una sola vez.
     */
    public static void install(TabPane pane) {
        if (pane == null || !PANES.add(pane)) return;
        pane.setOnDragOver(e -> {
            if (hasOurContent(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.MOVE);
                e.consume();
            }
        });
        pane.setOnDragDropped(e -> {
            moveTabTo(pane, e);
            e.setDropCompleted(true);
            e.consume();
        });
        pane.setOnDragDone(e -> {
            if (hasOurContent(e.getDragboard())) {
                DRAGS.clear();
                e.consume();
            }
        });
        pane.setOnDragDetected(e -> startDrag(pane, e));
    }

    /**
     * Índice de inserción (0..centers.size()) dado el punto de drop en X y los
     * centros de las cabeceras en el mismo espacio de coordenadas. Lógica pura.
     */
    public static int insertionIndex(List<Double> headerCenterXs, double x) {
        int index = 0;
        for (double center : headerCenterXs) {
            if (center < x) index++;
            else break;
        }
        return index;
    }

    private static void startDrag(TabPane pane, MouseEvent e) {
        Tab tab = tabUnderCursor(pane, e);
        if (tab == null) return;
        Dragboard db = pane.startDragAndDrop(TransferMode.MOVE);
        if (db == null) return;
        String id = "tab-" + SEQ.incrementAndGet();
        DRAGS.put(id, tab);
        db.setContent(Map.of(TAB_FORMAT, id));
        try {
            Node graphic = tab.getGraphic();
            if (graphic != null) {
                db.setDragView(graphic.snapshot(null, null));
            }
        } catch (Exception ignored) {
            // Snapshot opcional: si falla, el arrastre continúa sin previsualización.
        }
        e.consume();
    }

    private static void moveTabTo(TabPane target, javafx.scene.input.DragEvent e) {
        Dragboard db = e.getDragboard();
        Object content = db.getContent(TAB_FORMAT);
        if (!(content instanceof String id)) return;
        Tab tab = DRAGS.get(id);
        if (tab == null) return;
        TabPane source = paneOf(tab);
        double screenX = e.getSceneX();
        int insert = insertionIndex(centerXs(target), screenX);
        if (source != null) {
            source.getTabs().remove(tab);
        }
        insert = Math.max(0, Math.min(insert, target.getTabs().size()));
        target.getTabs().add(insert, tab);
        target.getSelectionModel().select(tab);
        DRAGS.remove(id);
    }

    private static TabPane paneOf(Tab tab) {
        for (TabPane pane : PANES) {
            if (pane.getTabs().contains(tab)) return pane;
        }
        return null;
    }

    /** Centros en coordenadas de pantalla de cada cabecera de pestaña. */
    private static List<Double> centerXs(TabPane pane) {
        List<Node> headers = new ArrayList<>(pane.lookupAll(".tab"));
        List<Double> centers = new ArrayList<>(headers.size());
        for (Node header : headers) {
            if (!header.isVisible()) continue;
            try {
                Bounds bounds = header.localToScreen(header.getBoundsInParent());
                centers.add(bounds.getCenterX());
            } catch (Exception ignored) {
                // Ignorar cabeceras sin geometría aún calculada.
            }
        }
        centers.sort(null);
        return centers;
    }

    private static Tab tabUnderCursor(TabPane pane, MouseEvent e) {
        Node node = e.getTarget() instanceof Node n ? n : null;
        while (node != null && node != pane) {
            if (node.getStyleClass().contains("tab")) break;
            node = node.getParent();
        }
        if (node == null || node == pane) return null;
        List<Node> headers = new ArrayList<>(pane.lookupAll(".tab"));
        int index = indexOfHeader(headers, node);
        return index >= 0 && index < pane.getTabs().size() ? pane.getTabs().get(index) : null;
    }

    private static int indexOfHeader(List<Node> headers, Node target) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i) == target) return i;
        }
        return -1;
    }

    private static boolean hasOurContent(Dragboard db) {
        return db != null && db.hasContent(TAB_FORMAT);
    }
}