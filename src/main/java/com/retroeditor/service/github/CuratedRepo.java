package com.retroeditor.service.github;

/**
 * Un repositorio del catálogo curado, con categoría y descripciones localizadas.
 */
public record CuratedRepo(
    GitHubRepo repo,
    String category,
    String descriptionEs,
    String descriptionEn
) {
    public CuratedRepo {
        if (repo == null) throw new IllegalArgumentException("repo must not be null");
        if (category == null) category = "general";
    }

    /**
     * Crea un CuratedRepo desde los campos individuales del JSON.
     */
    public static CuratedRepo fromJson(String owner, String name, String category,
                                       String descriptionEs, String descriptionEn, int stars) {
        GitHubRepo ghRepo = new GitHubRepo(0, name, owner, owner + "/" + name,
            descriptionEs != null ? descriptionEs : descriptionEn,
            stars, null, null, null, null, null);
        return new CuratedRepo(ghRepo, category, descriptionEs, descriptionEn);
    }

    /**
     * Devuelve la descripción en el idioma solicitado.
     */
    public String getDescription(String lang) {
        if ("es".equals(lang) && descriptionEs != null && !descriptionEs.isEmpty()) {
            return descriptionEs;
        }
        return descriptionEn != null ? descriptionEn : "";
    }
}
