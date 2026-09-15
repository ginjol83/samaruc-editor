package com.retroeditor.service.git;

import java.util.List;
import java.util.Optional;

/**
 * Modelos inmutables del motor git local: estado de cambios, commits e información de rama.
 * Los códigos siguen el formato {@code XY path} de {@code git status --porcelain} (v1).
 */
public final class GitModels {

    private GitModels() {
    }

    /** Una entrada de {@code git status --porcelain}: código de índice, código de trabajo y ruta relativa. */
    public record GitStatusEntry(char indexStatus, char workTreeStatus, String path) {

        public boolean isUntracked() {
            return indexStatus == '?' && workTreeStatus == '?';
        }

        /** {@code true} si hay contenido en el índice (staged, p. ej. "M " o "A "). */
        public boolean hasStagedChange() {
            return indexStatus != ' ' && indexStatus != '?';
        }

        /** {@code true} si hay cambios sin añadir al índice ({@code worktree}), p. ej. " M" o "MM". */
        public boolean hasUnstagedChange() {
            return workTreeStatus != ' ';
        }

        /** Código de dos letras estándar git, p. ej. "M ", "MM", "??". */
        public String shortStatus() {
            return String.valueOf(indexStatus) + workTreeStatus;
        }
    }

    /** Un commit del historial: hash corto y asunto del mensaje. */
    public record GitCommitInfo(String shortHash, String subject) {
        public String display() {
            return shortHash + "  " + subject;
        }
    }

    /** Información de la rama actual (la máquina puede estar en detached HEAD). */
    public record GitBranchInfo(Optional<String> current) {
        public static GitBranchInfo of(String branch) {
            return new GitBranchInfo(Optional.ofNullable(branch).filter(b -> !b.isBlank()));
        }
    }

    /** Tipo de una línea de diff para el renderizado inline. */
    public enum DiffLineType { HEADER, HUNK, CONTEXT, ADD, REMOVE }

    /** Una línea de un diff ({@code git diff}) clasificada para colorearla en el panel. */
    public record GitDiffLine(DiffLineType type, String text) {

        public static GitDiffLine header(String text) {
            return new GitDiffLine(DiffLineType.HEADER, text);
        }

        public static GitDiffLine hunk(String text) {
            return new GitDiffLine(DiffLineType.HUNK, text);
        }

        public static GitDiffLine add(String text) {
            return new GitDiffLine(DiffLineType.ADD, text);
        }

        public static GitDiffLine remove(String text) {
            return new GitDiffLine(DiffLineType.REMOVE, text);
        }

        public static GitDiffLine context(String text) {
            return new GitDiffLine(DiffLineType.CONTEXT, text);
        }
    }

    /** Un diff de un archivo concreto (todas sus líneas clasificadas). */
    public record GitDiffFile(String path, List<GitDiffLine> lines) {
    }
}