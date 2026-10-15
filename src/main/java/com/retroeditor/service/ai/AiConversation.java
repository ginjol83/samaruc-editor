package com.retroeditor.service.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.retroeditor.controller.ai.AiChatMessage;

/**
 * Construye la conversacion a partir del flujo de eventos normalizados.
 *
 * Vive en la capa de servicio y no en el controlador a proposito: el pegado de
 * {@code TextDelta} es la parte con reglas (fusionar fragmentos contiguos, no crear una
 * fila por token) y merece tests sin JavaFX. El controlador solo pinta lo que devuelve.
 */
public class AiConversation {

    private final List<AiChatMessage> messages = new ArrayList<>();
    private final Consumer<AiConversation> changeListener;

    public AiConversation() {
        this(null);
    }

    public AiConversation(Consumer<AiConversation> changeListener) {
        this.changeListener = changeListener != null ? changeListener : conversation -> { };
    }

    public List<AiChatMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /** Ultimo mensaje del rol indicado, vacio si todavia no hay ninguno. */
    public Optional<AiChatMessage> last(AiChatMessage.Role role) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == role) {
                return Optional.of(messages.get(i));
            }
        }
        return Optional.empty();
    }

    /** Anade un mensaje tal cual. Los vacios se ignoran. */
    public void add(AiChatMessage message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        messages.add(message);
        changed();
    }

    /**
     * Anade un fragmento de texto. Si el ultimo mensaje es del mismo rol se le anade el
     * texto; si no, abre uno nuevo. Es lo que evita ver una fila por cada token.
     */
    public void appendText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int lastIndex = messages.size() - 1;
        if (lastIndex >= 0 && messages.get(lastIndex).role() == AiChatMessage.Role.ASSISTANT) {
            AiChatMessage previous = messages.get(lastIndex);
            messages.set(lastIndex, AiChatMessage.assistant(previous.text() + text));
        } else {
            messages.add(AiChatMessage.assistant(text));
        }
        changed();
    }

    /**
     * Traduce un evento del proveedor a la conversacion. Devuelve {@code true} si el
     * evento aporto algo visible.
     *
     * @param statusText texto ya traducido del evento {@code Status}
     * @param toolText   texto ya traducido del evento {@code ToolStart}, con {0} resuelto
     */
    public boolean accept(AiStreamEvent event, String statusText, String toolText) {
        if (event instanceof AiStreamEvent.TextDelta delta) {
            appendText(delta.text());
            return true;
        }
        if (event instanceof AiStreamEvent.ToolStart tool) {
            add(AiChatMessage.tool(toolText != null ? toolText : tool.title()));
            return true;
        }
        if (event instanceof AiStreamEvent.Status status) {
            add(AiChatMessage.status(statusText != null ? statusText : status.message()));
            return true;
        }
        if (event instanceof AiStreamEvent.Error error) {
            add(AiChatMessage.error(error.message()));
            return true;
        }
        // Done no se pinta: es senal de control, no contenido.
        return false;
    }

    public void clear() {
        if (messages.isEmpty()) {
            return;
        }
        messages.clear();
        changed();
    }

    private void changed() {
        changeListener.accept(this);
    }
}