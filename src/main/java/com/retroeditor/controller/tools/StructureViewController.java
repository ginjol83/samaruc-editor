package com.retroeditor.controller.tools;

import java.util.List;
import java.util.function.Consumer;

import com.retroeditor.service.StructureViewService;
import com.retroeditor.service.StructureViewService.StructureSymbol;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.fxmisc.richtext.CodeArea;

public class StructureViewController {

    @FXML private TreeView<StructureSymbol> structureTreeView;

    private final StructureViewService structureViewService = new StructureViewService();
    private Consumer<Integer> onSymbolSelectedCallback;
    private CodeArea activeCodeArea;
    private ChangeListener<String> textListener;
    private ChangeListener<Number> caretListener;
    private List<StructureSymbol> currentSymbols = List.of();
    private boolean suppressNavigation;
    private int lastFollowedIndex = -1;

    @FXML
    public void initialize() {
        if (structureTreeView != null) {
            structureTreeView.setShowRoot(false);
            TreeItem<StructureSymbol> root = new TreeItem<>(new StructureSymbol("Root", 0, "root", "Root"));
            structureTreeView.setRoot(root);

            structureTreeView.setCellFactory(tv -> new javafx.scene.control.TreeCell<>() {
                @Override
                protected void updateItem(StructureSymbol item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item.getName() + " (L." + item.getLine() + ") [" + item.getType() + "]");
                    }
                }
            });

            structureTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (suppressNavigation || newVal == null || newVal.getValue() == null
                        || newVal.getValue().getLine() <= 0) {
                    return;
                }
                lastFollowedIndex = currentSymbols.indexOf(newVal.getValue());
                int line = newVal.getValue().getLine();
                suppressNavigation = true;
                try {
                    if (onSymbolSelectedCallback != null) {
                        onSymbolSelectedCallback.accept(line);
                    } else if (activeCodeArea != null) {
                        try {
                            int targetOffset = activeCodeArea.position(Math.max(0, line - 1), 0).toOffset();
                            activeCodeArea.moveTo(targetOffset);
                            activeCodeArea.requestFollowCaret();
                            activeCodeArea.requestFocus();
                        } catch (Exception ignored) {
                        }
                    }
                } finally {
                    suppressNavigation = false;
                }
            });
        }
    }

    public void updateStructure(String codeText) {
        if (structureTreeView == null) return;
        TreeItem<StructureSymbol> root = structureTreeView.getRoot();
        if (root == null) {
            root = new TreeItem<>(new StructureSymbol("Root", 0, "root", "Root"));
            structureTreeView.setRoot(root);
        }
        root.getChildren().clear();

        List<StructureSymbol> symbols = structureViewService.getStructure(codeText);
        currentSymbols = symbols;
        for (StructureSymbol sym : symbols) {
            root.getChildren().add(new TreeItem<>(sym));
        }
        root.setExpanded(true);
        followCaret();
    }

    @FXML
    private void onRefreshOutline() {
        if (activeCodeArea != null) {
            updateStructure(activeCodeArea.getText());
        }
    }

    public void setActiveCodeArea(CodeArea codeArea) {
        if (activeCodeArea != null) {
            if (textListener != null) {
                activeCodeArea.textProperty().removeListener(textListener);
            }
            if (caretListener != null) {
                activeCodeArea.caretPositionProperty().removeListener(caretListener);
            }
        }
        this.activeCodeArea = codeArea;
        textListener = null;
        caretListener = null;
        lastFollowedIndex = -1;
        if (codeArea != null) {
            textListener = (obs, oldV, newV) -> updateStructure(codeArea.getText());
            caretListener = (obs, oldV, newV) -> followCaret();
            codeArea.textProperty().addListener(textListener);
            codeArea.caretPositionProperty().addListener(caretListener);
            updateStructure(codeArea.getText());
        } else {
            updateStructure("");
        }
    }

    private void followCaret() {
        if (suppressNavigation || activeCodeArea == null || structureTreeView == null) {
            return;
        }
        TreeItem<StructureSymbol> root = structureTreeView.getRoot();
        if (root == null) {
            return;
        }
        int caretLine = activeCodeArea.getCurrentParagraph() + 1;
        int index = StructureViewService.indexOfSymbolAt(currentSymbols, caretLine);
        if (index < 0 || index == lastFollowedIndex || index >= root.getChildren().size()) {
            return;
        }
        TreeItem<StructureSymbol> item = root.getChildren().get(index);
        lastFollowedIndex = index;
        suppressNavigation = true;
        try {
            structureTreeView.getSelectionModel().select(item);
            int row = structureTreeView.getRow(item);
            if (row >= 0) {
                structureTreeView.scrollTo(row);
            }
        } finally {
            suppressNavigation = false;
        }
    }

    public void setOnSymbolSelectedCallback(Consumer<Integer> callback) {
        this.onSymbolSelectedCallback = callback;
    }
}
