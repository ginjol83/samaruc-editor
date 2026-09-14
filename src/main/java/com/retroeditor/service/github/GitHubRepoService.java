package com.retroeditor.service.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Operaciones de cuenta GitHub autenticadas: creación de repositorios
 * ({@code POST /user/repos}). Requiere un token con scope {@code repo}.
 */
public class GitHubRepoService {

    private static final String API_BASE = "https://api.github.com";

    /** Repositorio creado en GitHub. */
    public record CreatedRepo(String name, String fullName, String htmlUrl, String cloneUrl) {}

    private final HttpClient httpClient;
    private final String apiBase;
    private final ObjectMapper objectMapper;

    public GitHubRepoService() {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            API_BASE,
            new ObjectMapper());
    }

    GitHubRepoService(HttpClient httpClient, String apiBase, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.apiBase = apiBase;
        this.objectMapper = objectMapper;
    }

    /**
     * Crea un repositorio en la cuenta del usuario autenticado.
     *
     * @param token       token con scope {@code repo}
     * @param name        nombre del repositorio
     * @param description descripción (puede ser null)
     * @param isPrivate   visibilidad del repositorio
     * @return datos del repositorio creado
     * @throws IOException si el token no es válido, el nombre ya existe o la API falla
     */
    public CreatedRepo createRepository(String token, String name, String description, boolean isPrivate)
            throws IOException, InterruptedException {

        if (token == null || token.isBlank()) {
            throw new IOException("No hay token de GitHub configurado");
        }
        if (name == null || name.isBlank()) {
            throw new IOException("El nombre del repositorio no puede estar vacío");
        }

        String body = objectMapper.writeValueAsString(
            java.util.Map.of(
                "name", name.trim(),
                "description", description != null ? description : "",
                "private", isPrivate));

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/user/repos"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + token)
            .header("User-Agent", "Samaruc-Editor")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw new IOException("Token de GitHub inválido o revocado (HTTP 401). Revisa Ajustes > GitHub.");
        }
        if (response.statusCode() == 422) {
            throw new IOException("GitHub rechazó la creación: el repositorio '" + name + "' probablemente ya existe (HTTP 422)");
        }
        if (response.statusCode() == 403) {
            throw new IOException("Sin permiso para crear repositorios: tu token necesita el scope 'repo' (HTTP 403)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        JsonNode node = objectMapper.readTree(response.body());
        return new CreatedRepo(
            node.path("name").asText(name.trim()),
            node.path("full_name").asText(""),
            node.path("html_url").asText(""),
            node.path("clone_url").asText(""));
    }
}