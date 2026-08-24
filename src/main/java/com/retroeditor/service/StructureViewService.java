package com.retroeditor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para extraer la estructura de símbolos de un archivo fuente (C, C++, ASM)
 * para alimentar el panel de Outline / Structure View.
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

    private static final Pattern C_FUNC_PATTERN = Pattern.compile(
        "\\b(?:int|void|char|long|float|double|unsigned|static|inline|extern|_Bool|bool|struct\\s+\\w+)\\s+\\**([A-Za-z_]\\w*)\\s*\\("
    );
    private static final Pattern DEFINE_PATTERN = Pattern.compile("^\\s*#\\s*define\\s+([A-Za-z_]\\w*)");
    private static final Pattern STRUCT_PATTERN = Pattern.compile("\\b(?:struct|typedef\\s+struct)\\s+([A-Za-z_]\\w*)");
    private static final Pattern ASM_LABEL_PATTERN = Pattern.compile("^\\s*([A-Za-z_.][A-Za-z0-9_]*)\\s*:");
    private static final Pattern REGION_PATTERN = Pattern.compile("(?i)(?:#\\s*region|region)\\s+(.+)");
    private static final Pattern GLOBAL_VAR_PATTERN = Pattern.compile(
        "^(?:int|char|long|float|double|unsigned|const|static|volatile)\\s+(?:\\**)?([A-Za-z_]\\w*)\\s*(?:\\[[^\\]]*\\])?\\s*(?:=.*;|;)"
    );

    public List<StructureSymbol> getStructure(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<StructureSymbol> symbols = new ArrayList<>();
        String[] lines = text.split("\\r?\\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String lineText = lines[i].trim();
            int lineNum = i + 1;

            if (lineText.isEmpty() || lineText.startsWith("//") || lineText.startsWith(";")) {
                // Check if it's an asm label comment or region inside comment
                Matcher regMatcher = REGION_PATTERN.matcher(lineText);
                if (regMatcher.find()) {
                    symbols.add(new StructureSymbol(regMatcher.group(1).trim(), lineNum, "region", regMatcher.group(1).trim()));
                    continue;
                }
            }

            Matcher regMatcher = REGION_PATTERN.matcher(lines[i]);
            if (regMatcher.find()) {
                symbols.add(new StructureSymbol(regMatcher.group(1).trim(), lineNum, "region", regMatcher.group(1).trim()));
                continue;
            }

            Matcher defMatcher = DEFINE_PATTERN.matcher(lines[i]);
            if (defMatcher.find()) {
                symbols.add(new StructureSymbol(defMatcher.group(1), lineNum, "macro", lines[i].trim()));
                continue;
            }

            Matcher structMatcher = STRUCT_PATTERN.matcher(lines[i]);
            if (structMatcher.find()) {
                symbols.add(new StructureSymbol(structMatcher.group(1), lineNum, "struct", lines[i].trim()));
                continue;
            }

            Matcher funcMatcher = C_FUNC_PATTERN.matcher(lines[i]);
            if (funcMatcher.find()) {
                symbols.add(new StructureSymbol(funcMatcher.group(1), lineNum, "function", lines[i].trim()));
                continue;
            }

            Matcher asmMatcher = ASM_LABEL_PATTERN.matcher(lines[i]);
            if (asmMatcher.find()) {
                symbols.add(new StructureSymbol(asmMatcher.group(1), lineNum, "label", asmMatcher.group(1)));
                continue;
            }

            Matcher varMatcher = GLOBAL_VAR_PATTERN.matcher(lines[i]);
            if (varMatcher.find()) {
                symbols.add(new StructureSymbol(varMatcher.group(1), lineNum, "variable", lines[i].trim()));
            }
        }

        return symbols;
    }
}
