package com.retroeditor.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para extraer la estructura de símbolos de un archivo fuente (C, C++, ASM)
 * para alimentar el panel de Outline / Structure View.
 *
 * La deteccion es compartida con {@link SourceSymbolScanner}; este servicio solo
 * adapta cada simbolo al formato del panel.
 */
public class StructureViewService {

    public static final class StructureSymbol {
        private final String name;
        private final int line;
        private final int endLine;
        private final String type; // "function", "variable", "macro", "struct", "label", "region"
        private final String signature;

        public StructureSymbol(String name, int line, String type, String signature) {
            this(name, line, line, type, signature);
        }

        public StructureSymbol(String name, int line, int endLine, String type, String signature) {
            this.name = name;
            this.line = line;
            this.endLine = Math.max(line, endLine);
            this.type = type;
            this.signature = signature != null ? signature : name;
        }

        public String getName() { return name; }
        public int getLine() { return line; }
        public int getEndLine() { return endLine; }
        public String getType() { return type; }
        public String getSignature() { return signature; }
    }

    public List<StructureSymbol> getStructure(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<StructureSymbol> symbols = new ArrayList<>();
        for (SourceSymbolScanner.SourceSymbol symbol : SourceSymbolScanner.scan(text)) {
            symbols.add(new StructureSymbol(symbol.getName(), symbol.getStartLine(),
                    symbol.getEndLine(), symbol.getType(), symbol.getSignature()));
        }
        return symbols;
    }

    public static int indexOfSymbolAt(List<StructureSymbol> symbols, int caretLine) {
        if (symbols == null || symbols.isEmpty() || caretLine <= 0) {
            return -1;
        }
        int best = -1;
        int bestStart = Integer.MIN_VALUE;
        int bestEnd = Integer.MAX_VALUE;
        int fallback = -1;
        int fallbackStart = Integer.MIN_VALUE;
        for (int i = 0; i < symbols.size(); i++) {
            StructureSymbol symbol = symbols.get(i);
            int start = symbol.getLine();
            int end = symbol.getEndLine();
            if (start <= caretLine && start > fallbackStart) {
                fallback = i;
                fallbackStart = start;
            }
            if (caretLine < start || caretLine > end) {
                continue;
            }
            if (best < 0 || start > bestStart || (start == bestStart && end < bestEnd)) {
                best = i;
                bestStart = start;
                bestEnd = end;
            }
        }
        return best >= 0 ? best : fallback;
    }
}
