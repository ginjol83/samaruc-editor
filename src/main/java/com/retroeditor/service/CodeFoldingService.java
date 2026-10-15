package com.retroeditor.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para detectar bloques plegables ({ } y regiones ASM/#region) en código fuente.
 *
 * Las regiones provienen de {@link SourceSymbolScanner} (mismo analisis que la vista
 * de estructura y las migas de pan); las llaves se cuentan sobre el codigo con
 * comentarios y cadenas eliminados para no plegar llaves dentro de un comentario.
 */
public class CodeFoldingService {

    public static final class FoldRange {
        private final int startLine; // 1-indexed
        private final int endLine;   // 1-indexed
        private final String title;

        public FoldRange(int startLine, int endLine, String title) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.title = title;
        }

        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public String getTitle() { return title; }
    }

    public List<FoldRange> detectFoldRanges(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<FoldRange> ranges = new ArrayList<>();
        String[] lines = text.split("\\r?\n", -1);
        String[] code = SourceSymbolScanner.stripCommentsAndStrings(lines);

        Map<Integer, String> openingTitles = new HashMap<>();
        for (SourceSymbolScanner.SourceSymbol symbol : SourceSymbolScanner.scan(text)) {
            switch (symbol.getType()) {
                case "function" -> openingTitles.put(symbol.getStartLine(), symbol.getName() + "()");
                case "struct" -> openingTitles.put(symbol.getStartLine(), "struct " + symbol.getName());
                case "region" -> {
                    if (symbol.getEndLine() > symbol.getStartLine()) {
                        ranges.add(new FoldRange(symbol.getStartLine(), symbol.getEndLine(),
                                "#region " + symbol.getName()));
                    }
                }
                default -> { }
            }
        }

        List<Integer> braceStack = new ArrayList<>();
        for (int i = 0; i < code.length; i++) {
            String line = code[i];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '{') {
                    braceStack.add(i + 1);
                } else if (ch == '}' && !braceStack.isEmpty()) {
                    int startLine = braceStack.remove(braceStack.size() - 1);
                    int endLine = i + 1;
                    if (endLine > startLine) {
                        String title = openingTitles.getOrDefault(startLine, "{...}");
                        ranges.add(new FoldRange(startLine, endLine, title));
                    }
                }
            }
        }

        ranges.sort((a, b) -> Integer.compare(a.startLine, b.startLine));
        return ranges;
    }
}
