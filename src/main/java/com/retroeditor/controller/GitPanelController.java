package com.retroeditor.controller;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.retroeditor.service.git.GitModels.GitCommitInfo;
import com.retroeditor.service.git.GitModels.GitDiffLine;
import com.retroeditor.service.git.GitModels.GitStatusEntry;
import com.retroeditor.service.git.GitOperationsException;
import com.retroeditor.service.git.GitService;
import com.retroeditor.service.github.GitHubAuthService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Panel acoplable de Git: muestra rama y cambios ({@code status}), permite commitear,
 * pull/push, gestionar ramas, stash y descartar cambios, y consulta el historial ({@code log}).
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
    private Consumer<String> statusListener;
    private Consumer<String> operationListener;
    private Supplier<String> githubTokenSupplier = () -> "";

    // UI
    private Label lblBranch;
    private Label lblStatus;
    private Label lblFeedback;
    private ProgressIndicator progress;
    private Button btnRefresh;
    private Button btnInit;
    private Button btnPull;
    private Button btnPush;
    private Button btnCommit;
    private Button btnRestore;
    private Button btnNewBranch;
    private Button btnStash;
    private Button btnStashPop;
    private TextField txtCommit;
    private ComboBox<String> cmbBranch;
    private ListView<GitStatusEntry> changesList;
    private ListView<GitCommitInfo> historyList;
    private ListView<GitDiffLine> diffList;
    private Label lblChanges;
    private Label lblHistory;
    private Label lblDiff;
    private Label lblDiffHint;
    private boolean updatingBranches;

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

    /** Se notifica con un texto corto de estado (rama · cambios) para la barra de estado del editor. */
    public void setOnStatusChanged(Consumer<String> listener) {
        this.statusListener = listener;
    }

    /** Se notifica con un texto corto de avance (pull/push/commit/...) para la barra de estado del editor. */
    public void setOperationListener(Consumer<String> listener) {
        this.operationListener = listener;
    }

    /** Provee el token de GitHub (PAT) para autenticar pull/push por HTTPS. Nunca se persiste. */
    public void setGithubTokenSupplier(Supplier<String> supplier) {
        this.githubTokenSupplier = supplier != null ? supplier : () -> "";
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

        btnPull = new Button(msg("git.panel.pull", "Pull"));
        btnPull.setGraphic(new FontIcon("fas-arrow-down"));
        btnPull.setOnAction(e -> pullFromRemote());

        btnPush = new Button(msg("git.panel.push", "Push"));
        btnPush.setGraphic(new FontIcon("fas-arrow-up"));
        btnPush.setOnAction(e -> pushToRemote());

        btnInit = new Button(msg("git.panel.init", "Inicializar repositorio"));
        btnInit.setGraphic(new FontIcon("fas-plus-circle"));
        btnInit.setOnAction(e -> initRepository());

        progress = new ProgressIndicator();
        progress.setPrefWidth(16);
        progress.setPrefHeight(16);
        progress.setVisible(false);
        progress.setManaged(false);

        HBox header = new HBox(6, lblBranch, headerSpacer, progress, btnRefresh, btnPull, btnPush, btnInit);
        header.setAlignment(Pos.CENTER_LEFT);
        root.setTop(header);

        // Barra de ramas y stash
        cmbBranch = new ComboBox<>();
        cmbBranch.setPromptText(msg("git.panel.branch.prompt", "Rama..."));
        cmbBranch.setPrefWidth(180);
        cmbBranch.setOnAction(e -> onBranchSelected());

        btnNewBranch = new Button(msg("git.panel.branch.new", "Nueva rama"));
        btnNewBranch.setGraphic(new FontIcon("fas-code-branch"));
        btnNewBranch.setOnAction(e -> createBranchDialog());

        btnStash = new Button(msg("git.panel.stash", "Stash"));
        btnStash.setGraphic(new FontIcon("fas-box"));
        btnStash.setOnAction(e -> stashChanges());

        btnStashPop = new Button(msg("git.panel.stash.pop", "Recuperar stash"));
        btnStashPop.setGraphic(new FontIcon("fas-box-open"));
        btnStashPop.setOnAction(e -> popStash());

        HBox branchesBar = new HBox(6, cmbBranch, btnNewBranch, btnStash, btnStashPop);
        branchesBar.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(4, header, branchesBar);
        root.setTop(top);

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
        changesList.getSelectionModel().selectedItemProperty()
            .addListener((obs, oldEntry, newEntry) -> showDiffFor(newEntry));

        txtCommit = new TextField();
        txtCommit.setPromptText(msg("git.panel.commit.placeholder", "Mensaje del commit"));
        HBox.setHgrow(txtCommit, Priority.ALWAYS);

        btnCommit = new Button(msg("git.panel.commit.button", "Commit"));
        btnCommit.setGraphic(new FontIcon("fas-check"));
        btnCommit.setOnAction(e -> commitChanges());
        btnCommit.disableProperty().bind(txtCommit.textProperty().isEmpty());

        btnRestore = new Button(msg("git.panel.restore", "Descartar"));
        btnRestore.setGraphic(new FontIcon("fas-undo"));
        btnRestore.setOnAction(e -> restoreSelected());

        lblFeedback = new Label();
        lblFeedback.setWrapText(true);
        lblFeedback.getStyleClass().add("git-feedback");

        VBox changesPane = new VBox(4, lblChanges, changesList, new HBox(4, txtCommit, btnCommit, btnRestore), lblFeedback);
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

        // Panel inferior: diff inline del archivo seleccionado en "Cambios"
        lblDiff = new Label(msg("git.panel.diff", "Diff"));
        lblDiff.setStyle("-fx-font-weight: bold;");

        lblDiffHint = new Label(msg("git.panel.diff.select", "Selecciona un archivo para ver su diff."));
        lblDiffHint.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");

        diffList = new ListView<>();
        diffList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(GitDiffLine line, boolean empty) {
                super.updateItem(line, empty);
                if (empty || line == null) {
                    setText(null);
                    return;
                }
                setText(line.text());
                switch (line.type()) {
                    case ADD -> setStyle("-fx-text-fill: #2e7d32;");
                    case REMOVE -> setStyle("-fx-text-fill: #c62828;");
                    case HUNK -> setStyle("-fx-text-fill: #1565c0; -fx-font-weight: bold;");
                    case HEADER -> setStyle("-fx-text-fill: gray;");
                    default -> setStyle("");
                }
            }
        });

        VBox diffPane = new VBox(4, lblDiff, lblDiffHint, diffList);
        VBox.setVgrow(diffList, Priority.ALWAYS);

        SplitPane mainSplit = new SplitPane(split, diffPane);
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.setDividerPositions(0.62);
        root.setCenter(mainSplit);

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
        btnPull.setText(msg("git.panel.pull", "Pull"));
        btnPush.setText(msg("git.panel.push", "Push"));
        btnCommit.setText(msg("git.panel.commit.button", "Commit"));
        btnRestore.setText(msg("git.panel.restore", "Descartar"));
        btnNewBranch.setText(msg("git.panel.branch.new", "Nueva rama"));
        btnStash.setText(msg("git.panel.stash", "Stash"));
        btnStashPop.setText(msg("git.panel.stash.pop", "Recuperar stash"));
        txtCommit.setPromptText(msg("git.panel.commit.placeholder", "Mensaje del commit"));
        cmbBranch.setPromptText(msg("git.panel.branch.prompt", "Rama..."));
        lblChanges.setText(msg("git.panel.changes", "Cambios"));
        lblHistory.setText(msg("git.panel.history", "Historial"));
        lblDiff.setText(msg("git.panel.diff", "Diff"));
        refreshAsync();
    }

    /** Pide un refresco del estado git. Seguro llamarlo desde el hilo de JavaFX. */
    public void requestRefresh() {
        refreshAsync();
    }

    /** Ejecuta un pull desde origin en segundo plano (invocable desde fuera del panel). */
    public void pull() {
        pullFromRemote();
    }

    /** Ejecuta un push a origin en segundo plano (invocable desde fuera del panel). */
    public void push() {
        pushToRemote();
    }

    /** Abre el diálogo de nueva rama (invocable desde fuera del panel). */
    public void newBranch() {
        createBranchDialog();
    }

    /** Guarda los cambios en el stash (invocable desde fuera del panel). */
    public void stash() {
        stashChanges();
    }

    /** Recupera los cambios del último stash (invocable desde fuera del panel). */
    public void popStash() {
        popStashInternal();
    }

    /** Commitea todos los cambios con el mensaje dado (invocable desde fuera del panel). */
    public void commitWithMessage(String message) {
        txtCommit.setText(message);
        commitChanges();
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
                boolean hasRemote = gitService.hasRemote(dir, "origin");
                List<String> branches = gitService.listBranches(dir);
                Platform.runLater(() -> renderBranch(branch, changes, commits, hasRemote, branches));
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
                    notifyOperation(msg("git.panel.initialized", "Repositorio inicializado."));
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
                    notifyOperation(msg("git.panel.committed", "Commit creado."));
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() ->
                    renderError(msg("git.panel.commitFailed", "No se pudo crear el commit") + ": " + e.getMessage()));
            }
        });
    }

    // === Pull / Push ===

    private void pullFromRemote() {
        if (projectDir == null) {
            return;
        }
        setBusy(true);
        CompletableFuture.runAsync(() -> {
            try {
                gitService.pull(projectDir.toPath(), "origin", currentBranchName(), resolveCredentials());
                Platform.runLater(() -> {
                    String done = msg("git.panel.pulled", "Cambios recibidos del remoto.");
                    showFeedback(done);
                    notifyOperation(done);
                    setBusy(false);
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() -> {
                    setBusy(false);
                    renderError(e.getMessage());
                    notifyOperation(msg("git.panel.pull", "Pull") + " · " + e.getMessage());
                });
            }
        });
    }

    private void pushToRemote() {
        if (projectDir == null) {
            return;
        }
        setBusy(true);
        CompletableFuture.runAsync(() -> {
            try {
                gitService.push(projectDir.toPath(), "origin", currentBranchName(), resolveCredentials());
                Platform.runLater(() -> {
                    String done = msg("git.panel.pushed", "Cambios enviados al remoto.");
                    showFeedback(done);
                    notifyOperation(done);
                    setBusy(false);
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() -> {
                    setBusy(false);
                    renderError(e.getMessage());
                    notifyOperation(msg("git.panel.push", "Push") + " · " + e.getMessage());
                });
            }
        });
    }

    private String currentBranchName() {
        if (projectDir == null) {
            return "main";
        }
        try {
            return gitService.currentBranch(projectDir.toPath()).current().orElse("main");
        } catch (GitOperationsException e) {
            return "main";
        }
    }

    /** Resuelve las credenciales para operaciones HTTPS desde el proveedor de token y la variable de entorno. */
    private GitService.GitCredentials resolveCredentials() {
        String token = githubTokenSupplier != null ? githubTokenSupplier.get() : "";
        if (token == null || token.isBlank()) {
            token = System.getenv(GitHubAuthService.TOKEN_ENV);
        }
        if (token == null || token.isBlank()) {
            return null;
        }
        return new GitService.GitCredentials(token);
    }

    // === Ramas ===

    private void onBranchSelected() {
        if (updatingBranches || projectDir == null) {
            return;
        }
        String selected = cmbBranch.getSelectionModel().getSelectedItem();
        String current = currentBranchName();
        if (selected == null || selected.equals(current)) {
            return;
        }
        btnNewBranch.setDisable(true);
        CompletableFuture.runAsync(() -> {
            try {
                gitService.switchBranch(projectDir.toPath(), selected);
                Platform.runLater(() -> {
                    String done = msg("git.panel.branch.switched", "Rama cambiada a {0}")
                        .replace("{0}", selected);
                    showFeedback(done);
                    notifyOperation(done);
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() -> {
                    btnNewBranch.setDisable(false);
                    renderError(msg("git.panel.branch.failed", "No se pudo cambiar de rama") + ": " + e.getMessage());
                    refreshAsync();
                });
            }
        });
    }

    private void createBranchDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(msg("git.panel.branch.new", "Nueva rama"));
        dialog.setHeaderText(null);
        dialog.setContentText(msg("git.panel.branch.name", "Nombre de la nueva rama:"));
        dialog.showAndWait().ifPresent(rawName -> {
            String name = rawName == null ? "" : rawName.trim();
            if (name.isBlank()) {
                showFeedback(msg("git.panel.branch.empty", "Escribe un nombre para la rama."));
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    gitService.createBranch(projectDir.toPath(), name);
                    Platform.runLater(() -> {
                        String done = msg("git.panel.branch.created", "Rama creada y activada: {0}")
                            .replace("{0}", name);
                        showFeedback(done);
                        notifyOperation(done);
                        refreshAsync();
                    });
                } catch (GitOperationsException e) {
                    Platform.runLater(() -> renderError(e.getMessage()));
                }
            });
        });
    }

    // === Stash ===

    private void stashChanges() {
        if (projectDir == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                gitService.stash(projectDir.toPath());
                Platform.runLater(() -> {
                    String done = msg("git.panel.stashed", "Cambios guardados en el stash.");
                    showFeedback(done);
                    notifyOperation(done);
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() -> renderError(e.getMessage()));
            }
        });
    }

    private void popStashInternal() {
        if (projectDir == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                gitService.stashPop(projectDir.toPath());
                Platform.runLater(() -> {
                    String done = msg("git.panel.stashPopped", "Cambios recuperados del stash.");
                    showFeedback(done);
                    notifyOperation(done);
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() -> renderError(e.getMessage()));
            }
        });
    }

    // === Descartar cambios ===

    private void restoreSelected() {
        GitStatusEntry selected = changesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showFeedback(msg("git.panel.restore.select", "Selecciona un archivo de la lista para descartar sus cambios."));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                gitService.restoreFile(projectDir.toPath(), selected.path());
                Platform.runLater(() -> {
                    String done = msg("git.panel.restored", "Cambios descartados de {0}.")
                        .replace("{0}", selected.path());
                    showFeedback(done);
                    notifyOperation(done);
                    refreshAsync();
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() -> renderError(e.getMessage()));
            }
        });
    }

    // === Diff inline ===

    private void showDiffFor(GitStatusEntry entry) {
        diffList.getItems().clear();
        if (projectDir == null || entry == null) {
            lblDiffHint.setText(msg("git.panel.diff.select", "Selecciona un archivo para ver su diff."));
            return;
        }
        if (entry.isUntracked()) {
            lblDiffHint.setText(msg("git.panel.diff.untracked",
                "Archivo sin seguimiento (untracked): no muestra diff hasta añadirlo."));
            return;
        }
        lblDiffHint.setText(msg("git.panel.diff.loading", "Cargando diff..."));
        final java.nio.file.Path dir = projectDir.toPath();
        CompletableFuture.runAsync(() -> {
            try {
                List<GitDiffLine> lines = new java.util.ArrayList<>();
                if (entry.hasStagedChange()) {
                    lines.addAll(gitService.diff(dir, entry.path(), true));
                }
                if (entry.hasUnstagedChange()) {
                    lines.addAll(gitService.diff(dir, entry.path(), false));
                }
                Platform.runLater(() -> {
                    diffList.getItems().setAll(lines);
                    lblDiffHint.setText(lines.isEmpty()
                        ? msg("git.panel.diff.empty", "Sin diferencias que mostrar.")
                        : msg("git.panel.diff.file", "Diff de {0}").replace("{0}", entry.path()));
                });
            } catch (GitOperationsException e) {
                Platform.runLater(() -> lblDiffHint.setText(
                    msg("git.panel.diff.error", "Error cargando el diff") + ": " + e.getMessage()));
            }
        });
    }

    // === Render ===

    private void setBusy(boolean busy) {
        btnRefresh.setDisable(busy);
        btnPull.setDisable(busy);
        btnPush.setDisable(busy);
        btnInit.setDisable(busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
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
        btnPull.setVisible(false);
        btnPull.setManaged(false);
        btnPush.setVisible(false);
        btnPush.setManaged(false);
        cmbBranch.getItems().clear();
        diffList.getItems().clear();
        lblDiffHint.setText(msg("git.panel.diff.select", "Selecciona un archivo para ver su diff."));
        branchesBarVisible(false);
        notifyStatus("");
        notifyDirty(Set.of());
    }

    private void branchesBarVisible(boolean visible) {
        cmbBranch.setVisible(visible);
        cmbBranch.setManaged(visible);
        btnNewBranch.setVisible(visible);
        btnNewBranch.setManaged(visible);
        btnStash.setVisible(visible);
        btnStash.setManaged(visible);
        btnStashPop.setVisible(visible);
        btnStashPop.setManaged(visible);
    }

    private void renderNoRepository() {
        btnRefresh.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
        btnInit.setVisible(true);
        btnInit.setManaged(true);
        btnInit.setDisable(false);
        btnPull.setVisible(false);
        btnPull.setManaged(false);
        btnPush.setVisible(false);
        btnPush.setManaged(false);
        branchesBarVisible(false);
        lblBranch.setText("");
        changesList.getItems().clear();
        historyList.getItems().clear();
        diffList.getItems().clear();
        lblDiffHint.setText(msg("git.panel.diff.select", "Selecciona un archivo para ver su diff."));
        lblStatus.setText(msg("git.panel.notRepo", "No es un repositorio git."));
        notifyStatus("");
        notifyDirty(Set.of());
    }

    private void renderBranch(String branch, List<GitStatusEntry> changes, List<GitCommitInfo> commits,
                              boolean hasRemote, List<String> branches) {
        btnRefresh.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
        btnInit.setVisible(false);
        btnInit.setManaged(false);
        btnPull.setVisible(true);
        btnPull.setManaged(true);
        btnPush.setVisible(true);
        btnPush.setManaged(true);
        btnPull.setDisable(!hasRemote);
        btnPush.setDisable(!hasRemote);
        btnNewBranch.setDisable(false);
        btnStash.setDisable(false);
        btnStashPop.setDisable(false);
        branchesBarVisible(true);

        String branchLabel = branch != null
            ? msg("git.panel.status.branch", "Rama: ") + branch
            : msg("git.panel.status.noBranch", "(sin rama)");
        lblBranch.setText(branchLabel);

        // Poblar el combo de ramas sin disparar el switching programático
        updatingBranches = true;
        cmbBranch.getItems().setAll(branches);
        if (branch != null) {
            cmbBranch.getSelectionModel().select(branch);
        }
        updatingBranches = false;

        changesList.getItems().setAll(changes);
        historyList.getItems().setAll(commits);

        String statusText = changes.isEmpty()
            ? msg("git.panel.status.clean", "Sin cambios por commitear")
            : msg("git.panel.status.changes", "{0} archivo(s) con cambios").replace("{0}", String.valueOf(changes.size()));
        if (hasRemote) {
            statusText = statusText + " · " + msg("git.panel.status.remote", "origin");
        }
        lblStatus.setText(statusText);

        this.notifyStatus(statusText.isEmpty()
            ? branchLabel
            : branchLabel + " · " + statusText);
        notifyDirty(changes.stream().map(GitStatusEntry::path).collect(Collectors.toSet()));
    }

    private void renderError(String message) {
        btnRefresh.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
        lblStatus.setText(msg("git.panel.error", "Error de git") + ": " + message);
        notifyStatus("");
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

    private void notifyStatus(String text) {
        if (statusListener != null) {
            statusListener.accept(text);
        }
    }

    private void notifyOperation(String text) {
        if (operationListener != null) {
            operationListener.accept(text);
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