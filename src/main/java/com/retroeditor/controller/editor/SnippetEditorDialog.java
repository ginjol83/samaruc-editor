package com.retroeditor.controller.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import com.retroeditor.service.SnippetService.NamedSnippet;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Diálogo de definición de snippets (item 15): lista los snippets existentes y
 * permite añadir, editar o eliminar. La plantilla admite tabstops
 * {@code $1}..{@code $9}, {@code ${n:default}} y la posición final {@code $0}.
 *
 * <p>Al cerrar con un botón de confirmación (Insertar / Cerrar), la lista de
 * snippets pasada se sustituye por la editada en memoria —el llamante la
 * persiste—; {@code Cancelar} descarta los cambios. El valor devuelto es el
 * snippet elegido para insertar cuando {@code chooseToInsert} es {@code true}.</p>
 */
public class SnippetEditorDialog {

    private SnippetEditorDialog() {
    }

    /** Resultado interno: si se confirmó y, en su caso, el snippet elegido. */
    private record Outcome(boolean confirmed, NamedSnippet chosen) {}

    /**
     * @param current        lista de snippets actual; se reemplaza al confirmar.
     * @param chooseToInsert true → botón principal "Insertar" (devuelve la selección);
     *                       false → botón "Cerrar" (solo gestión, devuelve vacío).
     * @return el snippet elegido para insertar, o vacío (también si solo se gestionó).
     */
    public static Optional<NamedSnippet> show(Window owner,
                                              List<String> stylesheets,
                                              ResourceBundle bundle,
                                              List<NamedSnippet> current,
                                              boolean chooseToInsert) {
        List<NamedSnippet> working = new ArrayList<>(current != null ? current : List.of());

        ListView<NamedSnippet> list = new ListView<>();
        list.getItems().setAll(working);
        list.setPrefSize(420, 240);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(NamedSnippet item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });

        Button btnAdd = new Button(tr(bundle, "snippet.dialog.add", "Añadir"));
        Button btnEdit = new Button(tr(bundle, "snippet.dialog.edit", "Editar"));
        Button btnDelete = new Button(tr(bundle, "snippet.dialog.delete", "Eliminar"));
        Label hint = new Label(tr(bundle, "snippet.dialog.hint",
            "Tabstops: $1..$9, ${1:valor por defecto}, $0 (posición final), $$ (dólar literal)."));
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: gray;");

        VBox root = new VBox(8, list, new HBox(8, btnAdd, btnEdit, btnDelete), hint);
        root.setPadding(new Insets(10));

        Dialog<Outcome> dialog = new Dialog<>();
        dialog.setTitle(tr(bundle, "snippet.dialog.title", "Snippets"));
        dialog.initOwner(owner);
        if (stylesheets != null && !stylesheets.isEmpty()) {
            dialog.getDialogPane().getStylesheets().setAll(stylesheets);
        }

        ButtonType confirmType = chooseToInsert
            ? new ButtonType(tr(bundle, "snippet.dialog.insert", "Insertar"), ButtonBar.ButtonData.OK_DONE)
            : new ButtonType(tr(bundle, "snippet.dialog.close", "Cerrar"), ButtonBar.ButtonData.FINISH);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(root);

        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirmType);
        confirmButton.setDefaultButton(true);
        if (chooseToInsert) {
            confirmButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        }

        dialog.setResultConverter(button -> button == confirmType
            ? new Outcome(true, chooseToInsert ? list.getSelectionModel().getSelectedItem() : null)
            : null);

        btnAdd.setOnAction(e -> showSnippetForm(owner, stylesheets, bundle, null).ifPresent(added -> {
            working.add(added);
            list.getItems().setAll(working);
            list.getSelectionModel().select(added);
        }));
        btnEdit.setOnAction(e -> {
            NamedSnippet selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            int index = list.getSelectionModel().getSelectedIndex();
            showSnippetForm(owner, stylesheets, bundle, selected).ifPresent(edited -> {
                working.set(index, edited);
                list.getItems().setAll(working);
                list.getSelectionModel().select(edited);
            });
        });
        btnDelete.setOnAction(e -> {
            int index = list.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                working.remove(index);
                list.getItems().setAll(working);
                list.getSelectionModel().clearSelection();
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null
                && chooseToInsert) {
                confirmButton.fire();
            }
        });

        Optional<Outcome> outcome = dialog.showAndWait();
        outcome.filter(Outcome::confirmed).ifPresent(o -> {
            if (current != null) {
                current.clear();
                current.addAll(working);
            }
        });
        return outcome.filter(Outcome::confirmed).map(Outcome::chosen);
    }

    /** Formulario de alta/edición de un snippet. Devuelve vacío si se cancela. */
    private static Optional<NamedSnippet> showSnippetForm(Window owner,
                                                          List<String> stylesheets,
                                                          ResourceBundle bundle,
                                                          NamedSnippet existing) {
        TextField name = new TextField(existing != null ? existing.name() : "");
        name.setPromptText(tr(bundle, "snippet.dialog.name.prompt", "Nombre (p.ej. for)"));
        TextArea template = new TextArea(existing != null ? existing.template() : "");
        template.setPromptText(tr(bundle, "snippet.dialog.template.prompt",
            "Plantilla con tabstops, p.ej.: for (int i = 0; i < ${1:n}; i++) {\\n\\t${0}\\n}"));
        template.setPrefRowCount(8);
        template.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.add(new Label(tr(bundle, "snippet.dialog.name", "Nombre:")), 0, 0);
        grid.add(name, 1, 0);
        grid.add(new Label(tr(bundle, "snippet.dialog.template", "Plantilla:")), 0, 1);
        grid.add(template, 1, 1);
        GridPane.setHgrow(template, Priority.ALWAYS);
        GridPane.setVgrow(template, Priority.ALWAYS);

        Dialog<NamedSnippet> dialog = new Dialog<>();
        dialog.setTitle(existing != null
            ? tr(bundle, "snippet.dialog.edit.title", "Editar snippet")
            : tr(bundle, "snippet.dialog.add.title", "Nuevo snippet"));
        dialog.initOwner(owner);
        if (stylesheets != null && !stylesheets.isEmpty()) {
            dialog.getDialogPane().getStylesheets().setAll(stylesheets);
        }
        ButtonType saveType = new ButtonType(tr(bundle, "snippet.dialog.save", "Guardar"),
            ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.disableProperty().bind(
            name.textProperty().isEmpty().or(template.textProperty().isEmpty()));

        dialog.setResultConverter(button -> button == saveType
            ? new NamedSnippet(name.getText().trim(), template.getText())
            : null);
        return dialog.showAndWait();
    }

    private static String tr(ResourceBundle bundle, String key, String fallback) {
        if (bundle == null || !bundle.containsKey(key)) return fallback;
        return bundle.getString(key);
    }
}