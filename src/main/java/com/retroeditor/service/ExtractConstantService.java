package com.retroeditor.service;

/**
 * Refactorización "extraer constante" (item 18): convierte la expresión
 * seleccionada en una macro {@code #define} al inicio del archivo y la sustituye
 * por el nombre de la constante. Lógica pura (sin UI) para poder testearla.
 */
public class ExtractConstantService {

    /** Resultado de la extracción. {@code diagnostic} no vacío indica error (no se modificó nada). */
    public record Result(String newText, int caretPosition, String diagnostic) {}

    /**
     * Extrae la selección {@code [selStart, selEnd)} como una constante con el
     * nombre dado.
     *
     * @param text     contenido completo del archivo.
     * @param selStart inicio (exclusive) de la selección.
     * @param selEnd   fin (exclusive) de la selección.
     * @param name     nombre de la constante (identificador C).
     */
    public Result extract(String text, String name, int selStart, int selEnd) {
        if (text == null) {
            return new Result(null, -1, "Texto nulo");
        }
        if (name == null || !name.matches("[A-Za-z_]\\w*")) {
            return new Result(text, -1, "El nombre debe ser un identificador válido (letras, números o guion bajo).");
        }
        int lo = Math.max(0, selStart);
        int hi = Math.min(text.length(), selEnd);
        if (lo >= hi) {
            return new Result(text, -1, "Selecciona una expresión para extraerla como constante.");
        }
        String expr = text.substring(lo, hi).trim();
        if (expr.isEmpty()) {
            return new Result(text, -1, "Selecciona una expresión para extraerla como constante.");
        }
        if (expr.indexOf('\n') >= 0 || expr.indexOf('\r') >= 0) {
            return new Result(text, -1, "Selecciona una expresión en una sola línea.");
        }

        String directive = "#define " + name + " " + expr + "\n";
        String body = text.substring(0, lo) + name + text.substring(hi);
        return new Result(directive + body, directive.length() + lo + name.length(), "");
    }
}