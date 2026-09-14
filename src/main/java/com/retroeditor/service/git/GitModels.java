package com.retroeditor.service.git;

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
}