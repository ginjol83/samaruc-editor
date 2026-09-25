package com.retroeditor.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de snippets con tabstops (item 15 del plan), estilo TextMate simplificado:
 * <ul>
 *   <li>{@code $0} — posición final del cursor al terminar el snippet.</li>
 *   <li>{@code $1}..{@code $9} — tabstops navegables con Tab/Shift+Tab.</li>
 *   <li>{@code ${n:default}} — tabstop con texto por defecto (se preselecciona al llegar).</li>
 *   <li>{@code $$} — signo {@code $} literal.</li>
 * </ul>
 *
 * <p>Persistencia al analizar de forma pura (sin UI) para poder probarlo en tests:
 * {@link #parse} devuelve el fragmento expandido y {@link #project} lo sitúa dentro
 * de un documento concreto calculando posiciones absolutas.</p>
 */
public class SnippetService {

    /** Un tabstop ya posicionado en un documento. */
    public record TabStop(int number, int start, int end, String defaultValue) {}

    /** Resultado de analizar una plantilla (offsets relativos al fragmento). */
    public record Parsed(String fragment, List<TabStop> tabStops, int finalCaret) {}

    /** Resultado de proyectar un snippet en un documento (offsets absolutos). */
    public record Insertion(String fullText, List<TabStop> tabStops, int finalCaret) {}

    /** Snippet con nombre y cuerpo. */
    public record NamedSnippet(String name, String template) {}

    /**
     * Analiza una plantilla separando el texto final de los marcadores de tabstop.
     * La navegación recorre los tabstops en orden de primera aparición; {@code $0}
     * (o el final del fragmento) marca la posición final del cursor.
     */
    public Parsed parse(String template) {
        if (template == null) template = "";
        StringBuilder out = new StringBuilder();
        Map<Integer, TabStop> seen = new LinkedHashMap<>();
        int finalCaret = -1;

        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c != '$') {
                out.append(c);
                continue;
            }

            // $$ → literal
            if (i + 1 < template.length() && template.charAt(i + 1) == '$') {
                out.append('$');
                i++;
                continue;
            }

            // ${n:default}
            if (i + 1 < template.length() && template.charAt(i + 1) == '{') {
                int close = template.indexOf('}', i + 2);
                if (close > 0) {
                    String inner = template.substring(i + 2, close);
                    int colon = inner.indexOf(':');
                    String numStr = colon >= 0 ? inner.substring(0, colon) : inner;
                    String def = colon >= 0 ? inner.substring(colon + 1) : "";
                    Integer num = toInt(numStr.trim());
                    if (num != null) {
                        int start = out.length();
                        out.append(def);
                        int end = out.length();
                        if (num == 0) {
                            finalCaret = start;
                        } else {
                            seen.putIfAbsent(num, new TabStop(num, start, end, def));
                        }
                        i = close;
                        continue;
                    }
                    // No es un nº válido: se descarta el marcador, se conserva el texto.
                    out.append(inner);
                    i = close;
                    continue;
                }
                out.append(c);
                continue;
            }

            // $n
            if (i + 1 < template.length() && Character.isDigit(template.charAt(i + 1))) {
                int num = template.charAt(i + 1) - '0';
                int start = out.length();
                if (num == 0) {
                    finalCaret = start;
                } else {
                    seen.putIfAbsent(num, new TabStop(num, start, start, ""));
                }
                i++;
                continue;
            }

            out.append(c);
        }

        if (finalCaret < 0) {
            finalCaret = out.length();
        }
        return new Parsed(out.toString(), new ArrayList<>(seen.values()), finalCaret);
    }

    /**
     * Reemplaza el rango [replaceStart, replaceEnd) del documento por el snippet
     * expandido y devuelve el texto completo resultante, los tabstops con offsets
     * absolutos y la posición final del cursor.
     */
    public Insertion project(String fullText, int replaceStart, int replaceEnd, String template) {
        Parsed parsed = parse(template);
        String before = fullText == null ? "" : fullText;
        String safeBefore = before.substring(0, Math.max(0, Math.min(replaceStart, before.length())));
        String safeAfter = before.substring(Math.max(0, Math.min(replaceEnd, before.length())));
        String full = safeBefore + parsed.fragment() + safeAfter;

        int base = safeBefore.length();
        List<TabStop> absolute = new ArrayList<>(parsed.tabStops().size());
        for (TabStop t : parsed.tabStops()) {
            absolute.add(new TabStop(t.number(), base + t.start(), base + t.end(), t.defaultValue()));
        }
        return new Insertion(full, absolute, base + parsed.finalCaret());
    }

    private Integer toInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}