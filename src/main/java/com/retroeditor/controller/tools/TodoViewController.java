package com.retroeditor.controller.tools;

import java.io.File;
import java.util.function.BiConsumer;

import com.retroeditor.service.TodoScanService.TodoItem;

import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class TodoViewController {

    @FXML private TextField todoFilterField;
    @FXML private ListView<TodoItem> todoListView;

    private final java.util.List<TodoItem> allItems = new java.util.ArrayList<>();
    private BiConsumer<File, Integer> onTodoSelectedCallback;

    @FXML
    public void initialize() {
        if (todoListView != null) {
            todoListView.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(TodoItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getMessage().isEmpty()
                                ? item.getFile().getName() + " : " + item.getLine()
                                : item.getMessage() + "  (" + item.getFile().getName() + ":" + item.getLine() + ")");
                    }
                }
            });
            todoListView.setOnMouseClicked(event -> {
                if (event.getClickCount() >= 2) {
                    navigateToSelected();
                }
            });
        }
        if (todoFilterField != null) {
            todoFilterField.textProperty().addListener((obs, oldV, newV) -> refresh());
        }
    }

    public void setItems(java.util.List<TodoItem> items) {
        allItems.clear();
        if (items != null) {
            allItems.addAll(items);
        }
        refresh();
    }

    private void refresh() {
        if (todoListView == null) return;
        String filter = todoFilterField != null && todoFilterField.getText() != null
                ? todoFilterField.getText().trim().toLowerCase()
                : "";
        java.util.List<TodoItem> visible = new java.util.ArrayList<>();
        for (TodoItem item : allItems) {
            if (filter.isEmpty()
                    || item.getMessage().toLowerCase().contains(filter)
                    || item.getFile().getName().toLowerCase().contains(filter)) {
                visible.add(item);
            }
        }
        todoListView.getItems().setAll(visible);
    }

    private void navigateToSelected() {
        TodoItem selected = todoListView.getSelectionModel().getSelectedItem();
        if (selected == null || onTodoSelectedCallback == null) return;
        onTodoSelectedCallback.accept(selected.getFile(), selected.getLine());
    }

    public void setOnTodoSelectedCallback(BiConsumer<File, Integer> callback) {
        this.onTodoSelectedCallback = callback;
    }
}