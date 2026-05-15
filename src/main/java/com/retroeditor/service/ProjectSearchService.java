package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Busca un termino en todos los archivos regulares bajo el directorio raiz.
     */
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

    private String getSnippet(String content, int idx, int length) {
        int start = Math.max(0, idx - 30);
        int end = Math.min(content.length(), idx + length + 30);
        return content.substring(start, end).replaceAll("\r?\n", " ");
    }
}

