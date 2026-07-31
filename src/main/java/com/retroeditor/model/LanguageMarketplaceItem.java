package com.retroeditor.model;

/**
 * Item publicado en el catálogo remoto de idiomas (i18n).
 */
public record LanguageMarketplaceItem(
    String id,
    String languageCode,
    String name,
    String version,
    String description,
    String author,
    String downloadUrl,
    String fileSize
) {
    public String displayLine() {
        String langName = name != null && !name.isBlank() ? name : languageCode;
        String langVersion = version != null && !version.isBlank() ? version : "-";
        return langName + " [" + languageCode + "] (" + langVersion + ")";
    }
}
