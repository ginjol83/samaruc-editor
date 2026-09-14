package com.retroeditor.service.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cliente de la API REST de GitHub para buscar repositorios.
 * Funciona sin autenticación (rate limit: 60 req/h).
 */
public class GitHubApiService {

    private static final String API_BASE = "https://api.github.com";
    private static final int DEFAULT_PER_PAGE = 20;
    private static final int MAX_PER_PAGE = 100;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubApiService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    GitHubApiService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Busca repositorios por palabra clave.
     *
     * @param query   Texto de búsqueda
     * @param page    Página (1-based)
     * @param perPage Resultados por página
     * @return Lista de repos encontrados
     */
    public List<GitHubRepo> searchRepos(String query, int page, int perPage) throws IOException, InterruptedException {
        if (query == null || query.isBlank()) return List.of();
        perPage = Math.max(1, Math.min(perPage, MAX_PER_PAGE));

        String url = String.format("%s/search/repositories?q=%s&sort=stars&order=desc&page=%d&per_page=%d",
            API_BASE, encodeQuery(query), page, perPage);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Samaruc-Editor")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 403) {
            throw new RateLimitException("Límite de peticiones GitHub alcanzado (60/h sin autenticación)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        return parseSearchResults(response.body());
    }

    /**
     * Obtiene los detalles de un repositorio específico.
     */
    public GitHubRepo getRepository(String owner, String repo) throws IOException, InterruptedException {
        String url = String.format("%s/repos/%s/%s", API_BASE, owner, repo);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Samaruc-Editor")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        JsonNode node = objectMapper.readTree(response.body());
        return parseRepoNode(node);
    }

    /**
     * Parsea el JSON de resultados de búsqueda.
     */
    List<GitHubRepo> parseSearchResults(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode items = root.get("items");
        if (items == null || !items.isArray()) return List.of();

        List<GitHubRepo> repos = new ArrayList<>();
        for (JsonNode item : items) {
            repos.add(parseRepoNode(item));
        }
        return repos;
    }

    /**
     * Parsea un nodo JSON de repositorio.
     */
    static GitHubRepo parseRepoNode(JsonNode node) {
        long id = node.path("id").asLong(0);
        String name = node.path("name").asText("");
        String owner = node.path("owner").path("login").asText("");
        String fullName = node.path("full_name").asText(owner + "/" + name);
        String description = node.path("description").asText("");
        int stars = node.path("stargazers_count").asInt(0);
        String language = node.path("language").asText("");
        String url = node.path("html_url").asText("https://github.com/" + fullName);
        String defaultBranch = node.path("default_branch").asText("main");

        LocalDateTime updatedAt = null;
        String updatedAtStr = node.path("updated_at").asText("");
        if (!updatedAtStr.isEmpty()) {
            try {
                updatedAt = LocalDateTime.parse(updatedAtStr, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception ignored) { }
        }

        return new GitHubRepo(id, name, owner, fullName, description, stars,
            language, url, null, defaultBranch, updatedAt);
    }

    /**
     * Intenta parsear un JSON de repositorio individual (no search).
     */
    public GitHubRepo parseSingleRepo(String json) throws IOException {
        return parseRepoNode(objectMapper.readTree(json));
    }

    private String encodeQuery(String query) {
        return query.replace(" ", "+").replace("&", "%26");
    }

    /**
     * Excepción específica para rate limit de GitHub.
     */
    public static class RateLimitException extends IOException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
