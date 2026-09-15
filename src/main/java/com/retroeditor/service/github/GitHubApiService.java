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
    private final String token;

    public GitHubApiService() {
        this(null);
    }

    public GitHubApiService(String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper(), token);
    }

    GitHubApiService(HttpClient httpClient, ObjectMapper objectMapper, String token) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.token = token;
    }

    /**
     * Devuelve una copia de este servicio autenticada con el token indicado.
     * Se usa para elevar el rate limit (60/h sin token, 5000/h con token).
     */
    public GitHubApiService withToken(String token) {
        return new GitHubApiService(httpClient, objectMapper, token);
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

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Samaruc-Editor");
        if (token != null) {
            builder.header("Authorization", "token " + token);
        }

        HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 403) {
            throw new RateLimitException(token == null
                ? "Límite de peticiones GitHub alcanzado (60/h sin autenticación)"
                : "Límite de peticiones GitHub alcanzado");
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

        HttpResponse<String> response = httpClient.send(newRequestBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        JsonNode node = objectMapper.readTree(response.body());
        return parseRepoNode(node);
    }

    /**
     * Lista los repositorios del usuario autenticado, ordenados por actualización.
     * Requiere un servicio creado con token.
     */
    public List<GitHubRepo> getUserRepos(int page, int perPage) throws IOException, InterruptedException {
        if (token == null) {
            throw new IOException("Token de GitHub no configurado");
        }
        perPage = Math.max(1, Math.min(perPage, MAX_PER_PAGE));
        String url = String.format("%s/user/repos?sort=updated&page=%d&per_page=%d", API_BASE, page, perPage);

        HttpResponse<String> response = httpClient.send(newRequestBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        List<GitHubRepo> repos = new ArrayList<>();
        JsonNode array = objectMapper.readTree(response.body());
        if (array.isArray()) {
            for (JsonNode node : array) {
                repos.add(parseRepoNode(node));
            }
        }
        return repos;
    }

    private HttpRequest.Builder newRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Samaruc-Editor");
        if (token != null) {
            builder.header("Authorization", "token " + token);
        }
        return builder;
    }

    /**
     * Añade una estrella al repositorio indicado. Requiere un servicio autenticado.
     *
     * @param owner dueño del repositorio
     * @param repo  nombre del repositorio
     * @return {@code true} si se marcó la estrella
     */
    public boolean starRepo(String owner, String repo) throws IOException, InterruptedException {
        if (token == null) {
            throw new IOException("Token de GitHub no configurado");
        }
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(API_BASE + "/user/starred/" + enc(owner) + "/" + enc(repo)))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + token)
            .header("User-Agent", "Samaruc-Editor")
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204 || response.statusCode() == 200) {
            return true;
        }
        throw new IOException("GitHub API status: " + response.statusCode());
    }

    /**
     * Quita la estrella al repositorio indicado. Requiere un servicio autenticado.
     *
     * @return {@code true} si se quitó la estrella
     */
    public boolean unstarRepo(String owner, String repo) throws IOException, InterruptedException {
        if (token == null) {
            throw new IOException("Token de GitHub no configurado");
        }
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(API_BASE + "/user/starred/" + enc(owner) + "/" + enc(repo)))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + token)
            .header("User-Agent", "Samaruc-Editor")
            .DELETE()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204 || response.statusCode() == 200 || response.statusCode() == 404) {
            return true;
        }
        throw new IOException("GitHub API status: " + response.statusCode());
    }

    /**
     * Comprueba si el usuario autenticado marcó con estrella el repositorio.
     * Requiere un servicio autenticado.
     */
    public boolean isStarred(String owner, String repo) throws IOException, InterruptedException {
        if (token == null) {
            throw new IOException("Token de GitHub no configurado");
        }
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(API_BASE + "/user/starred/" + enc(owner) + "/" + enc(repo)))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + token)
            .header("User-Agent", "Samaruc-Editor")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 || response.statusCode() == 204) {
            return true;
        }
        if (response.statusCode() == 404 || response.statusCode() < 200 || response.statusCode() >= 300) {
            return false;
        }
        return false;
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
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
