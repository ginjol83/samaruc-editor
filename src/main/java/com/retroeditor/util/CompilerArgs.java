package com.retroeditor.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ayudantes para construir argumentos de compiladores (flags -D, -I y argumentos
 * de forma libre) de forma consistente para GBDK, Z88DK y GCC.
 */
public class CompilerArgs {

    public void addMacroFlags(List<String> cmd, String macros) {
        for (String token : splitCsvOrWhitespace(macros)) {
            cmd.add("-D" + token);
        }
    }

    public void addIncludeFlags(List<String> cmd, String includes) {
        if (includes == null || includes.isBlank()) return;
        String normalized = includes.replace(';', ',');
        for (String raw : normalized.split(",")) {
            String include = raw != null ? raw.trim() : "";
            if (include.isEmpty()) continue;
            cmd.add("-I" + include);
        }
    }

    public void addExtraArgs(List<String> cmd, String extraArgs) {
        cmd.addAll(splitQuotedArgs(extraArgs));
    }

    /**
     * Divide argumentos de forma libre preservando secciones entre comillas.
     * @param input Cadena con argumentos separados por espacios.
     * @return Lista de argumentos sin las comillas.
     */
    public List<String> splitQuotedArgs(String input) {
        List<String> args = new ArrayList<>();
        if (input == null || input.isBlank()) return args;

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args;
    }

    private List<String> splitCsvOrWhitespace(String input) {
        if (input == null || input.isBlank()) return Collections.emptyList();
        String normalized = input.replace(',', ' ');
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token == null) continue;
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) tokens.add(trimmed);
        }
        return tokens;
    }
}
