package com.retroeditor.service;

import java.util.Locale;

/**
 * Servicio de formateo y auto-indentación automática para código C y Assembly (Ctrl+Alt+L).
 */
public class CodeFormatterService {

    public static String formatCode(String sourceCode, String fileName) {
        if (sourceCode == null || sourceCode.isEmpty()) return "";
        String lower = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".asm") || lower.endsWith(".s")) {
            return formatAssembly(sourceCode);
        } else {
            return formatC(sourceCode);
        }
    }

    private static String formatC(String code) {
        String[] lines = code.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        int tabSize = 4;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                sb.append("\n");
                continue;
            }

            if (line.startsWith("}")) {
                indentLevel = Math.max(0, indentLevel - 1);
            }

            String indent = " ".repeat(indentLevel * tabSize);
            sb.append(indent).append(line).append("\n");

            if (line.endsWith("{")) {
                indentLevel++;
            }
        }

        return sb.toString().trim() + "\n";
    }

    private static String formatAssembly(String code) {
        String[] lines = code.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                sb.append("\n");
                continue;
            }

            if (line.endsWith(":")) {
                sb.append(line).append("\n");
            } else if (line.startsWith(";") || line.startsWith("*")) {
                sb.append("    ").append(line).append("\n");
            } else {
                sb.append("    ").append(line).append("\n");
            }
        }

        return sb.toString().trim() + "\n";
    }
}
