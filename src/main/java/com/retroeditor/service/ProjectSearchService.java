package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Servicio de busqueda en archivos de proyecto, sin dependencias de UI.
 */
public class ProjectSearchService {

    public static class Match {
        private final File file;
        private final int index;
        private final String snippet;

        public Match(File file, int index, String snippet) {
            this.file = file;
            this.index = index;
            this.snippet = snippet;
        }

        public File getFile() {
            return file;
        }

        public int getIndex() {
            return index;
        }

        public String getSnippet() {
            return snippet;
        }
    }

    public List<Match> searchProject(File root, String term) {
        List<Match> matches = new ArrayList<>();

        if (root == null || !root.isDirectory() || term == null || term.isBlank()) {
            return matches;
        }

        try (Stream<Path> paths = Files.walk(root.toPath())) {
            List<Path> files = paths.filter(Files::isRegularFile).collect(Collectors.toList());

            for (Path p : files) {
                try {
                    String content = Files.readString(p);
                    int idx = content.indexOf(term);

                    if (idx >= 0) {
                        String snippet = getSnippet(content, idx, term.length());
                        matches.add(new Match(p.toFile(), idx, snippet));
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }

        return matches;
    }

    public int replaceInProject(File root, String term, String replacement, boolean caseSensitive, boolean regex) {
        if (root == null || !root.isDirectory() || term == null || term.isEmpty()) {
            return 0;
        }

        int updatedFiles = 0;

        try (Stream<Path> paths = Files.walk(root.toPath())) {
            List<Path> files = paths
                .filter(Files::isRegularFile)
                .filter(this::isCandidateTextFile)
                .collect(Collectors.toList());

            for (Path p : files) {
                try {
                    String content = Files.readString(p);
                    String updated = replaceText(content, term, replacement, caseSensitive, regex);
                    if (!content.equals(updated)) {
                        Files.writeString(p, updated);
                        updatedFiles++;
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }

        return updatedFiles;
    }

    private String getSnippet(String content, int idx, int length) {
        int start = Math.max(0, idx - 30);
        int end = Math.min(content.length(), idx + length + 30);
        return content.substring(start, end).replaceAll("\r?\n", " ");
    }

    private boolean isCandidateTextFile(Path file) {
        String name = file.getFileName() != null ? file.getFileName().toString().toLowerCase() : "";
        return name.endsWith(".c")
            || name.endsWith(".h")
            || name.endsWith(".asm")
            || name.endsWith(".s")
            || name.endsWith(".md")
            || name.endsWith(".markdown")
            || name.equals("makefile")
            || name.endsWith(".mk")
            || name.endsWith(".txt");
    }

    private String replaceText(String text, String term, String replacement, boolean caseSensitive, boolean regex) {
        String safeReplacement = replacement != null ? replacement : "";

        try {
            if (regex) {
                return Pattern.compile(term, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE)
                    .matcher(text)
                    .replaceAll(java.util.regex.Matcher.quoteReplacement(safeReplacement));
            }

            if (caseSensitive) {
                return text.replace(term, safeReplacement);
            }

            return Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .replaceAll(java.util.regex.Matcher.quoteReplacement(safeReplacement));
        } catch (Exception ignored) {
            return text;
        }
    }
}
