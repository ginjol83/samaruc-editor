package com.retroeditor.service;

/**
 * Refactorización "extraer función" (item 18): mueve el bloque de código
 * seleccionado a una nueva función {@code static} y lo sustituye por una
 * llamada. La nueva función se inserta antes de la función que contenía el
 * bloque original (buscando el final de la sentencia de nivel superior previa).
 * Lógica pura (sin UI) para poder testearla.
 */
public class ExtractFunctionService {

    /** Resultado de la extracción. {@code diagnostic} no vacío indica error (no se modificó nada). */
    public record Result(String newText, int caretPosition, String diagnostic) {}

    /**
     * Extrae la selección {@code [selStart, selEnd)} a una nueva función.
     *
     * @param text       contenido completo del archivo.
     * @param selStart   inicio (exclusive) de la selección.
     * @param selEnd     fin (exclusive) de la selección.
     * @param functionName nombre de la nueva función (identificador C).
     * @param parameters texto de los parámetros (p. ej. {@code "int a, int b"});
     *                   vacío o {@code null} genera {@code void}.
     */
    public Result extract(String text, String functionName, String parameters, int selStart, int selEnd) {
        if (text == null) {
            return new Result(null, -1, "Texto nulo");
        }
        if (functionName == null || !functionName.matches("[A-Za-z_]\\w*")) {
            return new Result(text, -1, "El nombre debe ser un identificador válido (letras, números o guion bajo).");
        }
        int lo = Math.max(0, selStart);
        int hi = Math.min(text.length(), selEnd);
        if (lo >= hi) {
            return new Result(text, -1, "Selecciona el código a extraer a una función.");
        }
        String block = text.substring(lo, hi);
        if (block.isBlank()) {
            return new Result(text, -1, "Selecciona el código a extraer a una función.");
        }

        int[] braceLevels = braceLevelsBefore(text);
        int openBrace = enclosingFunctionOpenBrace(text, braceLevels, lo);
        int insertionOffset = previousTopLevelEnd(text, openBrace);

        String params = (parameters == null || parameters.isBlank()) ? "void" : parameters.trim();
        String bodyCore = block.endsWith("\n") ? block : block + "\n";
        String core = "static void " + functionName + "(" + params + ") {\n" + bodyCore + "}\n";
        String prefix = insertionOffset <= 0 ? "" : "\n";
        String suffix = insertionOffset <= 0 ? "\n" : "";
        String newFunction = prefix + core + suffix;

        String call = functionName + "(" + callArguments(params) + ")";
        String indent = leadingWhitespace(block);
        boolean trailingNl = block.endsWith("\n");
        boolean endsWithSemicolon = block.trim().endsWith(";");
        String replacement = indent + call + (endsWithSemicolon ? ";" : "") + (trailingNl ? "\n" : "");
        String body = text.substring(0, lo) + replacement + text.substring(hi);
        int caret = lo + replacement.length();

        if (insertionOffset < 0) {
            // Sin inserción segura: se sustituye solo el bloque por la llamada.
            return new Result(body, caret, "");
        }

        String newText;
        if (insertionOffset == 0) {
            newText = newFunction + body;
        } else {
            newText = body.substring(0, insertionOffset) + newFunction + body.substring(insertionOffset);
        }
        int newCaret = caret + newFunction.length();
        return new Result(newText, newCaret, "");
    }

    /**
     * Deriva los argumentos de la llamada a partir de la lista de parámetros:
     * toma el último token (el nombre) de cada parámetro. {@code void} o vacío
     * produce llamada sin argumentos.
     */
    private static String callArguments(String params) {
        if (params == null || params.isBlank() || "void".equals(params)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : params.split(",")) {
            String[] tokens = part.trim().split("\\s+");
            if (tokens.length == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(tokens[tokens.length - 1]);
        }
        return sb.toString();
    }

    /** Espacio o tabulaciones al inicio del bloque seleccionado (para la llamada). */
    private static String leadingWhitespace(String block) {
        int i = 0;
        while (i < block.length() && (block.charAt(i) == ' ' || block.charAt(i) == '\t')) {
            i++;
        }
        return block.substring(0, i);
    }

    /** Profundidad de llaves en cada offset: {@code levelAt[i]} = nº de '{' - '}' en {@code text[0..i)}. */
    private static int[] braceLevelsBefore(String text) {
        int n = text.length();
        int[] levels = new int[n];
        int level = 0;
        for (int i = 0; i < n; i++) {
            levels[i] = level;
            char c = text.charAt(i);
            if (c == '{') {
                level++;
            } else if (c == '}') {
                level--;
            }
        }
        return levels;
    }

    /**
     * El {@code {} de nivel superior más cercano a la izquierda de {@code lo}:
     * el que abre el cuerpo de la función envolvente.
     */
    private static int enclosingFunctionOpenBrace(String text, int[] levels, int lo) {
        int best = -1;
        for (int i = Math.max(0, lo - 1); i >= 0; i--) {
            if (text.charAt(i) == '{' && levels[i] == 0) {
                best = i;
                break;
            }
        }
        return best;
    }

    /**
     * Devuelve el offset inmediatamente posterior al fin de la sentencia de nivel
     * superior previa a {@code openBrace}, o -1 si no se puede encontrar un punto
     * de inserción seguro.
     */
    private static int previousTopLevelEnd(String text, int openBrace) {
        if (openBrace < 0) {
            return -1;
        }
        int depth = 0;
        for (int i = openBrace - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '}') {
                if (depth == 0) {
                    return i + 1;
                }
                depth++;
            } else if (c == '{') {
                if (depth > 0) {
                    depth--;
                }
            } else if (c == ';' && depth == 0) {
                return i + 1;
            }
        }
        return 0;
    }
}