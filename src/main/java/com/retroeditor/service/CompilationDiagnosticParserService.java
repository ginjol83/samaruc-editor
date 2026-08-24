package com.retroeditor.service;

import java.io.File;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de salida de compilador sin dependencias de UI.
 */
public class CompilationDiagnosticParserService {

    private static final Pattern CONSOLE_FILE_LINE_PATTERN = Pattern.compile("(['\"]?[A-Za-z]:[^:\\r\\n]*?\\.[A-Za-z0-9_]+['\"]?|['\"]?[^\\s:]+\\.[A-Za-z0-9_]+['\"]?):(\\d+)(?::\\d+)?");
    private static final Pattern COMPILER_FILE_LINE_DIAGNOSTIC_PATTERN = Pattern.compile("(?i)(['\"]?[A-Za-z]:[^:\\r\\n]*['\"]?|['\"]?[^:\\r\\n]+\\.[A-Za-z0-9_]+['\"]?):(\\d+)(?::\\d+)?\\s*:");
    private static final Pattern Z88DK_PREFIXED_FILE_LINE_DIAGNOSTIC_PATTERN = Pattern.compile("(?i)(?:sccz80|sdcc|zsdcc|zcc)\\s*:\\s*(['\"]?[^'\"\\r\\n]+?\\.[A-Za-z0-9_]+['\"]?)\\s+(?:L|line)\\s*[:=]?\\s*(\\d+)\\b.*?\\b(error|warning)\\b");
    private static final Pattern Z88DK_FILE_LINE_DIAGNOSTIC_PATTERN = Pattern.compile("(?i)(['\"]?[^'\"\\r\\n]+?\\.[A-Za-z0-9_]+['\"]?)\\s+(?:L|line)\\s*[:=]?\\s*(\\d+)\\b.*?\\b(error|warning)\\b");
    private static final Pattern COMPILER_AT_LINE_DIAGNOSTIC_PATTERN = Pattern.compile("(?i)\\bat\\s+(\\d+)\\s*:\\s*(error|warning)");
    private static final Pattern COMPILATION_EXIT_CODE_PATTERN = Pattern.compile("(?i)(?:c[oó]digo\\s+de\\s+salida|exit\\s+code)\\s*:\\s*(-?\\d+)");
    private static final Pattern DIAGNOSTIC_ERROR_PATTERN = Pattern.compile("(?i)\\b(?:fatal\\s+)?error\\b");
    private static final Pattern DIAGNOSTIC_WARNING_PATTERN = Pattern.compile("(?i)\\bwarning\\b");

    public static final class DiagnosticLocation {
        private final File file;
        private final int line;
        private final boolean warning;

        public DiagnosticLocation(File file, int line, boolean warning) {
            this.file = file;
            this.line = line;
            this.warning = warning;
        }

        public File getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public boolean isWarning() {
            return warning;
        }
    }

    public Integer parseCompilationExitCode(String outputLine) {
        Matcher matcher = COMPILATION_EXIT_CODE_PATTERN.matcher(outputLine == null ? "" : outputLine);
        if (!matcher.find()) return null;

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public DiagnosticLocation parseCompilerDiagnosticLocation(File sourceFile, String outputLine, Function<String, File> resolver) {
        if (outputLine == null || outputLine.isBlank()) return null;

        Matcher withFile = COMPILER_FILE_LINE_DIAGNOSTIC_PATTERN.matcher(outputLine);
        while (withFile.find()) {
            Boolean warning = resolveWarningSeverity(outputLine);
            if (warning == null) continue;

            int lineNumber = parseInt(withFile.group(2));
            if (lineNumber <= 0) continue;

            File resolved = resolve(resolver, sanitizePath(withFile.group(1)));
            if (resolved != null) {
                return new DiagnosticLocation(resolved, lineNumber, warning.booleanValue());
            }
        }

        DiagnosticLocation z88 = parseZ88dkDiagnosticLocation(outputLine, resolver);
        if (z88 != null) return z88;

        Matcher atLine = COMPILER_AT_LINE_DIAGNOSTIC_PATTERN.matcher(outputLine);
        if (atLine.find()) {
            String severity = atLine.group(2);
            boolean warning = "warning".equalsIgnoreCase(severity);
            if (!warning && !"error".equalsIgnoreCase(severity)) return null;

            int lineNumber = parseInt(atLine.group(1));
            if (lineNumber <= 0 || sourceFile == null) return null;
            return new DiagnosticLocation(sourceFile, lineNumber, warning);
        }

        return null;
    }

    public DiagnosticLocation parseConsoleFileLine(String lineText, Function<String, File> resolver) {
        if (lineText == null || lineText.isBlank()) return null;

        DiagnosticLocation z88 = parseZ88dkDiagnosticLocation(lineText, resolver);
        if (z88 != null) return z88;

        Matcher matcher = CONSOLE_FILE_LINE_PATTERN.matcher(lineText);
        while (matcher.find()) {
            int lineNumber = parseInt(matcher.group(2));
            if (lineNumber <= 0) continue;

            File resolved = resolve(resolver, sanitizePath(matcher.group(1)));
            if (resolved != null && resolved.isFile()) {
                return new DiagnosticLocation(resolved, lineNumber, false);
            }
        }

        return null;
    }

    private DiagnosticLocation parseZ88dkDiagnosticLocation(String outputLine, Function<String, File> resolver) {
        if (outputLine == null || outputLine.isBlank()) return null;

        DiagnosticLocation prefixed = parsePatternBasedDiagnostic(outputLine, Z88DK_PREFIXED_FILE_LINE_DIAGNOSTIC_PATTERN, resolver);
        if (prefixed != null) return prefixed;

        return parsePatternBasedDiagnostic(outputLine, Z88DK_FILE_LINE_DIAGNOSTIC_PATTERN, resolver);
    }

    private DiagnosticLocation parsePatternBasedDiagnostic(String outputLine, Pattern pattern, Function<String, File> resolver) {
        Matcher matcher = pattern.matcher(outputLine);
        while (matcher.find()) {
            String severity = matcher.group(3);
            boolean warning = "warning".equalsIgnoreCase(severity);
            if (!warning && !"error".equalsIgnoreCase(severity)) continue;

            int lineNumber = parseInt(matcher.group(2));
            if (lineNumber <= 0) continue;

            File resolved = resolve(resolver, sanitizePath(matcher.group(1)));
            if (resolved != null) {
                return new DiagnosticLocation(resolved, lineNumber, warning);
            }
        }
        return null;
    }

    private Boolean resolveWarningSeverity(String outputLine) {
        if (outputLine == null || outputLine.isBlank()) return null;
        if (DIAGNOSTIC_ERROR_PATTERN.matcher(outputLine).find()) return Boolean.FALSE;
        if (DIAGNOSTIC_WARNING_PATTERN.matcher(outputLine).find()) return Boolean.TRUE;
        return null;
    }

    private File resolve(Function<String, File> resolver, String rawPath) {
        if (resolver == null) return null;
        return resolver.apply(rawPath);
    }

    private int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception ex) {
            return -1;
        }
    }

    private String sanitizePath(String rawPath) {
        if (rawPath == null) return "";

        String cleaned = rawPath.trim();
        while (!cleaned.isEmpty() && "'\"([{".indexOf(cleaned.charAt(0)) >= 0) {
            cleaned = cleaned.substring(1);
        }
        while (!cleaned.isEmpty() && "'\")]},;".indexOf(cleaned.charAt(cleaned.length() - 1)) >= 0) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }
}

