package com.retroeditor.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de análisis estático en tiempo real (Linter / Diagnostics).
 */
public class StaticAnalysisService {

    public record Diagnostic(int line, String message, boolean isError) {}

    public static List<Diagnostic> analyzeCode(String code) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (code == null || code.isEmpty()) return diagnostics;

        String[] lines = code.split("\\r?\\n");
        boolean inBlockComment = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            String trimmed = line.trim();

            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            if (trimmed.startsWith("/*")) {
                if (!trimmed.endsWith("*/") || trimmed.indexOf("*/") < trimmed.indexOf("/*")) {
                    inBlockComment = true;
                }
                continue;
            }

            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("#")) {
                continue;
            }

            int semiIdx = findStatementSemicolon(trimmed);
            if (semiIdx >= 0 && semiIdx < trimmed.length() - 1) {
                String afterSemi = trimmed.substring(semiIdx + 1).trim();
                if (!afterSemi.isEmpty() && !afterSemi.startsWith("//") && !afterSemi.startsWith("/*")) {
                    diagnostics.add(new Diagnostic(lineNum, "Error de sintaxis: texto o símbolo inesperado después del punto y coma (';')", true));
                }
            }
        }

        return diagnostics;
    }

    private static int findStatementSemicolon(String trimmed) {
        int parenDepth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == ';' && parenDepth == 0) {
                return i;
            }
        }
        return -1;
    }
}
