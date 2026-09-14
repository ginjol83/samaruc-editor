package com.retroeditor.controller.tools;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.retroeditor.service.github.*;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/**
 * Explorador de repositorios de GitHub integrado en Samaruc.
 * Búsqueda en tiempo real, catálogo curado y gestión de repos clonados.
 */
public class GitHubRepoBrowserController {

    private final GitHubApiService apiService = new GitHubApiService();
    private final GitHubCloneService cloneService = new GitHubCloneService();
    private final RepoCatalogService catalogService = new RepoCatalogService();

    private Node rootNode;
    private ResourceBundle bundle;
    private Consumer<File> onCloned;
    private Runnable closeHandler;

    // UI refs
    private ProgressIndicator progressIndicator;
    private Label lblStatus;
    private ListView<GitHubRepo> searchResultsList;
    private ListView<CuratedRepo> curatedList;
    private ListView<Path> clonedReposList;
    private TextField txtSearch;
    private TextField txtUrl;
    private ComboBox<String> comboCategory;

    private final ObservableList<GitHubRepo> searchResults = FXCollections.observableArrayList();
    private final ObservableList<CuratedRepo> curatedResults = FXCollections.observableArrayList();
    private final ObservableList<Path> clonedRepos = FXCollections.observableArrayList();

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
        "https?://(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+)(?:/.*)?");

    public Node createNode(File currentProjectDir, ResourceBundle bundle, Consumer<File> onCloned) {
        this.bundle = bundle;
        this.onCloned = onCloned;
        return buildUi();
    }

    /**
     * Registra la acción a ejecutar cuando se cierra la herramienta (pestaña o ventana).
     */
    public void setOnCloseRequest(Runnable onClose) {
        this.closeHandler = onClose;
    }

    private void requestClose() {
        if (closeHandler != null) {
            closeHandler.run();
        }
    }

    private Node buildUi() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));

        root.setTop(createHeader());
        root.setCenter(createTabPane());
        root.setBottom(createBottomBar());
        rootNode = root;

        return root;
    }

    private Window currentWindow() {
        if (rootNode != null && rootNode.getScene() != null) {
            return rootNode.getScene().getWindow();
        }
        return null;
    }

    private VBox createHeader() {
        VBox header = new VBox(6);
        header.setPadding(new Insets(0, 0, 8, 0));

        // URL bar
        Label lblUrl = new Label(msg("tool.github.pasteUrl", "Pegar URL de GitHub:"));
        txtUrl = new TextField();
        txtUrl.setPromptText("https://github.com/owner/repo");
        txtUrl.setPrefWidth(400);

        Button btnCloneUrl = new Button(msg("tool.github.cloneUrl", "Clonar"));
        btnCloneUrl.setOnAction(e -> cloneFromUrl());

        HBox urlRow = new HBox(8, lblUrl, txtUrl, btnCloneUrl);
        urlRow.setAlignment(Pos.CENTER_LEFT);

        // Progress
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);

        lblStatus = new Label();
        lblStatus.setId("githubStatusLabel");

        HBox statusRow = new HBox(8, progressIndicator, lblStatus);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        header.getChildren().addAll(urlRow, statusRow);
        return header;
    }

    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: Búsqueda
        Tab tabSearch = new Tab(msg("tool.github.search", "Buscar"));
        tabSearch.setContent(createSearchTab());

        // Tab 2: Catálogo curado
        Tab tabCatalog = new Tab(msg("tool.github.curated", "Catálogo Curado"));
        tabCatalog.setContent(createCatalogTab());

        // Tab 3: Mis repos
        Tab tabMyRepos = new Tab(msg("tool.github.myRepos", "Mis Repos"));
        tabMyRepos.setContent(createMyReposTab());

        tabPane.getTabs().addAll(tabSearch, tabCatalog, tabMyRepos);
        return tabPane;
    }

    private VBox createSearchTab() {
        VBox panel = new VBox(6);

        txtSearch = new TextField();
        txtSearch.setPromptText(msg("tool.github.searchPrompt", "Ej: gameboy gbdk platformer..."));
        txtSearch.setOnAction(e -> doSearch());

        Button btnSearch = new Button(msg("tool.github.searchBtn", "Buscar"));
        btnSearch.setOnAction(e -> doSearch());

        HBox searchRow = new HBox(8, txtSearch, btnSearch);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(txtSearch, Priority.ALWAYS);

        searchResultsList = new ListView<>(searchResults);
        searchResultsList.setCellFactory(list -> new RepoCell());

        panel.getChildren().addAll(searchRow, searchResultsList);
        VBox.setVgrow(searchResultsList, Priority.ALWAYS);
        return panel;
    }

    private VBox createCatalogTab() {
        VBox panel = new VBox(6);

        comboCategory = new ComboBox<>();
        comboCategory.getItems().add(msg("tool.github.allCategories", "Todas"));
        try {
            List<String> categories = catalogService.getCategories();
            for (String cat : categories) {
                comboCategory.getItems().add(msg("tool.github.category." + cat, cat));
            }
        } catch (Exception ignored) { }
        comboCategory.setValue(msg("tool.github.allCategories", "Todas"));
        comboCategory.setOnAction(e -> filterCatalog());

        Label lblFilter = new Label(msg("tool.github.filterByCategory", "Filtrar por categoría:"));
        HBox filterRow = new HBox(8, lblFilter, comboCategory);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        curatedList = new ListView<>(curatedResults);
        curatedList.setCellFactory(list -> new CuratedRepoCell());

        Button btnLoadCatalog = new Button(msg("tool.github.loadCatalog", "Cargar catálogo"));
        btnLoadCatalog.setOnAction(e -> loadCatalog());

        panel.getChildren().addAll(filterRow, btnLoadCatalog, curatedList);
        VBox.setVgrow(curatedList, Priority.ALWAYS);
        return panel;
    }

    private VBox createMyReposTab() {
        VBox panel = new VBox(6);

        clonedReposList = new ListView<>(clonedRepos);
        clonedReposList.setCellFactory(list -> new ClonedRepoCell());

        Button btnRefresh = new Button(msg("tool.github.refresh", "Actualizar"));
        btnRefresh.setOnAction(e -> refreshClonedRepos());

        Button btnOpenDir = new Button(msg("tool.github.openDir", "Abrir carpeta"));
        btnOpenDir.setOnAction(e -> openClonedDir());

        HBox btnRow = new HBox(8, btnRefresh, btnOpenDir);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        panel.getChildren().addAll(btnRow, clonedReposList);
        VBox.setVgrow(clonedReposList, Priority.ALWAYS);

        refreshClonedRepos();
        return panel;
    }

    private HBox createBottomBar() {
        Button btnClose = new Button(msg("button.close", "Cerrar"));
        btnClose.setOnAction(e -> requestClose());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, spacer, btnClose);
        bar.setPadding(new Insets(8, 0, 0, 0));
        bar.setAlignment(Pos.CENTER_RIGHT);
        return bar;
    }

    // === Actions ===

    private void doSearch() {
        String query = txtSearch.getText();
        if (query == null || query.isBlank()) return;

        showProgress(true);
        lblStatus.setText(msg("tool.github.searching", "Buscando..."));

        Thread t = new Thread(() -> {
            try {
                List<GitHubRepo> results = apiService.searchRepos(query, 1, 20);
                Platform.runLater(() -> {
                    searchResults.clear();
                    searchResults.addAll(results);
                    lblStatus.setText(String.format(msg("tool.github.found", "%d resultados"), results.size()));
                    showProgress(false);
                });
            } catch (GitHubApiService.RateLimitException ex) {
                Platform.runLater(() -> {
                    lblStatus.setText(msg("tool.github.rateLimit", "Límite de peticiones alcanzado (60/h)"));
                    showProgress(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lblStatus.setText(msg("tool.github.searchError", "Error: ") + ex.getMessage());
                    showProgress(false);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void loadCatalog() {
        try {
            curatedResults.clear();
            curatedResults.addAll(catalogService.loadCatalog());
            lblStatus.setText(String.format(msg("tool.github.catalogLoaded", "%d repos en catálogo"), curatedResults.size()));
        } catch (Exception ex) {
            lblStatus.setText(msg("tool.github.catalogError", "Error cargando catálogo: ") + ex.getMessage());
        }
    }

    private void filterCatalog() {
        try {
            String selected = comboCategory.getValue();
            curatedResults.clear();
            if (selected == null || selected.equals(msg("tool.github.allCategories", "Todas"))) {
                curatedResults.addAll(catalogService.loadCatalog());
            } else {
                // Buscar la categoría original (el msg puede haberla traducido)
                for (String cat : catalogService.getCategories()) {
                    if (selected.equals(msg("tool.github.category." + cat, cat))) {
                        curatedResults.addAll(catalogService.getByCategory(cat));
                        break;
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private void cloneRepo(GitHubRepo repo) {
        showProgress(true);
        lblStatus.setText(String.format(msg("tool.github.cloning", "Clonando %s..."), repo.name()));

        Path baseDir = cloneService.getCloneBaseDirectory();

        Thread t = new Thread(() -> {
            try {
                Path result = cloneService.cloneRepo(repo, baseDir, null);
                Platform.runLater(() -> {
                    lblStatus.setText(msg("tool.github.cloned", "Repositorio clonado: ") + repo.name());
                    showProgress(false);
                    refreshClonedRepos();
                    if (onCloned != null) onCloned.accept(result.toFile());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lblStatus.setText(msg("tool.github.cloneError", "Error clonando: ") + ex.getMessage());
                    showProgress(false);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void cloneFromUrl() {
        String url = txtUrl.getText();
        if (url == null || url.isBlank()) return;

        Matcher m = GITHUB_URL_PATTERN.matcher(url.trim());
        if (!m.matches()) {
            lblStatus.setText(msg("tool.github.invalidUrl", "URL no válida. Usa: https://github.com/owner/repo"));
            return;
        }

        String owner = m.group(1);
        String repoName = m.group(2);

        showProgress(true);
        lblStatus.setText(String.format(msg("tool.github.cloning", "Clonando %s/%s..."), owner, repoName));

        Thread t = new Thread(() -> {
            try {
                GitHubRepo repo = apiService.getRepository(owner, repoName);
                Path baseDir = cloneService.getCloneBaseDirectory();
                Path result = cloneService.cloneRepo(repo, baseDir, null);
                Platform.runLater(() -> {
                    lblStatus.setText(msg("tool.github.cloned", "Repositorio clonado: ") + repo.name());
                    showProgress(false);
                    refreshClonedRepos();
                    if (onCloned != null) onCloned.accept(result.toFile());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lblStatus.setText(msg("tool.github.cloneError", "Error clonando: ") + ex.getMessage());
                    showProgress(false);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void refreshClonedRepos() {
        clonedRepos.clear();
        clonedRepos.addAll(cloneService.listClonedRepos());
    }

    private void openClonedDir() {
        try {
            Path baseDir = cloneService.getCloneBaseDirectory();
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(baseDir.toFile());
            }
        } catch (Exception ignored) { }
    }

    private void showProgress(boolean show) {
        if (progressIndicator != null) progressIndicator.setVisible(show);
    }

    // === Cell implementations ===

    private class RepoCell extends ListCell<GitHubRepo> {
        @Override
        protected void updateItem(GitHubRepo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            Label lblName = new Label(item.fullName());
            lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

            Label lblDesc = new Label(item.description());
            lblDesc.setWrapText(true);
            lblDesc.setStyle("-fx-text-fill: gray;");

            String lang = item.language() != null && !item.language().isEmpty() ? item.language() : "-";
            Label lblMeta = new Label(String.format("⭐ %d  |  %s", item.stars(), lang));

            Button btnClone = new Button(msg("tool.github.clone", "Clonar"));
            btnClone.setOnAction(e -> cloneRepo(item));

            Button btnOpen = new Button(msg("tool.github.openBrowser", "Abrir"));
            btnOpen.setOnAction(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(item.url()));
                } catch (Exception ignored) { }
            });

            HBox buttons = new HBox(6, btnOpen, btnClone);
            buttons.setAlignment(Pos.CENTER_RIGHT);

            VBox box = new VBox(2, lblName, lblDesc, lblMeta, buttons);
            box.setPadding(new Insets(4));
            setGraphic(box);
        }
    }

    private class CuratedRepoCell extends ListCell<CuratedRepo> {
        @Override
        protected void updateItem(CuratedRepo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            Label lblName = new Label(item.repo().fullName());
            lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

            String desc = item.getDescription("es");
            if (desc == null || desc.isEmpty()) desc = item.getDescription("en");
            Label lblDesc = new Label(desc);
            lblDesc.setWrapText(true);
            lblDesc.setStyle("-fx-text-fill: gray;");

            Label lblCategory = new Label("[" + item.category() + "]");
            lblCategory.setStyle("-fx-text-fill: #2196F3;");

            Button btnClone = new Button(msg("tool.github.clone", "Clonar"));
            btnClone.setOnAction(e -> cloneRepo(item.repo()));

            Button btnOpen = new Button(msg("tool.github.openBrowser", "Abrir"));
            btnOpen.setOnAction(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(item.repo().url()));
                } catch (Exception ignored) { }
            });

            HBox buttons = new HBox(6, btnOpen, btnClone);
            buttons.setAlignment(Pos.CENTER_RIGHT);

            HBox topRow = new HBox(6, lblName, lblCategory);
            topRow.setAlignment(Pos.CENTER_LEFT);

            VBox box = new VBox(2, topRow, lblDesc, buttons);
            box.setPadding(new Insets(4));
            setGraphic(box);
        }
    }

    private class ClonedRepoCell extends ListCell<Path> {
        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            Label lblName = new Label(item.getFileName().toString());
            lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

            Label lblPath = new Label(item.toString());
            lblPath.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

            Button btnOpen = new Button(msg("tool.github.openProject", "Abrir proyecto"));
            btnOpen.setOnAction(e -> {
                if (onCloned != null) onCloned.accept(item.toFile());
            });

            Button btnDelete = new Button(msg("tool.github.delete", "Eliminar"));
            btnDelete.setOnAction(e -> {
                try {
                    cloneService.deleteClonedRepo(item);
                    refreshClonedRepos();
                } catch (Exception ex) {
                    lblStatus.setText(msg("tool.github.deleteError", "Error eliminando: ") + ex.getMessage());
                }
            });

            HBox buttons = new HBox(6, btnOpen, btnDelete);
            buttons.setAlignment(Pos.CENTER_RIGHT);

            VBox box = new VBox(2, lblName, lblPath, buttons);
            box.setPadding(new Insets(4));
            setGraphic(box);
        }
    }

    // === Helpers ===

    private String msg(String key, String fallback) {
        if (bundle == null) return fallback;
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return fallback;
        }
    }
}
