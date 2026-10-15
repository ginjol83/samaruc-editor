package com.retroeditor.controller.ai;

import java.util.Objects;

/**
 * Vista de una fila del chat. Solo datos que la UI pinta: el controlador decide el color
 * y el icono segun el rol, sin saber nada de opencode.
 */
public record AiChatMessage(Role role, String text) {

    public enum Role {
        USER,
        ASSISTANT,
        STATUS,
        TOOL,
        ERROR
    }

    public AiChatMessage {
        Objects.requireNonNull(role, "role");
        text = text != null ? text : "";
    }

    public static AiChatMessage user(String text) {
        return new AiChatMessage(Role.USER, text);
    }

    public static AiChatMessage assistant(String text) {
        return new AiChatMessage(Role.ASSISTANT, text);
    }

    public static AiChatMessage status(String text) {
        return new AiChatMessage(Role.STATUS, text);
    }

    public static AiChatMessage tool(String text) {
        return new AiChatMessage(Role.TOOL, text);
    }

    public static AiChatMessage error(String text) {
        return new AiChatMessage(Role.ERROR, text);
    }

    public boolean isEmpty() {
        return text.isBlank();
    }
}