package com.retroeditor.service.github;

import java.time.LocalDateTime;

/**
 * Representa un repositorio de GitHub con sus datos esenciales.
 */
public record GitHubRepo(
    long id,
    String name,
    String owner,
    String fullName,
    String description,
    int stars,
    String language,
    String url,
    String archiveUrl,
    String defaultBranch,
    LocalDateTime updatedAt
) {
    public GitHubRepo {
        if (name == null || owner == null) {
            throw new IllegalArgumentException("name and owner must not be null");
        }
        if (fullName == null) fullName = owner + "/" + name;
        if (url == null) url = "https://github.com/" + fullName;
        if (defaultBranch == null) defaultBranch = "main";
    }

    /**
     * Devuelve la URL de descarga ZIP del repositorio.
     */
    public String getZipUrl() {
        return String.format("https://github.com/%s/archive/refs/heads/%s.zip", fullName, defaultBranch);
    }

    /**
     * Devuelve la URL HTTPS apta para {@code git clone} del repositorio.
     */
    public String getGitUrl() {
        return "https://github.com/" + fullName + ".git";
    }

    @Override
    public String toString() {
        return String.format("%s (⭐ %d)", fullName, stars);
    }
}
