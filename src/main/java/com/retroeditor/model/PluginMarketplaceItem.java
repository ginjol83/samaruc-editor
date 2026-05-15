package com.retroeditor.model;

/**
 * Item published in the remote plugin marketplace catalog.
 */
public record PluginMarketplaceItem(
    String id,
    String name,
    String version,
    String description,
    String author,
    String category,
    String downloadUrl,
    String fileSize,
    boolean verified
) {
    public String displayLine() {
        String pluginName = name != null && !name.isBlank() ? name : id;
        String pluginVersion = version != null && !version.isBlank() ? version : "-";
        return pluginName + " (" + pluginVersion + ")";
    }
}

