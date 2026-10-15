package com.retroeditor.controller.ai;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import com.retroeditor.service.LanguageService;
import com.retroeditor.service.ai.AiConversation;
import com.retroeditor.service.ai.AiProvider;
import com.retroeditor.service.ai.AiService;
import com.retroeditor.service.ai.AiSettings;
import com.retroeditor.service.ai.AiSettingsRepository;
import com.retroeditor.service.ai.AiStreamEvent;
import com.retroeditor.service.ai.OpenCodeRuntime;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

/**
 * Panel de chat del asistente IA.
 *
 * Reglas que mantiene este controlador:
 * - Habla solo con {@link AiService} y {@link AiConversation}. No importa ninguna clase
 *   {@code OpenCode*}: si manana hay otro proveedor, este fichero no cambia.
 * - No lanza nada de opencode ni consulta la red: la deteccion del binario la hace
 *   {@link OpenCodeRuntime} y el envio lo hace el servicio.
 * - Los eventos llegan ya en el hilo de JavaFX; aqui solo se pinta.
 */
public class AiChatController {

    @FXML private Label aiHeaderLabel;
    @FXML private Label aiStatusLabel;
    @FXML private VBox aiUnavailableBox;
    @FXML private Label aiUnavailableHintLabel;
    @FXML private VBox aiInstallHintsBox;
    @FXML private Button btnAiRecheck;
    @FXML private VBox aiPermissionBox;
    @FXML private Label aiPermissionLabel;
    @FXML private Button btnPermissionAllow;
    @FXML private Button btnPermissionAlways;
    @FXML private Button btnPermissionReject;
    @FXML private ListView<AiChatMessage> aiMessageList;
    @FXML private TextArea aiPromptArea;
    @FXML private Button btnAiSend;
    @FXML private Button btnAiCancel;
    @FXML private Button btnAiNewChat;

    private final ObservableList<AiChatMessage> items = FXCollections.observableArrayList();
    private final AiConversation conversation = new AiConversation(this::onConversationChanged);

    /** Permiso pendiente de respuesta; null cuando no hay ninguno en curso. */
    private volatile String pendingPermissionId;
    /** Detalle ya montado del permiso (tipo + patrones) para poder repintarlo al cambiar de idioma. */
    private volatile String pendingPermissionDetail;

    private AiService service;
    private AiSettings settings = AiSettings.defaults();
    private volatile OpenCodeRuntime runtime;
    private volatile String installedVersion;
    private LanguageService languageService;

    @FXML
    public void initialize() {
        if (aiMessageList != null) {
            aiMessageList.setItems(items);
            aiMessageList.setCellFactory(list -> new AiMessageCell());
            Label empty = new Label();
            empty.setWrapText(true);
            empty.getStyleClass().add("ai-chat-status");
            empty.setText(bundle("ai.chat.empty", "Sin mensajes. Escribe una petición para empezar."));
            aiMessageList.setPlaceholder(empty);
        }
        if (aiPromptArea != null) {
            aiPromptArea.addEventFilter(KeyEvent.KEY_PRESSED, this::onPromptKey);
            aiPromptArea.textProperty().addListener((obs, oldValue, newValue) -> updateButtons());
        }
        if (aiUnavailableBox != null) {
            aiUnavailableBox.setVisible(false);
        }
        if (aiPermissionBox != null) {
            aiPermissionBox.setVisible(false);
        }
        renderInstallHints();
        updateButtons();
    }

    // ---------------------------------------------------------------- cableado

    /**
     * Inyecta el servicio ya construido. Se llama desde {@code MainController} para que
     * el servicio no nazca dentro del panel y se pueda cerrar al salir.
     */
    public void setService(AiService service) {
        this.service = service;
        refreshStatus();
    }

    public void setLanguageService(LanguageService languageService) {
        this.languageService = languageService;
        applyTexts();
    }

    /** Ajustes cargados: por defecto el workspace es el proyecto abierto. */
    public void setSettings(AiSettings settings) {
        this.settings = settings != null ? settings : AiSettings.defaults();
        setRuntime(new OpenCodeRuntime(this.settings));
    }

    /**
     * Detecta el binario. Se expone aparte porque {@code MainController} ya lo consulta para
     * activar el menu y no conviene resolverlo dos veces por arranque.
     */
    public void setRuntime(OpenCodeRuntime runtime) {
        this.runtime = runtime;
        applyTexts();
        refreshStatus();
        loadInstalledVersionAsync();
    }

    /** Se llama al abrir o cambiar de proyecto. */
    public void useWorkspace(Path directory) {
        if (service == null || directory == null) {
            return;
        }
        service.useWorkspace(directory);
        conversation.clear();
        hidePermissionRequest();
        refreshStatus();
    }

    /** Cambia el estado del panel al cambiar de idioma. */
    public void applyTexts() {
        if (aiHeaderLabel != null) aiHeaderLabel.setText(bundle("ai.chat.header", "Asistente IA"));
        if (aiUnavailableHintLabel != null) {
            aiUnavailableHintLabel.setText(bundle("ai.chat.unavailableHint",
                    "Para usar el asistente necesitas instalar opencode:"));
        }
        if (aiMessageList != null && aiMessageList.getPlaceholder() instanceof Label placeholder) {
            placeholder.setText(bundle("ai.chat.empty", "Sin mensajes. Escribe una petición para empezar."));
        }
        if (aiPromptArea != null) aiPromptArea.setPromptText(bundle("ai.chat.prompt.placeholder", "Escribe tu petición..."));
        if (btnAiSend != null) btnAiSend.setTooltip(new Tooltip(bundle("ai.chat.send", "Enviar")));
        if (btnAiCancel != null) btnAiCancel.setTooltip(new Tooltip(bundle("ai.chat.cancel", "Cancelar")));
        if (btnAiNewChat != null) btnAiNewChat.setTooltip(new Tooltip(bundle("ai.chat.newConversation", "Nueva conversación")));
        if (btnAiRecheck != null) btnAiRecheck.setTooltip(new Tooltip(bundle("ai.chat.recheck", "Buscar opencode de nuevo")));
        if (btnPermissionAllow != null) btnPermissionAllow.setText(bundle("ai.permission.allow", "Permitir"));
        if (btnPermissionAlways != null) {
            btnPermissionAlways.setText(bundle("ai.permission.allowAlways", "Permitir siempre"));
        }
        if (btnPermissionReject != null) btnPermissionReject.setText(bundle("ai.permission.reject", "Denegar"));
        if (aiPermissionBox != null && aiPermissionBox.isVisible()) {
            renderPermissionLabel();
        }
        renderInstallHints();
        refreshStatus();
    }

    private String bundle(String key, String fallback) {
        if (languageService == null) {
            return fallback;
        }
        ResourceBundle current = languageService.getCurrentBundle();
        return current != null && current.containsKey(key) ? current.getString(key) : fallback;
    }

    // ---------------------------------------------------------------- acciones

    @FXML
    private void onSend() {
        if (aiPromptArea == null) {
            return;
        }
        String prompt = aiPromptArea.getText();
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        if (onSend(prompt.trim())) {
            aiPromptArea.clear();
        }
        refreshStatus();
    }

    /** Envia un texto ya limpio. Devuelve false si el servicio no lo acepta. */
    private boolean onSend(String text) {
        if (service == null) {
            return false;
        }
        if (!service.send(text)) {
            // El prompt se queda en el area: es probable que solo haya una peticion en curso
            // y no tiene sentido perder lo que el usuario habia escrito.
            conversation.add(AiChatMessage.status(bundle("ai.chat.sendRejected",
                    "Ya hay una petición en curso")));
            return false;
        }
        conversation.add(AiChatMessage.user(text));
        return true;
    }

    @FXML
    private void onCancel() {
        if (service != null) {
            service.cancel();
            refreshStatus();
        }
    }

    @FXML
    private void onNewConversation() {
        conversation.clear();
        hidePermissionRequest();
        if (service != null) {
            service.resetSession();
        }
        refreshStatus();
    }

    /** Vuelve a buscar el binario: util tras instalar opencode sin reiniciar el IDE. */
    @FXML
    private void onRecheck() {
        // Se reconstruye siempre: el PATH puede haber cambiado desde el ultimo intento.
        setRuntime(new OpenCodeRuntime(settings));
    }

    private void onPromptKey(KeyEvent event) {
        boolean isEnter = event.getCode() == javafx.scene.input.KeyCode.ENTER;
        boolean noShift = !event.isShiftDown();
        String text = aiPromptArea != null && aiPromptArea.getText() != null ? aiPromptArea.getText() : "";
        boolean onSingleLine = !text.contains("\n");
        // Enter envia; Shift+Enter añade un salto si el prompt ya ocupa varias lineas.
        if (isEnter && (noShift || onSingleLine) && isSendEnabled()) {
            event.consume();
            onSend();
        }
    }

    // ---------------------------------------------------------------- pintado

    /** Llega desde el servicio, siempre en el hilo de la UI. */
    public void onStreamEvent(AiStreamEvent event) {
        if (event instanceof AiStreamEvent.PermissionRequest request) {
            showPermissionRequest(request);
            return;
        }
        conversation.accept(event,
                bundle("ai.chat.status.busy", "Trabajando..."),
                bundle("ai.chat.tool", "Herramienta: {0}").replace("{0}", describeTool(event)));
        refreshStatus();
    }

    // ---------------------------------------------------------------- permisos

    private void showPermissionRequest(AiStreamEvent.PermissionRequest request) {
        if (aiPermissionBox == null || request.requestId().isBlank()) {
            return;
        }
        pendingPermissionId = request.requestId();
        pendingPermissionDetail = describePermission(request);
        renderPermissionLabel();
        aiPermissionBox.setVisible(true);
        aiPermissionBox.setManaged(true);
    }

    /** "bash: git status*, git diff*" — el permiso y sus patrones, tal cual del proveedor. */
    private static String describePermission(AiStreamEvent.PermissionRequest request) {
        String detail = request.permission().isBlank() ? "?" : request.permission();
        if (!request.patterns().isEmpty()) {
            detail += ": " + String.join(", ", request.patterns());
        }
        return detail;
    }

    private void renderPermissionLabel() {
        if (aiPermissionLabel != null && pendingPermissionDetail != null) {
            aiPermissionLabel.setText(bundle("ai.permission.request", "El agente solicita permiso para: {0}")
                    .replace("{0}", pendingPermissionDetail));
        }
    }

    @FXML
    private void onPermissionAllow() {
        replyPermission(AiProvider.PermissionReply.ONCE,
                "ai.permission.granted", "Permiso concedido");
    }

    @FXML
    private void onPermissionAlways() {
        replyPermission(AiProvider.PermissionReply.ALWAYS,
                "ai.permission.grantedAlways", "Permiso concedido (siempre)");
    }

    @FXML
    private void onPermissionReject() {
        replyPermission(AiProvider.PermissionReply.REJECT,
                "ai.permission.rejected", "Permiso denegado");
    }

    /** Oculta la tarjeta, responde al proveedor y deja constancia en la conversacion. */
    private void replyPermission(AiProvider.PermissionReply reply, String statusKey, String statusFallback) {
        String requestId = pendingPermissionId;
        if (service == null || requestId == null) {
            return;
        }
        hidePermissionRequest();
        service.replyPermission(requestId, reply);
        conversation.add(AiChatMessage.status(bundle(statusKey, statusFallback)));
    }

    private void hidePermissionRequest() {
        pendingPermissionId = null;
        pendingPermissionDetail = null;
        if (aiPermissionBox != null) {
            aiPermissionBox.setVisible(false);
            aiPermissionBox.setManaged(false);
        }
    }

    private static String describeTool(AiStreamEvent event) {
        if (event instanceof AiStreamEvent.ToolStart tool) {
            return !tool.title().isBlank() ? tool.title() : tool.tool();
        }
        return "";
    }

    private void onConversationChanged(AiConversation changed) {
        if (Platform.isFxApplicationThread()) {
            items.setAll(changed.messages());
            if (!items.isEmpty() && aiMessageList != null) {
                aiMessageList.scrollTo(items.size() - 1);
            }
        } else {
            Platform.runLater(() -> onConversationChanged(changed));
        }
    }

    private void refreshStatus() {
        boolean available = isRuntimeAvailable();
        if (aiUnavailableBox != null) {
            aiUnavailableBox.setManaged(!available);
            aiUnavailableBox.setVisible(!available);
        }
        if (aiStatusLabel != null) {
            aiStatusLabel.setText(statusText());
        }
        updateButtons();
    }

    private boolean isRuntimeAvailable() {
        OpenCodeRuntime current = runtime;
        return current == null || current.isAvailable();
    }

    private String statusText() {
        if (runtime != null && !runtime.isAvailable()) {
            return bundle("ai.chat.status.unavailable", "opencode no disponible");
        }
        if (service == null) {
            return "";
        }
        return switch (service.state()) {
            case STARTING -> bundle("ai.chat.status.starting", "Arrancando opencode...");
            case BUSY -> bundle("ai.chat.status.busy", "Trabajando...");
            case UNAVAILABLE -> bundle("ai.chat.status.unavailable", "opencode no disponible");
            case CLOSED -> bundle("ai.chat.status.closed", "Integración cerrada");
            case IDLE -> runtime == null ? "" : installedVersionText();
        };
    }

    /**
     * Version instalada, cacheada. Leerla lanza {@code opencode --version}, asi que se resuelve
     * una vez en segundo plano: hacerlo desde {@link #refreshStatus()} bloquearia el hilo de
     * JavaFX hasta el timeout del proceso.
     */
    private String installedVersionText() {
        return installedVersion != null && !installedVersion.isBlank()
                ? bundle("ai.chat.installedVersion", "opencode {0}").replace("{0}", installedVersion)
                : "";
    }

    private void loadInstalledVersionAsync() {
        OpenCodeRuntime current = runtime;
        if (current == null || !current.isAvailable()) {
            return;
        }
        Thread thread = new Thread(() -> {
            Optional<String> version = current.version();
            String value = version.orElse(null);
            Platform.runLater(() -> {
                installedVersion = value;
                refreshStatus();
            });
        }, "opencode-version");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderInstallHints() {
        if (aiInstallHintsBox == null) {
            return;
        }
        aiInstallHintsBox.getChildren().clear();
        for (String hint : OpenCodeRuntime.installationHints()) {
            aiInstallHintsBox.getChildren().add(new Label(hint));
        }
    }

    private void updateButtons() {
        if (btnAiSend != null) btnAiSend.setDisable(!isSendEnabled());
        if (btnAiCancel != null) btnAiCancel.setDisable(!isBusy());
    }

    /** Enviar solo si hay algo escrito, el runtime disponible y ninguna peticion en curso. */
    private boolean isSendEnabled() {
        if (service == null || service.state() == AiService.State.CLOSED || service.isBusy()) {
            return false;
        }
        if (!isRuntimeAvailable()) {
            return false;
        }
        return aiPromptArea != null
                && aiPromptArea.getText() != null
                && !aiPromptArea.getText().isBlank();
    }

    private boolean isBusy() {
        return service != null && service.isBusy();
    }

    /**
     * Envia una peticion ya redactada desde el menu ("Explicar error", "Explicar seleccion").
     * No pasa por el area de texto a proposito: el prompt construido lleva bloques de log y
     * codigo que el usuario no ha escrito y no debe ver en el editor.
     */
    public void submitPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        if (onSend(prompt)) {
            aiPromptArea.clear();
        }
        refreshStatus();
    }

    /** Pinta un aviso sin pasar por el servicio (p. ej. opencode no instalado). */
    public void showNotice(String text) {
        conversation.add(AiChatMessage.status(text));
    }

    List<AiChatMessage> messagesForTesting() {
        return new ArrayList<>(items);
    }

    /** Fila del chat: solo cambia el estilo segun el rol. */
    private final class AiMessageCell extends ListCell<AiChatMessage> {
        private final Label label = new Label();

        private AiMessageCell() {
            label.setWrapText(true);
            label.getStyleClass().add("ai-chat-bubble");
        }

        @Override
        protected void updateItem(AiChatMessage item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            label.setText(item.text());
            label.getStyleClass().removeAll("ai-bubble-user", "ai-bubble-assistant", "ai-bubble-status",
                    "ai-bubble-tool", "ai-bubble-error");
            label.getStyleClass().add(switch (item.role()) {
                case USER -> "ai-bubble-user";
                case ASSISTANT -> "ai-bubble-assistant";
                case STATUS -> "ai-bubble-status";
                case TOOL -> "ai-bubble-tool";
                case ERROR -> "ai-bubble-error";
            });
            label.setOpacity(item.role() == AiChatMessage.Role.STATUS ? 0.7 : 1.0);
            setGraphic(label);
            setText(null);
        }
    }
}
