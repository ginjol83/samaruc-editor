package com.retroeditor.service.ai;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Punto de entrada de la UI para pedir ayuda al agente.
 *
 * El panel de chat solo conoce esta clase: nunca ve un {@code OpenCode*} ni un
 * {@code Process}. Aqui se cumple la regla del proyecto de no bloquear el hilo de
 * JavaFX: el trabajo pesado corre en un pool propio y la UI recibe los eventos ya en su
 * hilo. Los eventos se despachan a traves de un puente inyectable para que los tests no
 * dependan de JavaFX.
 */
public class AiService implements AutoCloseable {

    /** Como se lleva un evento del hilo de trabajo al hilo de JavaFX. */
    @FunctionalInterface
    public interface UiDispatcher {
        /** Ejecuta la accion en el hilo de la interfaz. */
        void dispatch(Runnable action);
    }

    /** Estado observable del servicio, para pintar la cabecera del panel. */
    public enum State {
        IDLE,
        STARTING,
        BUSY,
        UNAVAILABLE,
        CLOSED
    }

    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "ai-service");
        thread.setDaemon(true);
        return thread;
    };

    private static final ThreadFactory PERMISSION_THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "ai-permissions");
        thread.setDaemon(true);
        return thread;
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor(THREAD_FACTORY);
    /**
     * Executor aparte para responder permisos: el de una sola hebra puede estar ocupado
     * en {@code prompt()} esperando a {@code session.idle}, que no se cierra justo hasta
     * que el usuario responde al permiso. Encolar la respuesta ahi lo bloquearia.
     */
    private final ExecutorService permissionExecutor =
            Executors.newSingleThreadExecutor(PERMISSION_THREAD_FACTORY);
    private final UiDispatcher dispatcher;
    private final Consumer<AiStreamEvent> eventConsumer;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final AtomicReference<String> currentSession = new AtomicReference<>();
    private volatile boolean running = true;
    private volatile AiProvider provider;
    private volatile AiProvider.AiWorkspace workspace;
    private volatile String lastError;
    private volatile AiProvider.AiWorkspace sessionWorkspace;
    private final Object sessionLock = new Object();

    public AiService(UiDispatcher dispatcher, Consumer<AiStreamEvent> eventConsumer) {
        this.dispatcher = dispatcher != null ? dispatcher : Runnable::run;
        this.eventConsumer = eventConsumer != null ? eventConsumer : event -> { };
    }

    public State state() {
        return state.get();
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    public Optional<String> currentSessionId() {
        return Optional.ofNullable(currentSession.get());
    }

    public boolean isBusy() {
        return state.get() == State.BUSY || state.get() == State.STARTING;
    }

    /**
     * Fija el proveedor activo. Si el workspace cambia se reinicia la sesion en el
     * siguiente envio, porque el agente no debe ver un directorio equivocado.
     */
    public void useProvider(AiProvider newProvider) {
        AiProvider previous = this.provider;
        this.provider = newProvider;
        if (previous != null && previous != newProvider) {
            previous.close();
        }
        if (newProvider == null || !newProvider.isAvailable()) {
            lastError = newProvider == null ? "proveedor no configurado" : newProvider.unavailableReason();
            state.set(State.UNAVAILABLE);
        } else {
            lastError = null;
            state.set(State.IDLE);
        }
    }

    /** Cambia el workspace efectivo (directorio del proyecto abierto). */
    public void useWorkspace(Path directory) {
        this.workspace = new AiProvider.AiWorkspace(directory);
    }

    /**
     * Envia un prompt. No bloquea: el resultado llega por el consumidor de eventos.
     * Devuelve {@code false} si no hay proveedor o ya hay una peticion en curso.
     */
    public boolean send(String prompt) {
        if (!running || prompt == null || prompt.isBlank()) {
            return false;
        }
        AiProvider active = provider;
        if (active == null || !active.isAvailable()) {
            lastError = active == null ? "opencode no esta disponible" : active.unavailableReason();
            state.set(State.UNAVAILABLE);
            publish(AiStreamEvent.error(lastError));
            return false;
        }
        if (isBusy()) {
            return false;
        }
        lastError = null;
        state.set(State.BUSY);
        executor.execute(() -> runPrompt(active, prompt.trim()));
        return true;
    }

    private void runPrompt(AiProvider active, String prompt) {
        try {
            AiProvider.AiWorkspace effective = currentWorkspace();
            active.start(effective, this::publish);
            active.prompt(existingOrCreateSession(active, effective), prompt);
        } catch (AiProvider.AiException e) {
            fail(e.getMessage());
        } catch (RuntimeException e) {
            fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } finally {
            if (state.get() != State.UNAVAILABLE && state.get() != State.CLOSED) {
                state.set(State.IDLE);
            }
        }
    }

    /**
     * Reutiliza la sesion mientras el workspace no cambie. Si el usuario abre otro
     * proyecto hay que empezar de cero: el agente no debe conservar contexto de un
     * directorio al que ya no tiene acceso.
     */
    private String existingOrCreateSession(AiProvider active, AiProvider.AiWorkspace workspace)
            throws AiProvider.AiException {
        synchronized (sessionLock) {
            if (sessionWorkspace != null && !sessionWorkspace.equals(workspace)) {
                abortQuietly(active, currentSession.get());
                currentSession.set(null);
            }
            String existing = currentSession.get();
            if (existing != null) {
                return existing;
            }
            String created = active.createSession();
            currentSession.set(created);
            sessionWorkspace = workspace;
            return created;
        }
    }

    private void abortQuietly(AiProvider active, String sessionId) {
        if (active == null || sessionId == null) {
            return;
        }
        try {
            active.abort(sessionId);
        } catch (RuntimeException ignored) {
            // la sesion previa ya estaba cerrada
        }
    }

    private AiProvider.AiWorkspace currentWorkspace() {
        AiProvider.AiWorkspace current = workspace;
        return current != null ? current : new AiProvider.AiWorkspace(Path.of(System.getProperty("user.dir")));
    }

    /** Interrumpe la peticion en curso sin cerrar la sesion. */
    public void cancel() {
        AiProvider active = provider;
        String sessionId = currentSession.get();
        if (active == null || sessionId == null) {
            state.set(State.IDLE);
            return;
        }
        executor.execute(() -> {
            try {
                active.abort(sessionId);
                publish(AiStreamEvent.status("peticion cancelada"));
            } catch (RuntimeException e) {
                publish(AiStreamEvent.error("no se pudo cancelar: " + e.getMessage()));
            }
        });
    }

    /**
     * Responde a una peticion de permiso del agente en curso ({@code AiStreamEvent.PermissionRequest}).
     * No bloquea: el envio va al executor de permisos y un fallo se publica como evento
     * {@code Error} para que el panel lo muestre.
     */
    public void replyPermission(String requestId, AiProvider.PermissionReply reply) {
        AiProvider active = provider;
        if (!running || active == null || requestId == null || requestId.isBlank() || reply == null) {
            return;
        }
        permissionExecutor.execute(() -> {
            try {
                active.replyPermission(requestId, reply);
            } catch (AiProvider.AiException | RuntimeException e) {
                publish(AiStreamEvent.error("no se pudo responder al permiso: " + e.getMessage()));
            }
        });
    }

    /** Cierra la sesion actual para empezar de cero (boton "nueva conversacion"). */
    public void resetSession() {
        AiProvider active = provider;
        String sessionId;
        synchronized (sessionLock) {
            sessionId = currentSession.getAndSet(null);
            sessionWorkspace = null;
        }
        if (active == null || sessionId == null) {
            return;
        }
        executor.execute(() -> abortQuietly(active, sessionId));
    }

    /**
     * Traduce un fallo de la peticion a estado visible. No se marca el servicio como
     * indisponible: un timeout o un error del proveedor son cosas del turno concreto, y dejar
     * el panel bloqueado para siempre haria que hubiera que reiniciar el IDE.
     */
    private void fail(String message) {
        lastError = message;
        AiProvider active = provider;
        if (active == null || !active.isAvailable()) {
            state.set(State.UNAVAILABLE);
        }
        publish(AiStreamEvent.error(message != null ? message : "error desconocido"));
    }

    /** Encola un evento en el hilo de la UI. Nunca se llama desde ahi directamente. */
    private void publish(AiStreamEvent event) {
        dispatcher.dispatch(() -> eventConsumer.accept(event));
    }

    @Override
    public void close() {
        if (!running) {
            return;
        }
        running = false;
        state.set(State.CLOSED);
        AiProvider active = provider;
        if (active != null) {
            try {
                active.close();
            } catch (RuntimeException ignored) {
                // cierre best-effort al apagar el IDE
            }
        }
        executor.shutdownNow();
        permissionExecutor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}