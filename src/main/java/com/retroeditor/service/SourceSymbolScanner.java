package com.retroeditor.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Escaner unico de simbolos de fuente (C, C++, ASM) compartido por la vista de
 * estructura ({@link StructureViewService}), el plegado de codigo
 * ({@link CodeFoldingService}) y las migas de pan ({@link SymbolBreadcrumbService}).
 *
 * Cada simbolo lleva su rango {@code [startLine, endLine]} (1-indexado e
 * incluido): las funciones abarcan de la firma al cierre de llaves (o la propia
 * firma si son prototipo), las etiquetas hasta la siguiente, las regiones hasta
 * su cierre (o EOF) y el resto de simbolos son de una sola linea.
 *
 * Se detectan comentarios y cadenas antes de analizar para que ni las llaves de
 * dentro de un comentario descuadren los rangos ni las firmas comentadas
 * generen falsos positivos.
 */
public final class SourceSymbolScanner {

    public static final class SourceSymbol {
        private final String name;
        private final int startLine;
        private final int endLine;
        private final String type; // "function", "variable", "macro", "struct", "label", "region"
        private final String signature;

        public SourceSymbol(String name, int startLine, int endLine, String type, String signature) {
            this.name = name;
            this.startLine = startLine;
            this.endLine = endLine;
            this.type = type;
            this.signature = signature != null ? signature : name;
        }

        public String getName() { return name; }
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public String getType() { return type; }
        public String getSignature() { return signature; }
    }

    private SourceSymbolScanner() {}

    // Tipos explicitos: la alternativa evita confundir `if (x)`/`while (...)` con firmas.
    // Incluye los tipos habituales en C de 8 bits (GBDK/SDCC usan uint8_t, UBYTE, UWORD...).
    private static final String C_TYPE =
        "(?:const|static|inline|extern|volatile|register|restrict|unsigned|signed|"
        + "void|int|short|long|char|float|double|bool|_Bool|"
        + "uint8_t|uint16_t|uint32_t|uint64_t|int8_t|int16_t|int32_t|int64_t|"
        + "size_t|ssize_t|ptrdiff_t|UBYTE|SBYTE|UWORD|SWORD|DWORD|FILE|"
        + "struct\\s+[A-Za-z_]\\w*|union\\s+[A-Za-z_]\\w*|enum\\s+[A-Za-z_]\\w*)";

    private static final Pattern C_FUNC_PATTERN =
        Pattern.compile("\\b" + C_TYPE + "\\s+\\**([A-Za-z_]\\w*)\\s*\\(");
    /** Solo el tipo en una linea: firma multilinea, el nombre va en la siguiente. */
    private static final Pattern C_TYPE_ONLY_PATTERN =
        Pattern.compile("^\\s*" + C_TYPE + "\\s*;?\\s*$");
    /** Continuacion de firma multilinea: `nombre(params)` sin tipo. */
    private static final Pattern C_FUNC_CONTINUATION_PATTERN =
        Pattern.compile("^\\s*\\**([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern DEFINE_PATTERN =
        Pattern.compile("^\\s*#\\s*define\\s+([A-Za-z_]\\w*)");
    private static final Pattern STRUCT_PATTERN =
        Pattern.compile("\\b(?:struct|typedef\\s+struct)\\s+([A-Za-z_]\\w*)");
    private static final Pattern ASM_LABEL_PATTERN =
        Pattern.compile("^\\s*([A-Za-z_.][A-Za-z0-9_]*|[0-9]+)\\s*:");
    private static final Pattern GLOBAL_VAR_PATTERN = Pattern.compile(
        "^(?:int|char|long|float|double|unsigned|const|static|volatile)\\s+(?:\\**)?([A-Za-z_]\\w*)\\s*(?:\\[[^\\]]*\\])?\\s*(?:=.*;|;)"
    );
    // Regiones ancladas al inicio de linea (con prefijo de comentario opcional):
    // una frase que contenga la palabra "region" a mitad de linea no crea simbolo.
    private static final Pattern REGION_START_PATTERN =
        Pattern.compile("^\\s*(?:(?://|;|#)\\s*)*(?:#\\s*region|region)\\s+(.+)");
    private static final Pattern REGION_END_PATTERN =
        Pattern.compile("^\\s*(?:(?://|;|#)\\s*)*(?:#\\s*endregion|endregion)\\b");

    private static final int MAX_SIGNATURE_LINES = 10;

    private record PendingLabel(String name, int line) {}

    /** Extrae todos los simbolos con su rango, ordenados por linea de inicio. */
    public static List<SourceSymbol> scan(String text) {
        if (text == null || text.isEmpty()) return List.of();
        String[] lines = text.split("\\r?\n", -1);
        String[] code = stripCommentsAndStrings(lines);
        int lastLine = lines.length;

        List<SourceSymbol> symbols = new ArrayList<>();
        List<int[]> functionRanges = new ArrayList<>();
        List<PendingLabel> labels = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1;
            if (REGION_START_PATTERN.matcher(lines[i]).find()) continue; // resuelta en su pasada
            String lineCode = code[i].trim();
            if (lineCode.isEmpty()) continue;

            Matcher macro = DEFINE_PATTERN.matcher(code[i]);
            if (macro.find()) {
                symbols.add(new SourceSymbol(macro.group(1), lineNum, lineNum, "macro", lines[i].trim()));
                continue;
            }
            Matcher func = C_FUNC_PATTERN.matcher(code[i]);
            if (func.find()) {
                int end = findFunctionEnd(code, i);
                symbols.add(new SourceSymbol(func.group(1), lineNum, end, "function", lines[i].trim()));
                functionRanges.add(new int[] {lineNum, end});
                continue;
            }
            if (C_TYPE_ONLY_PATTERN.matcher(lineCode).matches()) {
                int cont = nextNonBlank(code, i + 1);
                if (cont >= 0) {
                    Matcher contM = C_FUNC_CONTINUATION_PATTERN.matcher(code[cont].trim());
                    if (contM.find()) {
                        int end = findFunctionEnd(code, i);
                        symbols.add(new SourceSymbol(contM.group(1), lineNum, end, "function", lines[cont].trim()));
                        functionRanges.add(new int[] {lineNum, end});
                        continue;
                    }
                }
                // sin continuacion puede seguir siendo una declaracion de tipo (`struct Player;`)
            }
            Matcher struct = STRUCT_PATTERN.matcher(code[i]);
            if (struct.find()) {
                symbols.add(new SourceSymbol(struct.group(1), lineNum, lineNum, "struct", lines[i].trim()));
                continue;
            }
            Matcher label = ASM_LABEL_PATTERN.matcher(code[i]);
            if (label.find()) {
                labels.add(new PendingLabel(label.group(1), lineNum));
                continue;
            }
            Matcher var = GLOBAL_VAR_PATTERN.matcher(code[i]);
            if (var.find()) {
                symbols.add(new SourceSymbol(var.group(1), lineNum, lineNum, "variable", lines[i].trim()));
            }
        }

        addRegions(lines, symbols);
        addLabels(symbols, labels, functionRanges, lastLine);
        symbols.sort(Comparator.comparingInt(SourceSymbol::getStartLine));
        return symbols;
    }

    /**
     * Fin de una funcion: primera llave de apertura a nivel de parentesis y su
     * cierre correspondiente. Sin cuerpo (prototipo o firma suelta) el rango es
     * la propia linea de la firma.
     */
    private static int findFunctionEnd(String[] code, int startIdx) {
        int depthParen = 0;
        boolean entered = false;
        int depth = 0;
        int limit = Math.min(code.length, startIdx + MAX_SIGNATURE_LINES);
        for (int i = startIdx; i < limit; i++) {
            String line = code[i];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '(') depthParen++;
                else if (ch == ')') { if (depthParen > 0) depthParen--; }
                else if (depthParen == 0 && ch == '{') {
                    entered = true;
                    depth++;
                } else if (depthParen == 0 && ch == '}') {
                    if (depth > 0) depth--;
                    if (entered && depth == 0) return i + 1;
                } else if (depthParen == 0 && ch == ';' && !entered) {
                    return startIdx + 1; // prototipo sin cuerpo
                }
            }
            if (entered && depth == 0) return i + 1;
        }
        return startIdx + 1;
    }

    /** Regiones `#region`/`; region` con pila; una region sin cierre llega a EOF. */
    private static void addRegions(String[] lines, List<SourceSymbol> symbols) {
        List<Integer> stack = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher start = REGION_START_PATTERN.matcher(lines[i]);
            if (start.find()) {
                stack.add(i + 1);
                names.add(start.group(1).trim());
                continue;
            }
            if (!stack.isEmpty() && REGION_END_PATTERN.matcher(lines[i]).find()) {
                int startLine = stack.remove(stack.size() - 1);
                String name = names.remove(names.size() - 1);
                symbols.add(new SourceSymbol(name, startLine, i + 1, "region", name));
            }
        }
        while (!stack.isEmpty()) {
            int startLine = stack.remove(stack.size() - 1);
            String name = names.remove(names.size() - 1);
            symbols.add(new SourceSymbol(name, startLine, lines.length, "region", name));
        }
    }

    /**
     * Cada etiqueta cubre hasta la siguiente (o hasta EOF). Las etiquetas internas
     * de una funcion en C (`retry:` de un goto, `default:` de un switch) no son
     * simbolos navegables: se descartan.
     */
    private static void addLabels(List<SourceSymbol> symbols, List<PendingLabel> labels,
                                  List<int[]> functionRanges, int lastLine) {
        labels.sort(Comparator.comparingInt(PendingLabel::line));
        for (int i = 0; i < labels.size(); i++) {
            PendingLabel label = labels.get(i);
            boolean insideFunction = false;
            for (int[] range : functionRanges) {
                if (range[0] < label.line() && label.line() <= range[1]) {
                    insideFunction = true;
                    break;
                }
            }
            if (insideFunction) continue;
            int end = i + 1 < labels.size() ? labels.get(i + 1).line() - 1 : lastLine;
            symbols.add(new SourceSymbol(label.name(), label.line(), end, "label", label.name()));
        }
    }

    private static int nextNonBlank(String[] code, int from) {
        for (int i = from; i < code.length; i++) {
            if (!code[i].trim().isEmpty()) return i;
        }
        return -1;
    }

    /**
     * Elimina comentarios y contenido de cadenas para que ni las llaves de dentro
     * de un comentario descuadren un rango ni las firmas escritas en un comentario
     * generen falsos positivos. Se procesa en orden para llevar el estado de los
     * comentarios de bloque multilínea.
     */
    static String[] stripCommentsAndStrings(String[] lines) {
        String[] code = new String[lines.length];
        boolean[] inBlock = {false};
        for (int i = 0; i < lines.length; i++) {
            code[i] = stripLine(lines[i], inBlock);
        }
        return code;
    }

    private static String stripLine(String line, boolean[] inBlock) {
        StringBuilder out = new StringBuilder(line.length());
        int i = 0;
        int n = line.length();
        boolean inString = false;
        char quote = 0;
        while (i < n) {
            char c = line.charAt(i);
            if (inBlock[0]) {
                int end = line.indexOf("*/", i);
                if (end < 0) return out.toString();
                inBlock[0] = false;
                i = end + 2;
                continue;
            }
            if (inString) {
                if (c == '\\' && i + 1 < n) { i += 2; continue; }
                if (c == quote) { inString = false; out.append(c); }
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') return out.toString();
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') { inBlock[0] = true; i += 2; continue; }
            if (c == '"' || c == '\'') { inString = true; quote = c; out.append(c); i++; continue; }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
