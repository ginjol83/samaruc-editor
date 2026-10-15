package com.retroeditor.service.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Proveedor real: habla con {@code opencode serve} por HTTP y consume el stream de eventos.
 *
 * Es la unica clase del paquete que conoce el protocolo de opencode. Por eso el panel de chat
 * nunca lo ve y {@link AiProvider} sigue siendo suficiente para anadir otro proveedor.
 *
 * Traduccion de eventos (contrato de opencode -> {@link AiStreamEvent}):
 * <ul>
 *   <li>{@code message.part.updated} de tipo {@code text} -> {@code TextDelta}, usando el
 *       campo {@code delta} cuando viene (evita repetir el texto entero en cada fragmento)</li>
 *   <li>{@code message.part.updated} de tipo {@code tool} -> {@code ToolStart}, solo la primera
 *       vez que aparece la herramienta</li>
 *   <li>{@code session.error} -> {@code Error}</li>
 *   <li>{@code session.idle} -> {@code Done}: es la senal de que la respuesta termino</li>
 *   <li>{@code permission.asked} (y su variante {@code permission.v2.asked}) ->
 *       {@code PermissionRequest}, una sola vez por peticion</li>
 * </ul>
 *
 * Decisiones que importan:
 * - {@code prompt()} es bloqueante hasta {@code session.idle}, pero se llama siempre desde el
 *   hilo del pool de {@link AiService}, nunca desde el hilo de JavaFX.
 * - El stream se filtra por {@code sessionID}: el servidor emite eventos de todas las sesiones
 *   del directorio y solo nos interesan los de la nuestra.
 * - Los identificadores de sesion se tratan como opacos, como manda el contrato.
 */
public class OpenCodeProvider implements AiProvider {

    /** Espera maxima por la respuesta de una peticion antes de darla por perdida. */
    private static final long RESPONSE_TIMEOUT_SECONDS = 600;

    /** Margen para que el stream este abierto antes de lanzar el prompt. */
    private static final long STREAM_READY_TIMEOUT_SECONDS = 15;

    private final OpenCodeServerBridge bridge;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Semaforos por sesion para no perder un Done que llega mientras se compone el sink. */
    private final Map<String, CountDownLatch> pending = new ConcurrentHashMap<>();

    /** Sesion cuyos eventos llegan al panel; null mientras no hay peticion en vuelo. */
    private volatile String activeSessionId;

    /** Estado del turno en curso: evita repetir texto y herramientas ya mostrados. */
    private final TurnState turn = new TurnState();

    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile CountDownLatch streamReady = new CountDownLatch(1);
    private volatile Thread streamThread;

    public OpenCodeProvider(OpenCodeServerBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String id() {
        return "opencode";
    }

    @Override
    public boolean isAvailable() {
        return bridge != null && bridge.isAvailable();
    }

    @Override
    public String unavailableReason() {
        return bridge != null ? bridge.unavailableReason() : "opencode no esta disponible";
    }

    /**
     * Arranca el servidor (solo si no lo esta) y asegura el stream de eventos.
     *
     * El servicio llama a este metodo en cada peticion, asi que tiene que ser idempotente:
     * abrir un hilo y una conexion SSE por prompt dejaria un monton de streams abiertos
     * apuntando al mismo servidor.
     */
    @Override
    public void start(AiWorkspace workspace, AiEventSink sink) throws AiException {
        if (closed.get()) {
            throw new AiException("el proveedor esta cerrado");
        }
        if (!isAvailable()) {
            throw new AiException(unavailableReason());
        }
        // Si el workspace cambio, el bridge reinicia el servidor: el stream anterior quedo
        // pegado a un proceso muerto, asi que hay que volver a conectarse.
        if (bridge.start(workspace != null ? workspace.directory() : null)) {
            streamReady = new CountDownLatch(1);
        }
        ensureEventStream(sink);
    }

    @Override
    public String createSession() throws AiException {
        String body = bridge.post(URI.create("/session"), "{}");
        JsonNode parsed = readTree(body);
        String id = parsed.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new AiException("opencode no devolvio un id de sesion valido");
        }
        return id;
    }

    @Override
    public void prompt(String sessionId, String promptText) throws AiException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new AiException("sesion invalida");
        }
        String body = promptBody(promptText);
        CountDownLatch finished = new CountDownLatch(1);
        pending.put(sessionId, finished);
        activeSessionId = sessionId;
        turn.reset();
        try {
            awaitStreamReady();
            bridge.post(URI.create("/session/" + sessionId + "/prompt_async"), body);
            // El stream de eventos es quien cierra esto; el timeout es solo una red de seguridad
            // para el caso de que opencode muera a mitad de respuesta.
            if (!finished.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AiException("opencode no termino la respuesta en "
                        + RESPONSE_TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("respuesta interrumpida", e);
        } finally {
            pending.remove(sessionId);
            if (sessionId.equals(activeSessionId)) {
                activeSessionId = null;
            }
        }
    }

    /**
     * No se puede lanzar el prompt hasta que el stream este conectado: los eventos del turno
     * empiezan a emitirse en cuanto se acepta el prompt, y sin suscripcion se perderian
     * (la respuesta se quedaria colgada hasta el timeout).
     */
    private void awaitStreamReady() throws AiException, InterruptedException {
        if (streamReady.await(STREAM_READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return;
        }
        throw new AiException("no se pudo suscribir al stream de eventos de opencode en "
                + STREAM_READY_TIMEOUT_SECONDS + "s");
    }

    @Override
    public void abort(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !bridge.isRunning()) {
            return;
        }
        try {
            bridge.post(URI.create("/session/" + sessionId + "/abort"), "{}");
        } catch (AiException e) {
            // abortar es best-effort: si la sesion ya estaba cerrada no hay nada que hacer
        }
    }

    /**
     * Responde a una peticion de permiso con {@code POST /permission/{requestID}/reply}.
     * El cuerpo es {@code {"reply":"once"|"always"|"reject"}}, los mismos literales que
     * usa el TUI de opencode.
     */
    @Override
    public void replyPermission(String requestId, PermissionReply reply) throws AiException {
        if (requestId == null || requestId.isBlank()) {
            throw new AiException("peticion de permiso invalida");
        }
        if (reply == null) {
            throw new AiException("respuesta de permiso invalida");
        }
        if (bridge == null || !bridge.isRunning()) {
            throw new AiException("opencode no esta en marcha");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("reply", switch (reply) {
            case ONCE -> "once";
            case ALWAYS -> "always";
            case REJECT -> "reject";
        });
        bridge.post(URI.create("/permission/" + requestId + "/reply"), writeJson(body));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            streamReady.countDown();
            if (bridge != null) {
                bridge.close();
            }
        }
    }

    /** Cuerpo de {@code prompt_async}: el texto va como un TextPartInput. */
    private String promptBody(String promptText) throws AiException {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode parts = root.putArray("parts");
        ObjectNode text = parts.addObject();
        text.put("type", "text");
        text.put("text", promptText != null ? promptText : "");
        return writeJson(root);
    }

    private String writeJson(JsonNode node) throws AiException {
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new AiException("no se pudo serializar la peticion: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- stream de eventos

    /**
     * Un unico hilo daemon para todo el proveedor. Reconecta mientras siga vivo: el servidor
     * cierra la conexion cada cierto tiempo y tambien se reinicia al cambiar de workspace.
     */
    private synchronized void ensureEventStream(AiEventSink sink) {
        if (streamThread != null && streamThread.isAlive()) {
            return;
        }
        Thread thread = new Thread(() -> consumeEventStream(sink), "opencode-events");
        thread.setDaemon(true);
        streamThread = thread;
        thread.start();
    }

    private void consumeEventStream(AiEventSink sink) {
        while (!closed.get()) {
            if (!bridge.isRunning()) {
                // El servidor se esta arrancando o se ha reiniciado por un cambio de proyecto.
                sleepQuietly(150);
                continue;
            }
            try {
                streamOnce(sink);
            } catch (IOException e) {
                if (closed.get() || !bridge.isRunning()) {
                    // El proceso se fue: se reconecta cuando vuelva a estar arriba.
                    continue;
                }
                // El servidor corta la conexion cada cierto tiempo: es normal, se reconecta.
                sleepQuietly(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void streamOnce(AiEventSink sink) throws IOException, InterruptedException {
        if (bridge.baseUri().isEmpty()) {
            throw new IOException("servidor no arrancado");
        }
        try (InputStream body = bridge.openEventStream(URI.create("/event"))) {
            // Suscrito: a partir de aqui ningun evento del turno se pierde.
            streamReady.countDown();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(body, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleSseLine(line, sink);
                }
            }
        } catch (AiProvider.AiException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Una linea del stream. SSE solo interesa por las que empiezan por {@code data:}; el resto
     * son comentarios y campos de control que se ignoran.
     */
    private void handleSseLine(String line, AiEventSink sink) {
        if (line == null || !line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if (payload.isEmpty()) {
            return;
        }
        for (AiStreamEvent event : translateForActiveSession(payload)) {
            sink.accept(event);
        }
    }

    /**
     * Traduce un evento descartando los de otras sesiones. El servidor emite para todo el
     * directorio: si el usuario tiene el TUI abierto en el mismo proyecto, sus eventos se
     * colarian en el panel del IDE.
     */
    private List<AiStreamEvent> translateForActiveSession(String json) {
        String active = activeSessionId;
        if (active == null) {
            return List.of();
        }
        try {
            JsonNode properties = mapper.readTree(json).path("properties");
            if (!active.equals(sessionIdOf(properties))) {
                return List.of();
            }
        } catch (IOException e) {
            return List.of();
        }
        return translate(json, turn);
    }

    /** La sesion viaja en {@code properties.sessionID} o dentro de la parte del mensaje. */
    private static String sessionIdOf(JsonNode properties) {
        String direct = properties.path("sessionID").asText("");
        if (!direct.isEmpty()) {
            return direct;
        }
        return properties.path("part").path("sessionID").asText("");
    }

    /**
     * Traduce un evento JSON de opencode. Es package-private para poder testearlo sin red:
     * es la parte con reglas y la que no puede depender de un servidor vivo.
     */
    List<AiStreamEvent> translate(String json) {
        return translate(json, new TurnState());
    }

    List<AiStreamEvent> translate(String json, TurnState state) {
        List<AiStreamEvent> events = new ArrayList<>();
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (IOException e) {
            return events;
        }
        String type = root.path("type").asText("");
        JsonNode properties = root.path("properties");
        switch (type) {
            case "message.part.updated" -> translatePart(properties.path("part"), properties, events, state);
            case "message.updated" -> state.rememberRole(
                    properties.path("info").path("id").asText(""),
                    properties.path("info").path("role").asText(""));
            case "session.error" -> {
                events.add(AiStreamEvent.error(errorMessage(properties.path("error"))));
                // Sin esto el prompt esperaria al timeout entero tras un error del proveedor.
                release(properties.path("sessionID").asText(null));
            }
            case "session.status" -> translateStatus(properties.path("status"), events);
            case "permission.asked", "permission.v2.asked" -> translatePermission(properties, events, state);
            case "session.idle" -> {
                events.add(new AiStreamEvent.Done());
                release(properties.path("sessionID").asText(null));
            }
            default -> {
                // message.updated, todo.* y demas no aportan texto al chat
            }
        }
        return events;
    }

    private void translatePart(JsonNode part, JsonNode properties, List<AiStreamEvent> events, TurnState state) {
        if (part.isMissingNode() || part.isNull()) {
            return;
        }
        String partId = part.path("id").asText("");
        String partType = part.path("type").asText("");
        if ("text".equals(partType)) {
            if (state.isUserMessage(part.path("messageID").asText(""))) {
                // opencode reenvia como parte de texto el prompt que acabamos de enviar: sin
                // este filtro el panel escribiria dos veces lo que ha pedido el usuario.
                return;
            }
            // opencode envia el texto acumulado y, cuando puede, el delta. Sin delta hay que
            // quedarse solo con el trozo nuevo: si no, cada fragmento repitaria el parrafo
            // entero en el panel.
            String delta = properties.path("delta").asText("");
            String text = delta.isEmpty() ? part.path("text").asText("") : delta;
            String fresh = state.acceptText(partId, text, delta.isEmpty());
            if (!fresh.isEmpty()) {
                events.add(AiStreamEvent.textDelta(fresh));
            }
            return;
        }
        if ("tool".equals(partType)) {
            JsonNode toolState = part.path("state");
            String status = toolState.path("status").asText("");
            // Solo interesan los estados vivos; un completed no es una herramienta en curso.
            // La misma herramienta pasa por pending y running: se anuncia una sola vez.
            if (("pending".equals(status) || "running".equals(status))
                    && state.acceptTool(partId, part.path("tool").asText(""),
                            toolState.path("title").asText(""))) {
                events.add(new AiStreamEvent.ToolStart(
                        part.path("tool").asText(""), toolState.path("title").asText("")));
            }
        }
    }

    private void translateStatus(JsonNode status, List<AiStreamEvent> events) {
        String type = status.path("type").asText("");
        if ("retry".equals(type)) {
            events.add(AiStreamEvent.status("reintentando: " + status.path("message").asText("")));
        } else if ("busy".equals(type)) {
            events.add(AiStreamEvent.status("trabajando"));
        }
    }

    /**
     * Peticion de permiso del agente. opencode puede reenviar la misma peticion y ademas
     * existe una variante v2 que trae el tipo en {@code action} en lugar de
     * {@code permission}: ambas se traducen al mismo evento y solo se anuncia una vez.
     */
    private void translatePermission(JsonNode properties, List<AiStreamEvent> events, TurnState state) {
        String requestId = properties.path("id").asText("");
        if (requestId.isEmpty() || !state.acceptPermission(requestId)) {
            return;
        }
        String permission = properties.path("permission").asText("");
        if (permission.isEmpty()) {
            permission = properties.path("action").asText("");
        }
        List<String> patterns = new ArrayList<>();
        JsonNode raw = properties.path("patterns");
        if (raw.isArray()) {
            raw.forEach(pattern -> patterns.add(pattern.asText("")));
        }
        events.add(new AiStreamEvent.PermissionRequest(requestId, permission, patterns));
    }

    private static String errorMessage(JsonNode error) {
        JsonNode data = error.path("data");
        String message = data.path("message").asText("");
        if (!message.isEmpty()) {
            return message;
        }
        return error.path("name").asText("opencode devolvio un error");
    }

    /** Marca la sesion como terminada si aun la hay en la tabla de pendientes. */
    private void release(String sessionId) {
        if (sessionId == null) {
            return;
        }
        CountDownLatch latch = pending.get(sessionId);
        if (latch != null) {
            latch.countDown();
        }
    }

    private JsonNode readTree(String body) throws AiException {
        try {
            return mapper.readTree(body != null && !body.isBlank() ? body : "{}");
        } catch (IOException e) {
            throw new AiException("respuesta ilegible de opencode: " + e.getMessage(), e);
        }
    }

private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Lo ya mostrado del turno en curso, para no repetirlo.
     *
     * opencode reenvia cada parte del mensaje con su estado completo: una parte de texto
     * llega muchas veces con el texto entero y una herramienta pasa por pending y running.
     * Sin esto el panel repetiria el parrafo entero en cada fragmento y la misma herramienta
     * apareceria dos veces.
     *
     * Solo lo usa el hilo del stream (un unico consumidor), asi que no necesita sincronizacion.
     */
    static final class TurnState {

        /** Texto ya emitido por parte, para quedarnos con el trozo que falta. */
        private final Map<String, StringBuilder> emittedText = new HashMap<>();

        /** Clave de la parte cuya herramienta ya se ha anunciado. */
        private final Set<String> announcedTools = new HashSet<>();

        /** Ids de permiso ya mostrados: opencode puede reenviar la misma peticion. */
        private final Set<String> announcedPermissions = new HashSet<>();

        /** Rol de cada mensaje del turno: lo dice message.updated, no la parte. */
        private final Map<String, String> messageRoles = new HashMap<>();

        void reset() {
            emittedText.clear();
            announcedTools.clear();
            announcedPermissions.clear();
            messageRoles.clear();
        }

        void rememberRole(String messageId, String role) {
            if (!messageId.isEmpty() && !role.isEmpty()) {
                messageRoles.put(messageId, role);
            }
        }

        boolean isUserMessage(String messageId) {
            return "user".equals(messageRoles.get(messageId));
        }

        /**
         * Devuelve el trozo de texto que aun no se ha mostrado.
         *
         * @param accumulated si el evento traia el texto completo en lugar de un delta
         */
        String acceptText(String partId, String text, boolean accumulated) {
            if (text == null || text.isEmpty()) {
                return "";
            }
            if (!accumulated) {
                append(partId, text);
                return text;
            }
            if (partId == null || partId.isEmpty()) {
                // Sin id de parte no hay forma de saber si lo que llega es texto nuevo o el
                // mismo: se muestra tal cual antes que comerse un fragmento.
                return text;
            }
            StringBuilder sent = emittedText.get(partId);
            if (sent != null && text.startsWith(sent.toString())) {
                String fresh = text.substring(sent.length());
                sent.append(fresh);
                return fresh;
            }
            // El texto no es una extension del anterior (parte nueva o reescrita): se muestra
            // entero y se olvida lo anterior.
            emittedText.put(partId, new StringBuilder(text));
            return text;
        }

        /** Devuelve true la primera vez que aparece una herramienta en este turno. */
        boolean acceptTool(String partId, String tool, String title) {
            String key = partId != null && !partId.isEmpty() ? partId : tool + "|" + title;
            return announcedTools.add(key);
        }

        /** Devuelve true la primera vez que se anuncia un permiso en este turno. */
        boolean acceptPermission(String requestId) {
            return announcedPermissions.add(requestId);
        }

        private void append(String partId, String text) {
            if (partId != null && !partId.isEmpty()) {
                emittedText.computeIfAbsent(partId, id -> new StringBuilder()).append(text);
            }
        }
    }
}
