package com.retroeditor.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Construye los prompts que se envian al agente anadiendo el contexto del editor.
 *
 * Vive en la capa de servicio y es deliberadamente tonto: recibe datos ya resueltos (fichero,
 * seleccion, log de compilacion, toolchain) y devuelve texto. No sabe nada de opencode, de
 * JavaFX ni de como se detecto la plataforma, asi que se testea sin mocks.
 *
 * Reglas:
 * - Los topes de contexto salen de {@link AiSettings}: un log de compilacion de 10.000 lineas
 *   se recorta a las ultimas, que son las que explican el fallo actual.
 * - Un fichero se recorta por tamano, no se rechaza: es preferible un contexto parcial a un
 *   prompt vacio.
 */
public class AiPromptBuilder {

    private final AiSettings settings;

    public AiPromptBuilder() {
        this(AiSettings.defaults());
    }

    public AiPromptBuilder(AiSettings settings) {
        this.settings = settings != null ? settings : AiSettings.defaults();
    }

    /**
     * Contexto disponible para un prompt. Todos los campos son opcionales: con ninguno el
     * prompt es la peticion tal cual.
     *
     * @param fileName     nombre del fichero abierto
     * @param selection    texto seleccionado en el editor
     * @param buildLog     ultimas lineas de la consola de compilacion
     * @param toolchain    compilador/perfil configurado (gbdk, z88dk-spectrum, cc65...)
     * @param platform     plataforma detectada del proyecto
     */
    public record Context(
            String fileName,
            String selection,
            String buildLog,
            String toolchain,
            String platform
    ) {
        public Context {
            fileName = trimToNull(fileName);
            selection = trimToNull(selection);
            buildLog = trimToNull(buildLog);
            toolchain = trimToNull(toolchain);
            platform = trimToNull(platform);
        }

        public static Context empty() {
            return new Context(null, null, null, null, null);
        }

        public boolean hasSelection() {
            return selection != null;
        }

        public boolean hasBuildLog() {
            return buildLog != null;
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.strip();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    /**
     * Prompt para "explicar este error de compilacion". Es la accion de mayor valor del IDE:
     * en lugar de texto suelto se manda el log real junto al toolchain que lo produjo.
     */
    public String explainCompilationError(Context context, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Explica el error de compilacion de este proyecto de 8 bits.\n");
        prompt.append("Causa concreta, por que ocurre en esta plataforma y el cambio minimo que lo arregla.\n");
        appendToolchain(prompt, context);
        if (context != null && context.fileName() != null) {
            prompt.append("\nFichero: ").append(context.fileName()).append('\n');
        }
        appendBuildLog(prompt, context);
        if (context != null && context.selection() != null) {
            appendSelection(prompt, context);
        }
        appendQuestion(prompt, question);
        return prompt.toString();
    }

    /** Prompt para "explica esta seleccion": el codigo va integro si cabe. */
    public String explainSelection(Context context, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Explica este codigo de un proyecto de 8 bits.\n");
        prompt.append("Que hace, que registros de la maquina usa y que limites hay que respetar.\n");
        appendToolchain(prompt, context);
        if (context != null && context.fileName() != null) {
            prompt.append("\nFichero: ").append(context.fileName()).append('\n');
        }
        appendSelection(prompt, context);
        appendQuestion(prompt, question);
        return prompt.toString();
    }

    /** Peticion libre del panel de chat, con el contexto minimo del proyecto. */
    public String chat(Context context, String question) {
        StringBuilder prompt = new StringBuilder();
        appendToolchain(prompt, context);
        if (context != null && context.fileName() != null) {
            prompt.append("\nFichero abierto: ").append(context.fileName()).append('\n');
        }
        prompt.append("\nPeticion: ").append(question != null ? question.strip() : "").append('\n');
        return prompt.toString();
    }

    // ---------------------------------------------------------------- contexto

    private void appendToolchain(StringBuilder prompt, Context context) {
        if (context == null) {
            return;
        }
        List<String> facts = new ArrayList<>();
        if (context.platform() != null) {
            facts.add("plataforma: " + context.platform());
        }
        if (context.toolchain() != null) {
            facts.add("toolchain: " + context.toolchain());
        }
        if (!facts.isEmpty()) {
            prompt.append('\n').append(String.join(" | ", facts)).append('\n');
        }
    }

    private void appendBuildLog(StringBuilder prompt, Context context) {
        if (context == null || !context.hasBuildLog()) {
            return;
        }
        prompt.append("\nSalida de compilacion (ultimas lineas):\n```\n")
                .append(tail(context.buildLog(), settings.maxLogLines()))
                .append("\n```\n");
    }

    private void appendSelection(StringBuilder prompt, Context context) {
        if (context == null || !context.hasSelection()) {
            return;
        }
        prompt.append("\nCodigo seleccionado:\n```\n")
                .append(clampKb(context.selection(), settings.maxContextKb()))
                .append("\n```\n");
    }

    private void appendQuestion(StringBuilder prompt, String question) {
        Optional<String> extra = Optional.ofNullable(question).map(String::strip).filter(q -> !q.isEmpty());
        extra.ifPresent(q -> prompt.append("\n").append(q).append('\n'));
    }

    /** Ultimas {@code max} lineas: en un log de compilación el final es el que explica el fallo. */
    static String tail(String text, int max) {
        if (text == null || text.isBlank() || max <= 0) {
            return "";
        }
        List<String> lines = new ArrayList<>(List.of(text.replace("\r\n", "\n").split("\n", -1)));
        if (lines.size() <= max) {
            return String.join("\n", lines).strip();
        }
        return String.join("\n", lines.subList(lines.size() - max, lines.size())).strip();
    }

    /** Recorta por tamano con un aviso, para que el agente sepa que el texto esta incompleto. */
    static String clampKb(String text, int maxKb) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int limit = Math.max(1, maxKb) * 1024;
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "\n[contexto recortado por limite del IDE]";
    }
}