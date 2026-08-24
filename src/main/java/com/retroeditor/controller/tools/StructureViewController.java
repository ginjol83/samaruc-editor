package com.retroeditor.controller.tools;

import java.util.List;
import java.util.function.Consumer;

import com.retroeditor.service.StructureViewService;
import com.retroeditor.service.StructureViewService.StructureSymbol;

import javafx.fxml.FXML;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.fxmisc.richtext.CodeArea;

public class StructureViewController {

    @FXML private TreeView<StructureSymbol> structureTreeView;

    private final StructureViewService structureViewService = new StructureViewService();
    private Consumer<Integer> onSymbolSelectedCallback;
    private CodeArea activeCodeArea;

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
                if (newVal != null && newVal.getValue() != null && newVal.getValue().getLine() > 0) {
                    int line = newVal.getValue().getLine();
                    if (onSymbolSelectedCallback != null) {
                        onSymbolSelectedCallback.accept(line);
                    } else if (activeCodeArea != null) {
                        try {
                            int targetOffset = activeCodeArea.position(line - 1, 0).toOffset();
                            activeCodeArea.moveTo(targetOffset);
                            activeCodeArea.requestFollowCaret();
                            activeCodeArea.requestFocus();
                        } catch (Exception ignored) {
                        }
                    }
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
        for (StructureSymbol sym : symbols) {
            root.getChildren().add(new TreeItem<>(sym));
        }
        root.setExpanded(true);
    }

    @FXML
    private void onRefreshOutline() {
        if (activeCodeArea != null) {
            updateStructure(activeCodeArea.getText());
        }
    }

    public void setActiveCodeArea(CodeArea codeArea) {
        this.activeCodeArea = codeArea;
        if (codeArea != null) {
            updateStructure(codeArea.getText());
        } else {
            updateStructure("");
        }
    }

    public void setOnSymbolSelectedCallback(Consumer<Integer> callback) {
        this.onSymbolSelectedCallback = callback;
    }
}
