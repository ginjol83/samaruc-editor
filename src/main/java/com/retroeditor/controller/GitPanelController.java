package com.retroeditor.controller;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.retroeditor.service.git.GitModels.GitCommitInfo;
import com.retroeditor.service.git.GitModels.GitStatusEntry;
import com.retroeditor.service.git.GitOperationsException;
import com.retroeditor.service.git.GitService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Panel acoplable de Git: muestra rama, cambios ({@code status}), permite commitear y
 * consulta el historial ({@code log}).
 *
 * <p>Las operaciones git se ejecutan en hilo de fondo y la UI se actualiza en el hilo
 * de JavaFX (nunca bloquear el Application Thread).</p>
 */
public class GitPanelController {

    private static final int MAX_LOG_ENTRIES = 30;

    private final GitService gitService;

    private ResourceBundle bundle;
    private File projectDir;
    private Consumer<Set<String>> dirtyPathsListener;

    // UI
    private Label lblBranch;
    private Label lblStatus;
    private Label lblFeedback;
    private ProgressIndicator progress;
    private Button btnRefresh;
    private Button btnInit;
    private Button btnCommit;
    private TextField txtCommit;
    private ListView<GitStatusEntry> changesList;
    private ListView<GitCommitInfo> historyList;
    private Label lblChanges;
    private Label lblHistory;

    public GitPanelController() {
        this(new GitService());
    }

    GitPanelController(GitService gitService) {
        this.gitService = gitService;
    }

    public void setBundle(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    public void setProject(File projectDir) {
        this.projectDir = projectDir;
        refreshAsync();
    }

    /** Se notifica con el conjunto de rutas relativas con cambios tras cada refresco. */
    public void setOnDirtyPathsChanged(Consumer<Set<String>> listener) {
        this.dirtyPathsListener = listener;
    }

    /** Crea la pestaña acoplable para el {@code outputTabPane}. */
    public Tab createTab() {
        Tab tab = new Tab(msg("git.panel.title", "Git"), buildUi());
        tab.setClosable(false);
        return tab;
    }

    private Node buildUi() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(4));

        // Cabecera: rama + acciones
        lblBranch = new Label();
        lblBranch.setStyle("-fx-font-weight: bold;");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        btnRefresh = new Button(msg("git.panel.refresh", "Actualizar"));
        btnRefresh.setGraphic(new FontIcon("fas-sync-alt"));
        btnRefresh.setOnAction(e -> refreshAsync());

        btnInit = new Button(msg("git.panel.init", "Inicializar repositorio"));
        btnInit.setGraphic(new FontIcon("fas-plus-circle"));
        btnInit.setOnAction(e -> initRepository());

        progress = new ProgressIndicator();
        progress.setPrefWidth(16);
        progress.setPrefHeight(16);
        progress.setVisible(false);
        progress.setManaged(false);

        HBox header = new HBox(6, lblBranch, headerSpacer, progress, btnRefresh, btnInit);
        header.setAlignment(Pos.CENTER_LEFT);
        root.setTop(header);

        // Centro: cambios + historial
        lblChanges = new Label(msg("git.panel.changes", "Cambios"));
        lblChanges.setStyle("-fx-font-weight: bold;");
        changesList = new ListView<>();
        changesList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(GitStatusEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                } else {
                    setText(entry.shortStatus() + "   " + entry.path());
                }
            }
        });

        txtCommit = new TextField();
        txtCommit.setPromptText(msg("git.panel.commit.placeholder", "Mensaje del commit"));
        HBox.setHgrow(txtCommit, Priority.ALWAYS);

        btnCommit = new Button(msg("git.panel.commit.button", "Commit"));
        btnCommit.setGraphic(new FontIcon("fas-check"));
        btnCommit.setOnAction(e -> commitChanges());
        btnCommit.disableProperty().bind(txtCommit.textProperty().isEmpty());

        lblFeedback = new Label();
        lblFeedback.setWrapText(true);
        lblFeedback.getStyleClass().add("git-feedback");

        VBox changesPane = new VBox(4, lblChanges, changesList, new HBox(4, txtCommit, btnCommit), lblFeedback);
        VBox.setVgrow(changesList, Priority.ALWAYS);
        lblChanges.setPadding(new Insets(2, 0, 0, 0));

        lblHistory = new Label(msg("git.panel.history", "Historial"));
        lblHistory.setStyle("-fx-font-weight: bold;");
        historyList = new ListView<>();
        historyList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(GitCommitInfo commit, boolean empty) {
                super.updateItem(commit, empty);
                if (empty || commit == null) {
                    setText(null);
                } else {
                    setText(commit.display());
                }
            }
        });

        VBox historyPane = new VBox(4, lblHistory, historyList);
        VBox.setVgrow(historyList, Priority.ALWAYS);

        SplitPane split = new SplitPane(changesPane, historyPane);
        split.setDividerPositions(0.62);
        split.setPadding(new Insets(4, 0, 0, 0));
        root.setCenter(split);

        // Pie: estado
        lblStatus = new Label();
        lblStatus.setStyle("-fx-font-size: 11px;");
        lblStatus.setPadding(new Insets(4, 0, 0, 0));
        root.setBottom(new VBox(2, new Separator(), new HBox(lblStatus)));

        return root;
    }

    /** Reaplica los textos i18n tras un cambio de idioma. */
    public void refreshLanguage() {
        btnRefresh.setText(msg("git.panel.refresh", "Actualizar"));
        btnInit.setText(msg("git.panel.init", "Inicializar repositorio"));
        btnCommit.setText(msg("git.panel.commit.button", "Commit"));
        txtCommit.setPromptText(msg("git.panel.commit.placeholder", "Mensaje del commit"));
        lblChanges.setText(msg("git.panel.changes", "Cambios"));
        lblHistory.setText(msg("git.panel.history", "Historial"));
        refreshAsync();
    }

    /** Pide un refresco del estado git. Seguro llamarlo desde el hilo de JavaFX. */
    public void requestRefresh() {
        refreshAsync();
    }

    private void refreshAsync() {
        if (btnRefresh == null) {
            return;
        }
        btnRefresh.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);

        CompletableFuture.runAsync(() -> {
            try {
                if (projectDir == null) {
                    Platform.runLater(this::renderNoProject);
                    return;
                }
                java.nio.file.Path dir = projectDir.toPath();
                boolean repo = gitService.isRepository(dir);
                if (!repo) {
                    Platform.runLater(() -> renderNoRepository());
                    return;
                }
                String branch = gitService.currentBranch(dir).current().orElse(null);
                List<GitStatusEntry> changes = gitService.status(dir);
                List<GitCommitInfo> commits = gitService.log(dir, MAX_LOG_ENTRIES);
                Platform.runLater(() -> renderBranch(branch, changes, commits));
            } catch (GitOperationsException e) {
                Platform.runLater(() -> renderError(e.getMessage()));
            }
        });
    }

    private void initRepository() {
        if (projectDir == null) {
            return;
        }
        btnInit.setDisable(true);
        CompletableFuture.runAsync(() -> {
            try {
                gitService.init(projectDir.toPath());
                Platform.runLater(() -> {
                    showFeedback(msg("git.panel.initialized", "Repositorio inicializado."));
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() -> {
                    btnInit.setDisable(false);
                    renderError(e.getMessage());
                });
            }
        });
    }

    private void commitChanges() {
        if (projectDir == null) {
            return;
        }
        String message = txtCommit.getText() == null ? "" : txtCommit.getText().trim();
        if (message.isBlank()) {
            showFeedback(msg("git.panel.commit.empty", "Escribe un mensaje para el commit."));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                gitService.addAll(projectDir.toPath());
                gitService.commit(projectDir.toPath(), message);
                Platform.runLater(() -> {
                    txtCommit.clear();
                    showFeedback(msg("git.panel.committed", "Commit creado."));
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() ->
                    renderError(msg("git.panel.commitFailed", "No se pudo crear el commit") + ": " + e.getMessage()));
            }
        });
    }

    private void renderNoProject() {
        if (btnRefresh == null) {
            return;
        }
        btnRefresh.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
        lblBranch.setText("");
        changesList.getItems().clear();
        historyList.getItems().clear();
        lblStatus.setText(msg("git.panel.status.noProject", "Abre un proyecto para ver su estado git."));
        btnInit.setVisible(false);
        btnInit.setManaged(false);
        notifyDirty(Set.of());
    }

    private void renderNoRepository() {
        btnRefresh.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
        btnInit.setVisible(true);
        btnInit.setManaged(true);
        btnInit.setDisable(false);
        lblBranch.setText("");
        changesList.getItems().clear();
        historyList.getItems().clear();
        lblStatus.setText(msg("git.panel.notRepo", "No es un repositorio git."));
        notifyDirty(Set.of());
    }

    private void renderBranch(String branch, List<GitStatusEntry> changes, List<GitCommitInfo> commits) {
        btnRefresh.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
        btnInit.setVisible(false);
        btnInit.setManaged(false);

        String branchLabel = branch != null
            ? msg("git.panel.status.branch", "Rama: ") + branch
            : msg("git.panel.status.noBranch", "(sin rama)");
        lblBranch.setText(branchLabel);

        changesList.getItems().setAll(changes);
        historyList.getItems().setAll(commits);

        String statusText = changes.isEmpty()
            ? msg("git.panel.status.clean", "Sin cambios por commitear")
            : msg("git.panel.status.changes", "{0} archivo(s) con cambios").replace("{0}", String.valueOf(changes.size()));
        lblStatus.setText(statusText);

        notifyDirty(changes.stream().map(GitStatusEntry::path).collect(Collectors.toSet()));
    }

    private void renderError(String message) {
        btnRefresh.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
        lblStatus.setText(msg("git.panel.error", "Error de git") + ": " + message);
        notifyDirty(Set.of());
    }

    private void showFeedback(String text) {
        lblFeedback.setText(text);
    }

    private void notifyDirty(Set<String> dirtyPaths) {
        if (dirtyPathsListener != null) {
            dirtyPathsListener.accept(dirtyPaths);
        }
    }

    private String msg(String key, String fallback) {
        if (bundle != null) {
            try {
                return bundle.getString(key);
            } catch (Exception ignored) {
                // key ausente: se usa el fallback (bug de i18n si llega a producción)
            }
        }
        return fallback;
    }
}