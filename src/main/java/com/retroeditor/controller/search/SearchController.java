package com.retroeditor.controller.search;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.fxmisc.richtext.CodeArea;

import com.retroeditor.service.ProjectSearchService;
import com.retroeditor.service.ProjectSearchService.SearchOptions;
import com.retroeditor.service.TextSearchService;
import com.retroeditor.service.UserActionMonitor;
import com.retroeditor.util.FXUtils;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

/**
 * SearchController: responsable de buscar texto dentro del archivo abierto y en todo el proyecto.
 */
public class SearchController {
    private final Stage          owner;
    private final TabPane        tabPane;
    private final FXUtils        fxUtils;
    private final Map<Tab, File> tabFileMap;

    private final com.retroeditor.model.EditorModel editorModel;
    private final ProjectContextPort projectContextPort;
    private final FileOpenPort fileOpenPort;
    private final ProjectSearchService projectSearchService;
    private final TextSearchService textSearchService;

    private int    lastSearchIndex = -1;
    private String lastSearchTerm  = null;
    private Stage  findPanelStage  = null;

    /**
     * Constructor del SearchController.
     * @param owner Ventana principal de la aplicación.
     * @param tabPane Pestañas del editor de texto.
     * @param tabFileMap Mapa de pestañas a archivos abiertos.
     * @param editorModel Modelo del editor de texto.
     * @param fxUtils Utilidades para JavaFX.
     */
    public SearchController(Stage owner, TabPane tabPane, Map<Tab, File> tabFileMap,
            com.retroeditor.model.EditorModel editorModel, ProjectContextPort projectContextPort, FileOpenPort fileOpenPort,
            ProjectSearchService projectSearchService, TextSearchService textSearchService, FXUtils fxUtils) {
        this.owner                  = owner;
        this.fxUtils                = fxUtils;
        this.tabPane                = tabPane;
        this.tabFileMap             = tabFileMap;
        this.editorModel            = editorModel;
        this.fileOpenPort           = fileOpenPort;
        this.textSearchService      = textSearchService;
        this.projectContextPort     = projectContextPort;
        this.projectSearchService   = projectSearchService;
    }


    /**
     * Muestra el panel de búsqueda en el archivo actual.
     */
    public void showFindInFileDialog() {
        UserActionMonitor.findInFileDialogOpened();
        Platform.runLater(() -> {
            // Siempre mostrar el Stage flotante para el panel de búsqueda (UX consistente / preferencia del usuario)
            if (findPanelStage != null && findPanelStage.isShowing()) {
                findPanelStage.toFront();
                return;
            }

            TextField txt = new TextField();
            txt.setPromptText("Texto a buscar");
            txt.setMinWidth(300);

            TextField txtReplace = new TextField();
            txtReplace.setPromptText("Texto a reemplazar");
            txtReplace.setMinWidth(300);

            javafx.scene.control.CheckBox chkCase   = new javafx.scene.control.CheckBox("Mayúsculas");
            javafx.scene.control.CheckBox chkRegex  = new javafx.scene.control.CheckBox("Regex");

            javafx.scene.control.Button btnNext     = new javafx.scene.control.Button("Siguiente");
            javafx.scene.control.Button btnPrev     = new javafx.scene.control.Button("Anterior");
            javafx.scene.control.Button btnReplace  = new javafx.scene.control.Button("Reemplazar");
            javafx.scene.control.Button btnReplaceAll = new javafx.scene.control.Button("Reemplazar todo");
            javafx.scene.control.Button btnFindAll  = new javafx.scene.control.Button("Buscar todo");
            javafx.scene.control.Button btnClose    = new javafx.scene.control.Button("Cerrar");

            javafx.scene.layout.HBox options        = new javafx.scene.layout.HBox(8, chkCase, chkRegex);
            javafx.scene.layout.HBox actions        = new javafx.scene.layout.HBox(8, btnPrev, btnNext, btnReplace, btnReplaceAll, btnFindAll, btnClose);
            javafx.scene.layout.VBox root           = new javafx.scene.layout.VBox(8, txt, txtReplace, options, actions);
            
            root.setStyle("-fx-padding:10; -fx-background-color: #f5f5f5; -fx-border-color: #ccc;");

            findPanelStage = new Stage();
            findPanelStage.initOwner(owner);
            findPanelStage.setResizable(false);
            findPanelStage.setTitle("Buscar en archivo");
            findPanelStage.setScene(new javafx.scene.Scene(root));

            // Handlers
            btnNext.setOnAction(e -> findNextInFileWithOptions(txt.getText(), chkCase.isSelected(), chkRegex.isSelected()));
            btnPrev.setOnAction(e -> findPreviousInFileWithOptions(txt.getText(), chkCase.isSelected(), chkRegex.isSelected()));
            btnReplace.setOnAction(e -> replaceNextInFileWithOptions(txt.getText(), txtReplace.getText(), chkCase.isSelected(), chkRegex.isSelected()));
            btnReplaceAll.setOnAction(e -> replaceAllInFileWithOptions(txt.getText(), txtReplace.getText(), chkCase.isSelected(), chkRegex.isSelected()));
            btnFindAll.setOnAction(e -> {
                String term = txt.getText();
                List<IndexSnippet> all = findAllInFile(term, chkCase.isSelected(), chkRegex.isSelected());
                showFileSearchResults(all, term);
            });
            btnClose.setOnAction(e -> findPanelStage.close());

            // busqueda incremental: cuando el texto cambia, saltar a la primera coincidencia
            txt.textProperty().addListener((obs, oldV, newV) -> {
                if (newV == null || newV.trim().isEmpty()) return;
                findNextInFileWithOptions(newV, chkCase.isSelected(), chkRegex.isSelected());
            });

            // Registrar aceleradores F3 / Shift+F3 
            if (owner != null && owner.getScene() != null) {
                owner.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.F3), () -> findNextInFileWithOptions(txt.getText(), chkCase.isSelected(), chkRegex.isSelected()));
                owner.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN), () -> findPreviousInFileWithOptions(txt.getText(), chkCase.isSelected(), chkRegex.isSelected()));
            }

            findPanelStage.show();
        });
    }

    /** 
     * Obtiene el CodeArea del archivo actualmente activo en el editor.
     * @return CodeArea del archivo activo.
     */
    private CodeArea getCurrentCodeArea() {
        return fxUtils.getCurrentCodeArea(tabPane);
    }

    /** 
     * Busca la siguiente ocurrencia del término en el archivo actual.
     * @param term Término a buscar.
     */
    private void findNextInFile(String term) {
        if (term == null || term.isEmpty()) return;

        CodeArea area = getCurrentCodeArea();

        if (area == null) return;

        String text = area.getText();
        int start   = area.getSelection().getEnd();
        int idx     = text.indexOf(term, Math.max(0, start));

        if (idx < 0 && start > 0) {
            // wrap
            idx = text.indexOf(term, 0);
        }

        if (idx >= 0) {
            final int fidx = idx;
            Platform.runLater(() -> {
                area.requestFocus();
                area.selectRange(fidx, fidx + term.length());
                lastSearchIndex = fidx;
                lastSearchTerm = term;
            });
        }
    }

    /** 
     * Busca la ocurrencia anterior del término en el archivo actual.
     * @param term Término a buscar.
     */
    private void findPreviousInFile(String term) {
        if (term == null || term.isEmpty()) return;

        CodeArea area = getCurrentCodeArea();

        if (area == null) return;

        String text  = area.getText();
        int    start = area.getSelection().getStart() - 1;

        if (start < 0) start = text.length();

        int idx = text.lastIndexOf(term, start);

        if (idx >= 0) {
            final int fidx = idx;
            Platform.runLater(() -> {
                area.requestFocus();
                area.selectRange(fidx, fidx + term.length());
                lastSearchIndex = fidx;
                lastSearchTerm = term;
            });
        }
    }

    /** 
     * Reemplaza la siguiente ocurrencia del término en el archivo actual.
     * @param term Término a buscar.
     * @param replacement Texto de reemplazo.
     */
    private void replaceNextInFile(String term, String replacement) {
        CodeArea area = getCurrentCodeArea();
        if (area == null || term == null || term.isEmpty()) return;

        String text     = area.getText();
        int    start    = area.getSelection().getEnd();
        int    idx      = text.indexOf(term, Math.max(0, start));

        if (idx < 0) idx = text.indexOf(term, 0);

        if (idx >= 0) {
            final int fidx = idx;
            Platform.runLater(() -> {
                area.replaceText(fidx, fidx + term.length(), replacement);
            });
        }
    }

    private void replaceNextInFileWithOptions(String term, String replacement, boolean caseSensitive, boolean regex) {
        CodeArea area = getCurrentCodeArea();
        if (area == null || term == null || term.isEmpty()) return;

        int start = area.getSelection().getEnd();
        String updated = textSearchService.replaceFirst(area.getText(), term, replacement, start, caseSensitive, regex);
        if (!updated.equals(area.getText())) {
            Platform.runLater(() -> area.replaceText(updated));
        }
    }

    /**
     * Reemplaza todas las ocurrencias del término en el archivo actual.
     * @param term Término a buscar.
     * @param replacement Texto de reemplazo.
     */
    private void replaceAllInFile(String term, String replacement) {
        CodeArea area = getCurrentCodeArea();

        if (area == null || term == null || term.isEmpty()) return;

        String text     = area.getText();
        String replaced = text.replace(term, replacement);
        final  String r = replaced;

        Platform.runLater(() -> {
            area.replaceText(r);
        });
    }

    private void replaceAllInFileWithOptions(String term, String replacement, boolean caseSensitive, boolean regex) {
        CodeArea area = getCurrentCodeArea();
        if (area == null || term == null || term.isEmpty()) return;

        String updated = textSearchService.replaceAll(area.getText(), term, replacement, caseSensitive, regex);
        if (!updated.equals(area.getText())) {
            Platform.runLater(() -> area.replaceText(updated));
        }
    }

    /**
     * Busca la siguiente ocurrencia del término en el archivo actual.
     * @param term Término a buscar.
     * @param caseSensitive Indica si la búsqueda distingue entre mayúsculas y minúsculas.
     * @param regex Indica si el término es una expresión regular.
     */
    private void findNextInFileWithOptions(String term, boolean caseSensitive, boolean regex) {
        if (term == null || term.isEmpty()) return;

        CodeArea area = getCurrentCodeArea();

        if (area == null) return;

        String text = area.getText();
        int start = area.getSelection().getEnd();
        TextSearchService.MatchRange next = textSearchService.findNext(text, term, start, caseSensitive, regex);

        if (next != null) {
            final int fidx = next.getStart();
            final int fend = Math.max(next.getEnd(), fidx + Math.max(1, term.length()));
            Platform.runLater(() -> {
                area.requestFocus();
                area.selectRange(fidx, Math.min(area.getLength(), fend));
                lastSearchIndex = fidx;
                lastSearchTerm = term;
            });
        }
    }

    /** 
     * Busca la ocurrencia anterior del término en el archivo actual.
     * @param term Término a buscar.
     * @param caseSensitive Indica si la búsqueda distingue entre mayúsculas y minúsculas.
     * @param regex Indica si el término es una expresión regular.
     */
    private void findPreviousInFileWithOptions(String term, boolean caseSensitive, boolean regex) {
        if (term == null || term.isEmpty()) return;

        CodeArea area = getCurrentCodeArea();

        if (area == null) return;

        String text = area.getText();
        int start = area.getSelection().getStart() - 1;
        TextSearchService.MatchRange prev = textSearchService.findPrevious(text, term, start, caseSensitive, regex);
        if (prev != null) {
            final int fidx = prev.getStart();
            final int fend = Math.max(prev.getEnd(), fidx + Math.max(1, term.length()));

            Platform.runLater(() -> {
                area.requestFocus();
                area.selectRange(fidx, Math.min(area.getLength(), fend));
                lastSearchIndex = fidx;
                lastSearchTerm  = term;
            });
        }
    }

    /** 
     * Encuentra todas las ocurrencias del término en el archivo actual.
     * @param term Término a buscar.
     * @param caseSensitive Indica si la búsqueda distingue entre mayúsculas y minúsculas.
     * @param regex Indica si el término es una expresión regular.
     * @return Lista de fragmentos encontrados.
     */
    private List<IndexSnippet> findAllInFile(String term, boolean caseSensitive, boolean regex) {
        List<IndexSnippet> list = new ArrayList<>();

        if (term == null || term.isEmpty()) return list;

        CodeArea area = getCurrentCodeArea();

        if (area == null) return list;

        String text = area.getText();
        for (TextSearchService.SnippetMatch match : textSearchService.findAll(text, term, caseSensitive, regex)) {
            list.add(new IndexSnippet(match.getStart(), match.getLength(), match.getSnippet()));
        }

        return list;
    }


    /** 
     * Clase que obtiene un fragmento de texto alrededor de una coincidencia.
     */
    private static class IndexSnippet { 
        final int    index;
        final int    matchLength;
        final String snippet; 

        IndexSnippet(int i, int len, String s) {
            index       = i;
            matchLength = len;
            snippet     = s;
        } 
    }


    /**
     * Muestra los resultados de la búsqueda en el archivo actual.
     * @param matches Lista de fragmentos encontrados.
     * @param term Término buscado.
     */
    private void showFileSearchResults(List<IndexSnippet> matches, String term) {
        Platform.runLater(() -> {

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(owner);
            dialog.setTitle("Resultados en archivo: '" + term + "'");

            ListView<IndexSnippet> list = new ListView<>(FXCollections.observableArrayList(matches));

            list.setCellFactory(lv -> new ListCell<>(){
                @Override
                protected void updateItem(IndexSnippet item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) setText(null); else setText(item.index + " — " + item.snippet);
                }
            });

            ButtonType open  = new ButtonType("Ir", ButtonBar.ButtonData.OK_DONE);
            ButtonType close = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

            dialog.getDialogPane().getButtonTypes().addAll(open, close);
            dialog.getDialogPane().setContent(list);

            Optional<ButtonType> res = dialog.showAndWait();

            if (res.isPresent() && res.get() == open) {
                IndexSnippet sel = list.getSelectionModel().getSelectedItem();

                if (sel != null) {
                    CodeArea area = getCurrentCodeArea();

                    if (area != null) {
                        int idx = sel.index;
                        area.requestFocus();
                        area.selectRange(idx, Math.min(area.getLength(), idx + sel.matchLength));
                    }
                }
            }
        });
    }

    /**
     * Muestra el diálogo de búsqueda en todo el proyecto.
     */
    public void showFindInProjectDialog() {
        UserActionMonitor.findInProjectDialogOpened();

        Platform.runLater(() -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(owner);
            dialog.setTitle("Búsqueda Avanzada en Proyecto");

            TextField txt = new TextField();
            txt.setPromptText("Texto a buscar");
            txt.setMinWidth(400);

            TextField txtReplace = new TextField();
            txtReplace.setPromptText("Texto a reemplazar");
            txtReplace.setMinWidth(400);

            TextField txtTypes = new TextField();
            txtTypes.setPromptText("Tipos de archivo (ej: .c, .asm)");
            txtTypes.setMinWidth(400);

            javafx.scene.control.DatePicker datePicker = new javafx.scene.control.DatePicker();
            datePicker.setPromptText("Modificado después de...");
            datePicker.setMinWidth(400);

            javafx.scene.control.CheckBox chkCase  = new javafx.scene.control.CheckBox("Mayúsculas");
            javafx.scene.control.CheckBox chkRegex = new javafx.scene.control.CheckBox("Regex");

            ButtonType search = new ButtonType("Buscar", ButtonBar.ButtonData.OK_DONE);
            ButtonType replaceAll = new ButtonType("Reemplazar todo", ButtonBar.ButtonData.YES);
            ButtonType close  = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

            dialog.getDialogPane().getButtonTypes().addAll(search, replaceAll, close);
            
            javafx.scene.layout.VBox layout = new javafx.scene.layout.VBox(10);
            layout.getChildren().addAll(
                new javafx.scene.control.Label("Texto a buscar:"), txt,
                new javafx.scene.control.Label("Reemplazar por (opcional):"), txtReplace,
                new javafx.scene.control.Label("Extensiones (separadas por coma):"), txtTypes,
                new javafx.scene.control.Label("Fecha de modificación:"), datePicker,
                new javafx.scene.layout.HBox(10, chkCase, chkRegex)
            );
            
            dialog.getDialogPane().setContent(layout);

            Optional<ButtonType> result = dialog.showAndWait();

            if (result.isPresent() && (result.get() == search || result.get() == replaceAll)) {
                String term = txt.getText();
                String replacement = txtReplace.getText();
                String types = txtTypes.getText();
                LocalDate date = datePicker.getValue();

                if (term != null && !term.trim().isEmpty()) {
                    SearchOptions options = new SearchOptions(term.trim(), types, date, chkCase.isSelected(), chkRegex.isSelected());
                    
                    if (result.get() == search) {
                        List<SearchMatch> matches = searchProjectWithOptions(options);
                        showProjectSearchResults(matches, term.trim());
                    } else {
                        replaceAllInProjectWithOptions(term.trim(), replacement, options);
                    }
                }
            }
        });
    }

    private List<SearchMatch> searchProjectWithOptions(SearchOptions options) {
        File root = projectContextPort != null ? projectContextPort.getCurrentProjectDir() : null;
        List<SearchMatch> matches = new ArrayList<>();

        for (ProjectSearchService.Match match : projectSearchService.searchProject(root, options)) {
            matches.add(new SearchMatch(match.getFile(), match.getIndex(), match.getSnippet()));
        }

        return matches;
    }

    private void replaceAllInProjectWithOptions(String term, String replacement, SearchOptions options) {
        File root = projectContextPort != null ? projectContextPort.getCurrentProjectDir() : null;
        if (root == null || !root.isDirectory()) return;

        int updatedFiles = projectSearchService.replaceInProject(root, term, replacement, options);
        if (updatedFiles > 0) {
            refreshOpenTabsFromDisk(root);
        }

        UserActionMonitor.searchExecuted(term, "project-replace");
    }

    /**
     * Busca el término en todos los archivos del proyecto actual.
     * @param term Término a buscar.
     * @return Lista de coincidencias encontradas.
     */
    private List<SearchMatch> searchProject(String term) {
        File root = projectContextPort != null ? projectContextPort.getCurrentProjectDir() : null;
        List<SearchMatch> matches = new ArrayList<>();

        for (ProjectSearchService.Match match : projectSearchService.searchProject(root, term)) {
            matches.add(new SearchMatch(match.getFile(), match.getIndex(), match.getSnippet()));
        }

        return matches;
    }

    private void replaceAllInProject(String term, String replacement, boolean caseSensitive, boolean regex) {
        File root = projectContextPort != null ? projectContextPort.getCurrentProjectDir() : null;
        if (root == null || !root.isDirectory()) return;

        int updatedFiles = projectSearchService.replaceInProject(root, term, replacement, caseSensitive, regex);
        if (updatedFiles > 0) {
            refreshOpenTabsFromDisk(root);
        }

        UserActionMonitor.searchExecuted(term, "project-replace");
    }

    private void refreshOpenTabsFromDisk(File projectRoot) {
        if (projectRoot == null || tabFileMap == null) return;

        for (Map.Entry<Tab, File> entry : tabFileMap.entrySet()) {
            File mappedFile = entry.getValue();
            if (mappedFile == null || !isInsideProject(projectRoot, mappedFile)) continue;

            try {
                editorModel.openFile(mappedFile);
                String content = editorModel.getFileContent(mappedFile);
                CodeArea area = fxUtils.getCodeArea(entry.getKey());
                if (area != null) {
                    Tab tab = entry.getKey();
                    Platform.runLater(() -> {
                        area.replaceText(content);
                        fxUtils.markTabSaved(tab, mappedFile.getName(), content);
                    });
                }
            } catch (IOException ex) {
                UserActionMonitor.errorOccurred("PROJECT_REPLACE_REFRESH", ex.getMessage());
            }
        }
    }

    private boolean isInsideProject(File projectRoot, File file) {
        if (projectRoot == null || file == null) return false;
        try {
            return file.getAbsoluteFile().toPath().normalize().startsWith(projectRoot.getAbsoluteFile().toPath().normalize());
        } catch (Exception ex) {
            return false;
        }
    }


    /**
     * Muestra los resultados de la búsqueda en un diálogo.
     * @param matches Lista de coincidencias encontradas.
     * @param term Término buscado.
     */
    private void showProjectSearchResults(List<SearchMatch> matches, String term) {

        Platform.runLater(() -> {
            Dialog<ButtonType> dialog = new Dialog<>();

            dialog.initOwner(owner);
            dialog.setTitle("Resultados: '" + term + "'");

            ListView<SearchMatch> list = new ListView<>(FXCollections.observableArrayList(matches));

            list.setCellFactory(lv -> new ListCell<>(){
                @Override
                protected void updateItem(SearchMatch item, boolean empty) {

                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.file.getPath() + " — " + item.snippet);
                    }
                }
            });

            ButtonType open  = new ButtonType("Abrir" , ButtonBar.ButtonData.OK_DONE);
            ButtonType close = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

            dialog.getDialogPane().getButtonTypes().addAll(open, close);
            dialog.getDialogPane().setContent(list);

            Optional<ButtonType> res = dialog.showAndWait();

            if (res.isPresent() && res.get() == open) {
                SearchMatch sel = list.getSelectionModel().getSelectedItem();

                if (sel != null) {
                    if (fileOpenPort != null) {
                        fileOpenPort.openFileFromExplorer(sel.file);
                    }

                    Platform.runLater(() -> {

                        CodeArea area = getCurrentCodeArea();

                        if (area != null) {
                            int idx = sel.index;
                            area.requestFocus();
                            area.selectRange(idx, Math.min(area.getLength(), idx + term.length()));
                        }
                    });
                }
            }
        });
    }

    /**
     * Coincidencia de búsqueda en proyecto.
     */
    private static class SearchMatch {
        final File   file;
        final int    index;
        final String snippet;

        SearchMatch(File file, int index, String snippet) {
            this.file = file;
            this.index = index;
            this.snippet = snippet;
        }
    }

}
