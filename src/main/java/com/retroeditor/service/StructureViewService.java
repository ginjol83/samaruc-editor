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
        private final String type; // "function", "variable", "macro", "struct", "label", "region"
        private final String signature;

        public StructureSymbol(String name, int line, String type, String signature) {
            this.name = name;
            this.line = line;
            this.type = type;
            this.signature = signature != null ? signature : name;
        }

        public String getName() { return name; }
        public int getLine() { return line; }
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
                    symbol.getType(), symbol.getSignature()));
        }
        return symbols;
    }
}
