package com.retroeditor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para analizar símbolos y generar la ruta de migas de pan (breadcrumb)
 * según la posición del cursor en el editor.
 */
public class SymbolBreadcrumbService {

    public static final class BreadcrumbItem {
        private final String name;
        private final int line;
        private final String type; // "file", "function", "region", "label"

        public BreadcrumbItem(String name, int line, String type) {
            this.name = name;
            this.line = line;
            this.type = type;
        }

        public String getName() { return name; }
        public int getLine() { return line; }
        public String getType() { return type; }
    }

    private static final Pattern C_FUNC_PATTERN = Pattern.compile(
        "\\b(?:int|void|char|long|float|double|unsigned|static|inline|extern|_Bool|bool|struct\\s+\\w+)\\s+\\**([A-Za-z_]\\w*)\\s*\\("
    );
    private static final Pattern ASM_LABEL_PATTERN = Pattern.compile("^\\s*([A-Za-z_.][A-Za-z0-9_]*)\\s*:");
    private static final Pattern REGION_PATTERN = Pattern.compile("(?i)(?:#\\s*region|region)\\s+(.+)");

    public List<BreadcrumbItem> getBreadcrumbs(String text, String fileName, int caretLine) {
        if (text == null) text = "";
        List<BreadcrumbItem> items = new ArrayList<>();
        items.add(new BreadcrumbItem(fileName != null ? fileName : "editor", 1, "file"));

        String[] lines = text.split("\\r?\\n", -1);
        List<SymbolContext> symbols = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String lineText = lines[i];
            int lineNum = i + 1;

            Matcher regMatcher = REGION_PATTERN.matcher(lineText);
            if (regMatcher.find()) {
                symbols.add(new SymbolContext(regMatcher.group(1).trim(), lineNum, "region"));
            } else {
                Matcher funcMatcher = C_FUNC_PATTERN.matcher(lineText);
                if (funcMatcher.find()) {
                    symbols.add(new SymbolContext(funcMatcher.group(1), lineNum, "function"));
                } else {
                    Matcher asmMatcher = ASM_LABEL_PATTERN.matcher(lineText);
                    if (asmMatcher.find()) {
                        symbols.add(new SymbolContext(asmMatcher.group(1), lineNum, "label"));
                    }
                }
            }
        }

        for (SymbolContext sym : symbols) {
            if (sym.line <= caretLine) {
                items.add(new BreadcrumbItem(sym.name, sym.line, sym.type));
            }
        }

        return items;
    }

    private static class SymbolContext {
        final String name;
        final int line;
        final String type;

        SymbolContext(String name, int line, String type) {
            this.name = name;
            this.line = line;
            this.type = type;
        }
    }
}
