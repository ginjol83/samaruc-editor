package com.retroeditor.service.ai;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Contrato minimo de un proveedor de asistencia IA.
 *
 * Se mantiene deliberadamente pequeno (arrancar, crear sesion, enviar prompt, abortar,
 * cerrar) porque solo hay un proveedor real hoy (opencode) pero la UI no debe depender de
 * el: si se Incorporan Claude Code, Copilot u Ollama, se implementa esta interfaz y la
 * capa de arriba no se toca.
 *
 * Reglas de implementacion:
 * - Los identificadores de sesion son opacos: nunca asumir donde los guarda el proveedor.
 * - Los eventos se emiten ya normalizados a {@link AiStreamEvent}.
 * - Ninguna llamada debe bloquear el hilo de JavaFX: las implementaciones trabajan en
 *   hilos propios y notifican por el {@link AiEventSink}.
 */
public interface AiProvider extends AutoCloseable {

    /** Identificador estable del proveedor ("opencode"). Se muestra en la UI. */
    String id();

    /** Si el proveedor puede usarse ahora mismo (binario presente, etc.). */
    boolean isAvailable();

    /** Explicacion de por que no esta disponible, o {@code null} si si lo esta. */
    String unavailableReason();

    /**
     * Prepara el proveedor para trabajar sobre un workspace.
     *
     * @param workspace directorio del proyecto (el cwd del agente)
     * @param sink      destino de los eventos normalizados
     */
    void start(AiWorkspace workspace, AiEventSink sink) throws AiException;

    /** Crea una sesion y devuelve su identificador opaco. */
    String createSession() throws AiException;

    /** Envia un prompt a una sesion ya creada. */
    void prompt(String sessionId, String prompt) throws AiException;

    /** Interrumpe lo que este en curso sin cerrar la sesion. */
    void abort(String sessionId);

    /**
     * Responde a una peticion de permiso del proveedor (por ejemplo el {@code bash}
     * restringido de opencode). Sin implementacion por defecto: un proveedor sin flujo
     * de permisos simplemente no lo llama.
     *
     * @param requestId el {@code requestId} del {@link AiStreamEvent.PermissionRequest}
     */
    default void replyPermission(String requestId, PermissionReply reply) throws AiException {
    }

    /** Como responder a un permiso: una vez, siempre o no. */
    enum PermissionReply {
        ONCE,
        ALWAYS,
        REJECT
    }

    @Override
    void close();

    /** Workspace sobre el que trabaja el agente. */
    record AiWorkspace(Path directory) {
        public AiWorkspace {
            directory = directory != null ? directory.toAbsolutePath().normalize() : null;
        }

        public static AiWorkspace of(Path directory) {
            return new AiWorkspace(directory);
        }
    }

    /** Destino de eventos normalizados. */
    @FunctionalInterface
    interface AiEventSink extends Consumer<AiStreamEvent> {
        @Override
        void accept(AiStreamEvent event);
    }

    /** Fallo de un proveedor, distinguido de un error de programacion. */
    class AiException extends Exception {
        public AiException(String message) {
            super(message);
        }

        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}