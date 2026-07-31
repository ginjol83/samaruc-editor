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
import com.retroeditor.model.LanguageMarketplaceItem;

/**
 * Servicio para descargar idiomas desde un catálogo remoto.
 */
public class LanguageMarketplaceService {

    public static final String DEFAULT_I18N_MARKETPLACE_URL =
        "https://raw.githubusercontent.com/ginjol83/samaruc-i18n-repo/main/languages.json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LanguageMarketplaceService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(), new ObjectMapper());
    }

    LanguageMarketplaceService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<LanguageMarketplaceItem> fetchCatalog(String catalogUrl) throws IOException, InterruptedException {
        String url = catalogUrl != null && !catalogUrl.isBlank() ? catalogUrl : DEFAULT_I18N_MARKETPLACE_URL;
        url = normalizeGithubBlobUrl(url);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Language Marketplace HTTP status: " + response.statusCode());
        }

        return parseCatalog(response.body());
    }

    List<LanguageMarketplaceItem> parseCatalog(String rawJson) throws IOException {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode languagesNode = root.path("languages");
        if (!languagesNode.isObject()) {
            return List.of();
        }

        List<LanguageMarketplaceItem> items = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : iterableEntries(languagesNode)) {
            String id = entry.getKey();
            JsonNode p = entry.getValue();

            String langCode = textOrNull(p, "languageCode");
            String name = textOrNull(p, "name");
            String version = textOrNull(p, "version");
            String description = textOrNull(p, "description");
            String author = textOrNull(p, "author");
            String downloadUrl = textOrNull(p, "downloadUrl");
            String fileSize = textOrNull(p, "fileSize");

            if (downloadUrl == null || downloadUrl.isBlank() || langCode == null) {
                continue;
            }

            items.add(new LanguageMarketplaceItem(id, langCode, name, version, description, author, downloadUrl, fileSize));
        }

        items.sort(Comparator.comparing(LanguageMarketplaceItem::displayLine, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    public DownloadedLanguage downloadLanguage(LanguageMarketplaceItem item) throws IOException, InterruptedException {
        if (item == null || item.downloadUrl() == null || item.downloadUrl().isBlank()) {
            throw new IllegalArgumentException("Language marketplace item without download URL");
        }

        String downloadUrl = normalizeGithubBlobUrl(item.downloadUrl());

        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Language download HTTP status: " + response.statusCode());
        }

        Path tempFile = Files.createTempFile("samaruc-i18n-", ".properties");
        Files.write(tempFile, response.body());

        String suggestedName = "MessagesBundle_" + item.languageCode() + ".properties";
        return new DownloadedLanguage(tempFile, suggestedName);
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

    public record DownloadedLanguage(Path tempFile, String suggestedFileName) {}
}
