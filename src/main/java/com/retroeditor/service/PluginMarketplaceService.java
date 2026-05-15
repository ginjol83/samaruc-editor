package com.retroeditor.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retroeditor.model.PluginMarketplaceItem;

/**
 * Reads and downloads plugins from a remote marketplace JSON catalog.
 */
public class PluginMarketplaceService {

    public static final String DEFAULT_MARKETPLACE_URL =
        "https://raw.githubusercontent.com/ginjol83/plugins-repo/main/plugins.json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PluginMarketplaceService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(), new ObjectMapper());
    }

    PluginMarketplaceService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<PluginMarketplaceItem> fetchCatalog(String catalogUrl) throws IOException, InterruptedException {
        String url = catalogUrl != null && !catalogUrl.isBlank() ? catalogUrl : DEFAULT_MARKETPLACE_URL;
        url = normalizeGithubBlobUrl(url);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Marketplace HTTP status: " + response.statusCode());
        }

        return parseCatalog(response.body());
    }

    List<PluginMarketplaceItem> parseCatalog(String rawJson) throws IOException {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode pluginsNode = root.path("plugins");
        if (!pluginsNode.isObject()) {
            return List.of();
        }

        List<PluginMarketplaceItem> items = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : iterableEntries(pluginsNode)) {
            String id = entry.getKey();
            JsonNode p = entry.getValue();

            String name = textOrNull(p, "name");
            String version = textOrNull(p, "version");
            String description = textOrNull(p, "description");
            String author = textOrNull(p, "author");
            String category = textOrNull(p, "category");
            String downloadUrl = textOrNull(p, "downloadUrl");
            String fileSize = textOrNull(p, "fileSize");
            boolean verified = p.path("verified").asBoolean(false);

            if (downloadUrl == null || downloadUrl.isBlank()) {
                continue;
            }

            items.add(new PluginMarketplaceItem(id, name, version, description, author, category, downloadUrl, fileSize, verified));
        }

        items.sort(Comparator.comparing(PluginMarketplaceItem::displayLine, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    public DownloadedPlugin downloadPlugin(PluginMarketplaceItem item) throws IOException, InterruptedException {
        if (item == null || item.downloadUrl() == null || item.downloadUrl().isBlank()) {
            throw new IllegalArgumentException("Plugin marketplace item without download URL");
        }

        String downloadUrl = normalizeGithubBlobUrl(item.downloadUrl());

        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Plugin download HTTP status: " + response.statusCode());
        }

        Path tempFile = Files.createTempFile("samaruc-marketplace-", ".jar");
        Files.write(tempFile, response.body());

        String suggestedName = buildFileName(item);
        return new DownloadedPlugin(tempFile, suggestedName);
    }

    String buildFileName(PluginMarketplaceItem item) {
        String fromUrl = null;
        try {
            URI uri = URI.create(item.downloadUrl());
            String path = uri.getPath();
            if (path != null && path.contains("/")) {
                fromUrl = path.substring(path.lastIndexOf('/') + 1);
            }
        } catch (Exception ignored) {
            // Fallback below.
        }

        if (fromUrl != null && !fromUrl.isBlank() && fromUrl.toLowerCase().endsWith(".jar")) {
            return fromUrl;
        }

        String baseName = item.id() != null && !item.id().isBlank() ? item.id() : "plugin";
        return baseName + ".jar";
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private Iterable<Map.Entry<String, JsonNode>> iterableEntries(JsonNode objectNode) {
        return objectNode::fields;
    }

    String normalizeGithubBlobUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || path == null) {
                return url;
            }

            String normalizedHost = host.toLowerCase();
            if (!("github.com".equals(normalizedHost) || "www.github.com".equals(normalizedHost))) {
                return url;
            }

            String[] parts = path.split("/");
            if (parts.length < 5) {
                return url;
            }

            // Expected path: /owner/repo/blob/branch/file...
            if (!"blob".equals(parts[3])) {
                return url;
            }

            String owner = parts[1];
            String repo = parts[2];
            String branch = parts[4];
            StringBuilder filePath = new StringBuilder();
            for (int i = 5; i < parts.length; i++) {
                if (parts[i] == null || parts[i].isBlank()) continue;
                if (filePath.length() > 0) filePath.append('/');
                filePath.append(parts[i]);
            }

            if (owner.isBlank() || repo.isBlank() || branch.isBlank() || filePath.length() == 0) {
                return url;
            }

            return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + filePath;
        } catch (Exception ignored) {
            return url;
        }
    }

    public record DownloadedPlugin(Path tempFile, String suggestedFileName) {}
}

