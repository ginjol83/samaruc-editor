package com.retroeditor.service.ai;

import java.util.List;

/**
 * Eventos normalizados que produce cualquier proveedor de asistencia IA.
 *
 * El objetivo es que la UI (panel de chat, acciones del editor) solo sepa pintar estos
 * casos y nunca use el nombre interno de una herramienta concreta de opencode
 * ({@code bash}, {@code read}...). Si manana se anade Claude Code u Ollama, sus eventos
 * se traducen aqui y el panel no cambia.
 */
public sealed interface AiStreamEvent {

    /** Fragmento de texto de la respuesta, para pintarlo incrementalmente. */
    record TextDelta(String text) implements AiStreamEvent {
        public TextDelta {
            text = text != null ? text : "";
        }
    }

    /** El agente ha empezado a usar una herramienta. */
    record ToolStart(String tool, String title) implements AiStreamEvent {
        public ToolStart {
            tool = tool != null ? tool : "";
            title = title != null ? title : "";
        }
    }

    /** Aviso de estado: arrancando, pensando, esperando permiso... */
    record Status(String message) implements AiStreamEvent {
        public Status {
            message = message != null ? message : "";
        }
    }

    /** La respuesta ha terminado bien. */
    record Done() implements AiStreamEvent {
    }

    /**
     * El agente pide permiso para una accion restringida (bash, escribir fichero...).
     * Hasta que la UI responda con {@code AiService.replyPermission} el turno sigue
     * esperando: si nadie contesta, opencode lo resuelve solo al timeout.
     *
     * @param requestId identificador opaco que hay que devolver en la respuesta
     * @param permission tipo de permiso ("bash", "edit"...), llega tal cual del proveedor
     * @param patterns  detalles concretos (comandos, rutas), tal cual del proveedor
     */
    record PermissionRequest(String requestId, String permission, List<String> patterns)
            implements AiStreamEvent {
        public PermissionRequest {
            requestId = requestId != null ? requestId : "";
            permission = permission != null ? permission : "";
            patterns = patterns != null ? List.copyOf(patterns) : List.of();
        }
    }

    /** La peticion ha fallado. */
    record Error(String message, boolean retryable) implements AiStreamEvent {
        public Error {
            message = message != null ? message : "";
        }
    }

    static AiStreamEvent textDelta(String text) {
        return new TextDelta(text);
    }

    static AiStreamEvent status(String message) {
        return new Status(message);
    }

    static AiStreamEvent error(String message) {
        return new Error(message, true);
    }
}