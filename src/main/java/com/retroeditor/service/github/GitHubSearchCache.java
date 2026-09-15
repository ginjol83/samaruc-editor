package com.retroeditor.service.github;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Caché local de resultados de búsqueda de GitHub.
 *
 * <p>Persiste {@code List<GitHubRepo>} por consulta en un archivo JSON del workspace
 * ({@code ~/.samaruc-editor/github-search-cache.json}) para poder reutilizarla sin red.
 * Las entradas caducan por antigüedad (24 h) y el número de consultas guardadas se acota.
 */
public class GitHubSearchCache {

    private static final long MAX_AGE_MILLIS = 24L * 60 * 60 * 1000;
    private static final int MAX_ENTRIES = 10;
    private static final String RELATIVE_PATH = ".samaruc-editor/github-search-cache.json";

    private final ObjectMapper objectMapper;
    private final Path cacheFile;

    public GitHubSearchCache() {
        this(new ObjectMapper(), Path.of(System.getProperty("user.home"), RELATIVE_PATH));
    }

    GitHubSearchCache(ObjectMapper objectMapper, Path cacheFile) {
        this.objectMapper = objectMapper;
        this.cacheFile = cacheFile;
    }

    /** Devuelve los resultados cacheados para una consulta, si existen y no han caducado. */
    public Optional<List<GitHubRepo>> get(String query) throws IOException {
        if (query == null || query.isBlank() || !Files.isRegularFile(cacheFile)) {
            return Optional.empty();
        }
        long now = Instant.now().toEpochMilli();
        JsonNode root = objectMapper.readTree(cacheFile.toFile());
        JsonNode entries = root.path("entries");
        if (!entries.isArray()) {
            return Optional.empty();
        }
        for (JsonNode entry : entries) {
            if (!query.equals(entry.path("query").asText(""))) {
                continue;
            }
            long savedAt = entry.path("savedAt").asLong(0);
            if (now - savedAt > MAX_AGE_MILLIS) {
                return Optional.empty();
            }
            List<GitHubRepo> repos = new ArrayList<>();
            for (JsonNode item : entry.path("items")) {
                repos.add(GitHubApiService.parseRepoNode(item));
            }
            return Optional.of(repos);
        }
        return Optional.empty();
    }

    /** Guarda (o reemplaza) los resultados de una consulta en la caché. */
    public void put(String query, List<GitHubRepo> repos) throws IOException {
        if (query == null || query.isBlank() || repos == null) {
            return;
        }
        JsonNode root = readOrNew();
        ArrayNode entries = (ArrayNode) root.withArray("entries");
        List<JsonNode> remaining = new ArrayList<>();
        for (JsonNode entry : entries) {
            if (!query.equals(entry.path("query").asText(""))) {
                remaining.add(entry);
            }
        }
        entries.removeAll();
        long now = Instant.now().toEpochMilli();
        ObjectNode newEntry = objectMapper.createObjectNode();
        newEntry.put("query", query);
        newEntry.put("savedAt", now);
        ArrayNode items = newEntry.putArray("items");
        for (GitHubRepo repo : repos) {
            items.add(toJson(repo));
        }
        remaining.add(0, newEntry);
        if (remaining.size() > MAX_ENTRIES) {
            remaining = remaining.subList(0, MAX_ENTRIES);
        }
        for (JsonNode node : remaining) {
            entries.add(node);
        }
        Path parent = cacheFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), root);
    }

    /** Vacía la caché (usado en tests y desde la UI si se desea). */
    public void clear() throws IOException {
        if (Files.exists(cacheFile)) {
            Files.delete(cacheFile);
        }
    }

    /** Ruta del archivo de caché (para diagnóstico/tests). */
    Path getCacheFile() {
        return cacheFile;
    }

    private JsonNode readOrNew() throws IOException {
        if (Files.isRegularFile(cacheFile)) {
            return objectMapper.readTree(cacheFile.toFile());
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", 1);
        root.putArray("entries");
        return root;
    }

    /** Serializa un repo con el mismo formato que la API de GitHub (lo entiende {@code parseRepoNode}). */
    static ObjectNode toJson(GitHubRepo repo) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("id", repo.id());
        node.put("name", repo.name());
        node.putObject("owner").put("login", repo.owner());
        node.put("full_name", repo.fullName());
        node.put("description", repo.description());
        node.put("stargazers_count", repo.stars());
        node.put("language", repo.language());
        node.put("html_url", repo.url());
        node.put("default_branch", repo.defaultBranch());
        if (repo.updatedAt() != null) {
            node.put("updated_at", repo.updatedAt().toString());
        }
        return node;
    }
}