package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Servicio que escanea el proyecto buscando comentarios marcados (TODO, FIXME,
 * HACK, XXX) en archivos fuente y devuelve una lista navegable.
 */
public class TodoScanService {

    public static final class TodoItem {
        private final File file;
        private final int line;
        private final String message;

        TodoItem(File file, int line, String message) {
            this.file = file;
            this.line = line;
            this.message = message != null ? message.trim() : "";
        }

        public File getFile() { return file; }
        public int getLine() { return line; }
        public String getMessage() { return message; }
    }

    private static final Pattern TODO_PATTERN =
        Pattern.compile("(?i)\\b(TODO|FIXME|HACK|XXX)\\b\\s*[:\\-]?\\s*(.*)");

    /** Escanea el proyecto completo devolviendo los comentarios marcados encontrados. */
    public List<TodoItem> scanProject(File projectRoot) {
        List<TodoItem> items = new ArrayList<>();
        if (projectRoot == null || !projectRoot.isDirectory()) return items;

        try (Stream<Path> paths = Files.walk(projectRoot.toPath())) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .filter(this::isCandidateTextFile)
                    .toList();
            for (Path p : files) {
                items.addAll(scanFile(p.toFile()));
            }
        } catch (IOException ignored) {
        }
        return items;
    }

    public List<TodoItem> scanFile(File file) {
        List<TodoItem> items = new ArrayList<>();
        if (file == null || !file.isFile()) return items;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String[] lines = content.split("\\r?\\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher matcher = TODO_PATTERN.matcher(line);
                if (matcher.find()) {
                    items.add(new TodoItem(file, i + 1, matcher.group(2)));
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return items;
    }

    private boolean isCandidateTextFile(Path file) {
        String name = file.getFileName() != null ? file.getFileName().toString().toLowerCase() : "";
        return name.endsWith(".c")
            || name.endsWith(".h")
            || name.endsWith(".cpp")
            || name.endsWith(".hpp")
            || name.endsWith(".asm")
            || name.endsWith(".s")
            || name.endsWith(".md")
            || name.endsWith(".txt")
            || name.endsWith(".smt")
            || name.equals("makefile")
            || name.endsWith(".mk");
    }
}