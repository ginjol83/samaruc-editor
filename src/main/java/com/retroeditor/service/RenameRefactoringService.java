package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Servicio de refactorización para renombrado seguro de símbolos (funciones, variables, macros)
 * en todo el proyecto mediante coincidencia por límites de palabra (word boundaries).
 */
public class RenameRefactoringService {

    public static final class RenameResult {
        private final int filesModified;
        private final int totalOccurrences;

        public RenameResult(int filesModified, int totalOccurrences) {
            this.filesModified = filesModified;
            this.totalOccurrences = totalOccurrences;
        }

        public int getFilesModified() { return filesModified; }
        public int getTotalOccurrences() { return totalOccurrences; }
    }

    public RenameResult renameSymbolInProject(File projectRoot, String oldName, String newName) {
        if (projectRoot == null || !projectRoot.isDirectory() || oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
            return new RenameResult(0, 0);
        }

        // Validate identifier format
        if (!oldName.matches("[A-Za-z_]\\w*") || !newName.matches("[A-Za-z_]\\w*")) {
            return new RenameResult(0, 0);
        }

        Pattern symbolPattern = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\b");
        int filesModified = 0;
        int totalOccurrences = 0;

        try (Stream<Path> paths = Files.walk(projectRoot.toPath())) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .filter(this::isCandidateTextFile)
                    .collect(Collectors.toList());

            for (Path p : files) {
                try {
                    String content = Files.readString(p);
                    Matcher matcher = symbolPattern.matcher(content);
                    int fileOccurrences = 0;
                    StringBuffer sb = new StringBuffer();
                    while (matcher.find()) {
                        fileOccurrences++;
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(newName));
                    }
                    matcher.appendTail(sb);

                    if (fileOccurrences > 0) {
                        Files.writeString(p, sb.toString());
                        filesModified++;
                        totalOccurrences += fileOccurrences;
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }

        return new RenameResult(filesModified, totalOccurrences);
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
            || name.equals("makefile")
            || name.endsWith(".mk");
    }
}
