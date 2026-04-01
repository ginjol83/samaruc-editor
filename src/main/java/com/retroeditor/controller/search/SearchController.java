package com.retroeditor.controller.search;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fxmisc.richtext.CodeArea;

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

    private final com.retroeditor.model.EditorModel         editorModel;
    private final com.retroeditor.controller.MainController mainController;

    private int    lastSearchIndex = -1;
    private String lastSearchTerm  = null;
    private Stage  findPanelStage  = null;

    /**
     * Constructor del SearchController.
     * @param owner Ventana principal de la aplicación.
     * @param tabPane Pestañas del editor de texto.
     * @param tabFileMap Mapa de pestañas a archivos abiertos.
     * @param editorModel Modelo del editor de texto.
     * @param mainController Controlador principal de la aplicación.
     * @param fxUtils Utilidades para JavaFX.
     */
    public SearchController(Stage owner, TabPane tabPane, Map<Tab, File> tabFileMap,
            com.retroeditor.model.EditorModel editorModel, com.retroeditor.controller.MainController mainController, FXUtils fxUtils) {
        this.owner          = owner;
        this.fxUtils        = fxUtils;
        this.tabPane        = tabPane;
        this.tabFileMap     = tabFileMap;
        this.editorModel    = editorModel;
        this.mainController = mainController;
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

            javafx.scene.control.CheckBox chkCase  = new javafx.scene.control.CheckBox("Mayúsculas");
            javafx.scene.control.CheckBox chkRegex = new javafx.scene.control.CheckBox("Regex");

            javafx.scene.control.Button btnNext     = new javafx.scene.control.Button("Siguiente");
            javafx.scene.control.Button btnPrev     = new javafx.scene.control.Button("Anterior");
            javafx.scene.control.Button btnFindAll  = new javafx.scene.control.Button("Buscar todo");
            javafx.scene.control.Button btnClose    = new javafx.scene.control.Button("Cerrar");

            javafx.scene.layout.HBox options  = new javafx.scene.layout.HBox(8, chkCase, chkRegex);
            javafx.scene.layout.HBox actions        = new javafx.scene.layout.HBox(8, btnPrev, btnNext, btnFindAll, btnClose);
            javafx.scene.layout.VBox root           = new javafx.scene.layout.VBox(8, txt, options, actions);
            
            root.setStyle("-fx-padding:10; -fx-background-color: #f5f5f5; -fx-border-color: #ccc;");

            findPanelStage = new Stage();
            findPanelStage.initOwner(owner);
            findPanelStage.setResizable(false);
            findPanelStage.setTitle("Buscar en archivo");
            findPanelStage.setScene(new javafx.scene.Scene(root));

            // Handlers
            btnNext.setOnAction(e -> findNextInFileWithOptions(txt.getText(), chkCase.isSelected(), chkRegex.isSelected()));
            btnPrev.setOnAction(e -> findPreviousInFileWithOptions(txt.getText(), chkCase.isSelected(), chkRegex.isSelected()));
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

        String text     = area.getText();
        String hay      = text;
        String needle   = term;

        if (!caseSensitive && !regex) {
            hay = text.toLowerCase();
            needle = term.toLowerCase();
        }

        int start    = area.getSelection().getEnd();
        int idx      = -1;
        int matchEnd = -1;

        try {
            if (regex) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(term, caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);

                if (m.find(start)) {
                    idx      = m.start();
                    matchEnd = m.end();
                } else if (start > 0 && m.find(0)) {
                    idx      = m.start();
                    matchEnd = m.end();
                }
            } else {
                idx = hay.indexOf(needle, Math.max(0, start));

                if (idx < 0 && start > 0) idx = hay.indexOf(needle, 0);
                if (idx >= 0) matchEnd = idx + needle.length();
            }
        } catch (Exception ex) {
            return;
        }

        if (idx >= 0) {
            final int fidx = idx;
            final int fend = (matchEnd > idx) ? matchEnd : idx + Math.max(1, term.length());
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

        String text   = area.getText();
        String hay    = text;
        String needle = term;

        if (!caseSensitive && !regex) {
            hay    = text.toLowerCase();
            needle = term.toLowerCase();
        }

        int start = area.getSelection().getStart() - 1;

        if (start < 0) start = text.length();

        int idx      = -1;
        int matchEnd = -1;

        try {
            if (regex) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(term, caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(text);
                int last    = -1;
                int lastEnd = -1;

                while (m.find() && m.start() < start) {
                    last    = m.start();
                    lastEnd = m.end();
                }

                idx      = last;
                matchEnd = lastEnd;
            } else {
                idx = hay.lastIndexOf(needle, start);
                if (idx >= 0) matchEnd = idx + needle.length();
            }

        } catch (Exception ex) {
            return;
        }
        if (idx >= 0) {
            final int fidx = idx;
            final int fend = (matchEnd > idx) ? matchEnd : idx + Math.max(1, term.length());

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

        try {
            if (regex) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(term, caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
                while (m.find()) {
                    int len = m.end() - m.start();
                    list.add(new IndexSnippet(m.start(), len, getSnippet(text, m.start(), len)));
                }
            } else {
                String hay = caseSensitive ? text : text.toLowerCase();
                String needle = caseSensitive ? term : term.toLowerCase();
                int idx = 0;
                while ((idx = hay.indexOf(needle, idx)) >= 0) {
                    list.add(new IndexSnippet(idx, term.length(), getSnippet(text, idx, term.length())));
                    idx += Math.max(1, term.length());
                }
            }
        } catch (Exception ex) {
            // ignore
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
            dialog.setTitle("Buscar en proyecto");

            TextField txt = new TextField();
            txt.setPromptText("Texto a buscar en el proyecto");
            txt.setMinWidth(400);

            ButtonType search = new ButtonType("Buscar", ButtonBar.ButtonData.OK_DONE);
            ButtonType close  = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

            dialog.getDialogPane().getButtonTypes().addAll(search, close);
            dialog.getDialogPane().setContent(txt);

            Optional<ButtonType> result = dialog.showAndWait();

            if (result.isPresent() && result.get() == search) {
                String term = txt.getText();

                if (term != null && !term.trim().isEmpty()) {
                    List<SearchMatch> matches = searchProject(term.trim());
                    showProjectSearchResults(matches, term.trim());
                }
            }
        });
    }

    /**
     * Busca el término en todos los archivos del proyecto actual.
     * @param term Término a buscar.
     * @return Lista de coincidencias encontradas.
     */
    private List<SearchMatch> searchProject(String term) {
        List<SearchMatch> matches = new ArrayList<>();
        File root = mainController.getCurrentProjectDir();

        if (root == null || !root.isDirectory()) return matches;

        try (Stream<Path> paths = Files.walk(root.toPath())) {
            List<Path> files = paths.filter(Files::isRegularFile).collect(Collectors.toList());

            for (Path p : files) {
                try {
                    String content = Files.readString(p);
                    int idx = content.indexOf(term);

                    if (idx >= 0) {
                        String snippet = getSnippet(content, idx, term.length());
                        matches.add(new SearchMatch(p.toFile(), idx, snippet));
                    }

                } catch (IOException ex) { }
            }
        } catch (IOException e) { }
        
        return matches;
    }

    /**
     * Obtiene un fragmento de texto alrededor de una coincidencia.
     * @param content Contenido completo del texto.
     * @param idx Índice donde se encontró la coincidencia.
     * @param length Longitud del término buscado.
     * @return Fragmento de texto alrededor de la coincidencia.
     */
    private String getSnippet(String content, int idx, int length) {
        int start = Math.max(0, idx - 30);
        int end   = Math.min(content.length(), idx + length + 30);

        return content.substring(start, end).replaceAll("\r?\n", " ");
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
                    mainController.openFileFromExplorer(sel.file);

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

