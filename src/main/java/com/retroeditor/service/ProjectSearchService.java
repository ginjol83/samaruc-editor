package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Servicio de busqueda en archivos de proyecto, sin dependencias de UI.
 */
public class ProjectSearchService {

    public static class SearchOptions {
        private final String term;
        private final String fileTypes; // Comma separated extensions like .c,.asm
        private final LocalDate modifiedAfter;
        private final boolean caseSensitive;
        private final boolean regex;

        public SearchOptions(String term, String fileTypes, LocalDate modifiedAfter, boolean caseSensitive, boolean regex) {
            this.term = term;
            this.fileTypes = fileTypes;
            this.modifiedAfter = modifiedAfter;
            this.caseSensitive = caseSensitive;
            this.regex = regex;
        }

        public String getTerm() { return term; }
        public String getFileTypes() { return fileTypes; }
        public LocalDate getModifiedAfter() { return modifiedAfter; }
        public boolean isCaseSensitive() { return caseSensitive; }
        public boolean isRegex() { return regex; }
    }

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

    public List<Match> searchProject(File root, SearchOptions options) {
        List<Match> matches = new ArrayList<>();

        if (root == null || !root.isDirectory() || options == null || options.getTerm() == null || options.getTerm().isBlank()) {
            return matches;
        }

        String term = options.getTerm();
        Set<String> extensions = null;
        if (options.getFileTypes() != null && !options.getFileTypes().isBlank()) {
            extensions = Arrays.stream(options.getFileTypes().split(","))
                    .map(String::trim)
                    .map(ext -> ext.startsWith(".") ? ext.toLowerCase() : "." + ext.toLowerCase())
                    .collect(Collectors.toSet());
        }

        try (Stream<Path> paths = Files.walk(root.toPath())) {
            final Set<String> finalExtensions = extensions;
            List<Path> files = paths.filter(Files::isRegularFile)
                    .filter(p -> filterByExtension(p, finalExtensions))
                    .filter(p -> filterByDate(p, options.getModifiedAfter()))
                    .collect(Collectors.toList());

            for (Path p : files) {
                try {
                    String content = Files.readString(p);
                    int idx = findTerm(content, term, options.isCaseSensitive(), options.isRegex());

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

    private boolean filterByExtension(Path p, Set<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return isCandidateTextFile(p);
        }
        String fileName = p.getFileName().toString().toLowerCase();
        return extensions.stream().anyMatch(fileName::endsWith);
    }

    private boolean filterByDate(Path p, LocalDate modifiedAfter) {
        if (modifiedAfter == null) {
            return true;
        }
        try {
            FileTime lastModified = Files.getLastModifiedTime(p);
            LocalDate lastModifiedDate = lastModified.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            return !lastModifiedDate.isBefore(modifiedAfter);
        } catch (IOException e) {
            return false;
        }
    }

    private int findTerm(String content, String term, boolean caseSensitive, boolean regex) {
        if (regex) {
            try {
                Pattern pattern = Pattern.compile(term, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.start();
                }
                return -1;
            } catch (Exception e) {
                return -1;
            }
        }
        if (caseSensitive) {
            return content.indexOf(term);
        } else {
            return content.toLowerCase().indexOf(term.toLowerCase());
        }
    }

    public List<Match> searchProject(File root, String term) {
        return searchProject(root, new SearchOptions(term, null, null, true, false));
    }

    public int replaceInProject(File root, String term, String replacement, SearchOptions options) {
        if (root == null || !root.isDirectory() || term == null || term.isEmpty() || options == null) {
            return 0;
        }

        int updatedFiles = 0;
        Set<String> extensions = null;
        if (options.getFileTypes() != null && !options.getFileTypes().isBlank()) {
            extensions = Arrays.stream(options.getFileTypes().split(","))
                    .map(String::trim)
                    .map(ext -> ext.startsWith(".") ? ext.toLowerCase() : "." + ext.toLowerCase())
                    .collect(Collectors.toSet());
        }

        try (Stream<Path> paths = Files.walk(root.toPath())) {
            final Set<String> finalExtensions = extensions;
            List<Path> files = paths
                .filter(Files::isRegularFile)
                .filter(p -> filterByExtension(p, finalExtensions))
                .filter(p -> filterByDate(p, options.getModifiedAfter()))
                .collect(Collectors.toList());

            for (Path p : files) {
                try {
                    String content = Files.readString(p);
                    String updated = replaceText(content, term, replacement, options.isCaseSensitive(), options.isRegex());
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

    public int replaceInProject(File root, String term, String replacement, boolean caseSensitive, boolean regex) {
        return replaceInProject(root, term, replacement, new SearchOptions(term, null, null, caseSensitive, regex));
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
