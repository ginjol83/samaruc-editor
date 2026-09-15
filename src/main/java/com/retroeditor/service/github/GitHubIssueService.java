package com.retroeditor.service.github;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gestión de issues de GitHub ({@code GET/POST /repos/{owner}/{repo}/issues}).
 * Requiere un token con scope {@code repo}.
 */
public class GitHubIssueService {

    private static final String API_BASE = "https://api.github.com";

    /** Issue de GitHub. */
    public record Issue(int number, String title, String body, String htmlUrl,
                        String state, String userLogin, String createdAt) {}

    private final HttpClient httpClient;
    private final String apiBase;
    private final ObjectMapper objectMapper;

    public GitHubIssueService() {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            API_BASE,
            new ObjectMapper());
    }

    GitHubIssueService(HttpClient httpClient, String apiBase, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.apiBase = apiBase;
        this.objectMapper = objectMapper;
    }

    /**
     * Lista los issues abiertos de un repositorio.
     *
     * @param token token con scope {@code repo}
     * @param owner dueño del repositorio
     * @param repo  nombre del repositorio
     * @return issues abiertos (página 1, hasta {@code perPage})
     */
    public List<Issue> listOpenIssues(String token, String owner, String repo)
            throws IOException, InterruptedException {
        return listIssues(token, owner, repo, "open", 1, 50);
    }

    /**
     * Lista issues de un repositorio con estado y paginación.
     */
    public List<Issue> listIssues(String token, String owner, String repo,
                                  String state, int page, int perPage)
            throws IOException, InterruptedException {

        if (token == null || token.isBlank()) {
            throw new IOException("No hay token de GitHub configurado");
        }
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            throw new IOException("No se pudo determinar el repositorio remoto");
        }

        String url = String.format("%s/repos/%s/%s/issues?state=%s&page=%d&per_page=%d",
            apiBase, enc(owner), enc(repo), enc(state == null ? "open" : state), page, perPage);

        HttpResponse<String> response = httpClient.send(
            newRequest(url, token).GET().build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw new IOException("Token de GitHub inválido o revocado (HTTP 401)");
        }
        if (response.statusCode() == 404) {
            throw new IOException("No se encontró el repositorio " + owner + "/" + repo + " (HTTP 404)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        List<Issue> issues = new ArrayList<>();
        JsonNode array = objectMapper.readTree(response.body());
        if (array.isArray()) {
            for (JsonNode node : array) {
                // Los PR también aparecen en /issues; se descartan.
                if (node.has("pull_request")) {
                    continue;
                }
                issues.add(parseIssue(node));
            }
        }
        return issues;
    }

    /**
     * Crea un issue en el repositorio indicado.
     *
     * @param token token con scope {@code repo}
     * @param owner dueño del repositorio
     * @param repo  nombre del repositorio
     * @param title título del issue (no puede estar vacío)
     * @param body  descripción (puede ser null)
     * @return issue creado
     */
    public Issue createIssue(String token, String owner, String repo, String title, String body)
            throws IOException, InterruptedException {

        if (token == null || token.isBlank()) {
            throw new IOException("No hay token de GitHub configurado");
        }
        if (title == null || title.isBlank()) {
            throw new IOException("El título del issue no puede estar vacío");
        }

        String payload = objectMapper.writeValueAsString(java.util.Map.of(
            "title", title.trim(),
            "body", body != null ? body : ""));

        HttpRequest request = HttpRequest.newBuilder(
                URI.create(apiBase + "/repos/" + enc(owner) + "/" + enc(repo) + "/issues"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + token)
            .header("User-Agent", "Samaruc-Editor")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw new IOException("Token de GitHub inválido o revocado (HTTP 401)");
        }
        if (response.statusCode() == 403) {
            throw new IOException("Sin permiso para crear issues (HTTP 403)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        return parseIssue(objectMapper.readTree(response.body()));
    }

    private HttpRequest.Builder newRequest(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + token)
            .header("User-Agent", "Samaruc-Editor");
    }

    static Issue parseIssue(JsonNode node) {
        String title = node.path("title").asText("");
        int number = node.path("number").asInt(0);
        String body = node.path("body").asText("");
        String htmlUrl = node.path("html_url").asText("");
        String state = node.path("state").asText("");
        String userLogin = node.path("user").path("login").asText("");
        String createdAt = node.path("created_at").asText("");
        return new Issue(number, title, body, htmlUrl, state, userLogin, createdAt);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}