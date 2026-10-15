package com.retroeditor.service.ai;

import java.nio.file.Path;
import java.util.Properties;

/**
 * Ajustes de la integracion con opencode.
 *
 * No se guardan aqui credenciales: opencode autentica al usuario por su cuenta
 * ({@code opencode auth}) y SamaruC nunca ve esas claves. Lo que si se guarda es la
 * configuracion del workspace y los limites de contexto.
 *
 * @param cliPath           ruta explicita del ejecutable; {@code null} = autodeteccion en el PATH
 * @param port              puerto de {@code opencode serve}; {@code 0} = que lo elija opencode (aleatorio)
 * @param mode              modo de transporte preferido
 * @param workingDirectory  workspace por defecto; {@code null} = directorio del proyecto abierto
 * @param maxContextKb      tope de contexto por fichero incluido en un prompt
 * @param maxFilesPerPrompt tope de ficheros adjuntos a un prompt
 * @param maxLogLines       tope de lineas de log de compilacion incluidas en un prompt
 */
public record AiSettings(
        String cliPath,
        int port,
        Mode mode,
        Path workingDirectory,
        int maxContextKb,
        int maxFilesPerPrompt,
        int maxLogLines
) {
    /** Modos de transporte. SERVE es el canal principal; RUN es para tareas sueltas. */
    public enum Mode {
        SERVE,
        RUN
    }

    public static final String KEY_CLI_PATH = "ai.cli_path";
    public static final String KEY_PORT = "ai.port";
    public static final String KEY_MODE = "ai.mode";
    public static final String KEY_WORKING_DIRECTORY = "ai.working_directory";
    public static final String KEY_MAX_CONTEXT_KB = "ai.max_context_kb";
    public static final String KEY_MAX_FILES = "ai.max_files_per_prompt";
    public static final String KEY_MAX_LOG_LINES = "ai.max_log_lines";

    public static final int DEFAULT_MAX_CONTEXT_KB = 64;
    public static final int DEFAULT_MAX_FILES = 5;
    public static final int DEFAULT_MAX_LOG_LINES = 40;

    private static final int MAX_CONTEXT_KB_LIMIT = 4096;
    private static final int MAX_FILES_LIMIT = 100;
    private static final int MAX_LOG_LINES_LIMIT = 2000;

    public AiSettings {
        port = clamp(port, 0, 65535, 0);
        maxContextKb = clamp(maxContextKb, 1, MAX_CONTEXT_KB_LIMIT, DEFAULT_MAX_CONTEXT_KB);
        maxFilesPerPrompt = clamp(maxFilesPerPrompt, 1, MAX_FILES_LIMIT, DEFAULT_MAX_FILES);
        maxLogLines = clamp(maxLogLines, 1, MAX_LOG_LINES_LIMIT, DEFAULT_MAX_LOG_LINES);
        mode = mode != null ? mode : Mode.SERVE;
        cliPath = cliPath != null && !cliPath.isBlank() ? cliPath.trim() : null;
        workingDirectory = workingDirectory != null ? workingDirectory.toAbsolutePath().normalize() : null;
    }

    public static AiSettings defaults() {
        return new AiSettings(null, 0, Mode.SERVE, null,
                DEFAULT_MAX_CONTEXT_KB, DEFAULT_MAX_FILES, DEFAULT_MAX_LOG_LINES);
    }

    /** Copia con otro workspace (el proyecto que este abierto en el momento). */
    public AiSettings withWorkingDirectory(Path directory) {
        return new AiSettings(cliPath, port, mode, directory, maxContextKb, maxFilesPerPrompt, maxLogLines);
    }

    public Properties toProperties() {
        Properties props = new Properties();
        if (cliPath != null) props.setProperty(KEY_CLI_PATH, cliPath);
        props.setProperty(KEY_PORT, Integer.toString(port));
        props.setProperty(KEY_MODE, mode.name());
        if (workingDirectory != null) props.setProperty(KEY_WORKING_DIRECTORY, workingDirectory.toString());
        props.setProperty(KEY_MAX_CONTEXT_KB, Integer.toString(maxContextKb));
        props.setProperty(KEY_MAX_FILES, Integer.toString(maxFilesPerPrompt));
        props.setProperty(KEY_MAX_LOG_LINES, Integer.toString(maxLogLines));
        return props;
    }

    /**
     * Lee los ajustes de un {@link Properties}. Un valor ausente o corrupto cae al
     * defecto en vez de fallar: un fichero de preferencias danado no debe impedir
     * abrir el IDE.
     */
    public static AiSettings fromProperties(Properties props) {
        if (props == null) return defaults();
        return new AiSettings(
                props.getProperty(KEY_CLI_PATH),
                readInt(props, KEY_PORT, 0),
                readMode(props.getProperty(KEY_MODE)),
                readPath(props.getProperty(KEY_WORKING_DIRECTORY)),
                readInt(props, KEY_MAX_CONTEXT_KB, DEFAULT_MAX_CONTEXT_KB),
                readInt(props, KEY_MAX_FILES, DEFAULT_MAX_FILES),
                readInt(props, KEY_MAX_LOG_LINES, DEFAULT_MAX_LOG_LINES));
    }

    private static int readInt(Properties props, String key, int fallback) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Mode readMode(String raw) {
        if (raw == null || raw.isBlank()) return Mode.SERVE;
        try {
            return Mode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Mode.SERVE;
        }
    }

    private static Path readPath(String raw) {
        return raw == null || raw.isBlank() ? null : Path.of(raw.trim());
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) return fallback;
        return value;
    }
}