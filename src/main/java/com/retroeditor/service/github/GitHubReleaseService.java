package com.retroeditor.service.github;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Publicación de GitHub Releases ({@code POST /repos/{owner}/{repo}/releases}) y
 * subida de artefactos compilados a su {@code upload_url}. Requiere un token con
 * scope {@code repo}.
 */
public class GitHubReleaseService {

    private static final String API_BASE = "https://api.github.com";

    /** Quita la plantilla {@code {?name,label}} de {@code upload_url}. */
    static final Pattern UPLOAD_TEMPLATE_PATTERN = Pattern.compile("\\{[^}]*\\}$");

    /** Datos de la release a crear. */
    public record NewRelease(String tagName, String name, String description,
                             boolean draft, boolean prerelease) {}

    /** Release creado; {@code uploadUrl} puede tener la plantilla {@code {?name,label}}. */
    public record ReleaseCreated(String tagName, String htmlUrl, String uploadUrl) {}

    /** Repo de GitHub extraído de una URL remota git. */
    public record OwnerRepo(String owner, String repo) {}

    private final HttpClient httpClient;
    private final String apiBase;
    private final ObjectMapper objectMapper;

    public GitHubReleaseService() {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            API_BASE,
            new ObjectMapper());
    }

    GitHubReleaseService(HttpClient httpClient, String apiBase, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.apiBase = apiBase;
        this.objectMapper = objectMapper;
    }

    /**
     * Crea una release (y su tag si el push de tags del repo lo requiere; GitHub
     * crea automáticamente el tag si no existe cuando {@code create_release} se usa
     * desde objetos… en la API REST el tag se crea en el primer push con ese tag;
     * la API crea un tag ligero si {@code tag_name} no existe en el repo).
     *
     * @param token   token con scope {@code repo}
     * @param owner   dueño del repositorio ({@code user}) o la organización
     * @param repo    nombre del repositorio
     * @param release datos de la release
     * @return release creada (tag, página web y endpoint de subida de assets)
     */
    public ReleaseCreated createRelease(String token, String owner, String repo, NewRelease release)
            throws IOException, InterruptedException {

        if (token == null || token.isBlank()) {
            throw new IOException("No hay token de GitHub configurado");
        }
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            throw new IOException("No se pudo determinar el repositorio remoto (owner/repo)");
        }
        if (release == null || release.tagName() == null || release.tagName().isBlank()) {
            throw new IOException("Indica un tag para el release");
        }

        String tag = release.tagName().trim();
        String body = objectMapper.writeValueAsString(Map.of(
            "tag_name", tag,
            "name", (release.name() != null && !release.name().isBlank()) ? release.name().trim() : tag,
            "body", release.description() != null ? release.description() : "",
            "draft", release.draft(),
            "prerelease", release.prerelease()));

        HttpRequest request = HttpRequest.newBuilder(
                URI.create(apiBase + "/repos/" + enc(owner) + "/" + enc(repo) + "/releases"))
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
        if (response.statusCode() == 404) {
            throw new IOException("No se encontró el repositorio " + owner + "/" + repo
                + " (HTTP 404). Comprueba que lo hayas publicado con 'Compartir proyecto'.");
        }
        if (response.statusCode() == 403) {
            throw new IOException("Sin permiso para publicar releases: tu token necesita el scope 'repo' (HTTP 403)");
        }
        if (response.statusCode() == 422) {
            throw new IOException("GitHub rechazó el release: el tag " + tag
                + " ya existe o el cuerpo es inválido (HTTP 422)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }

        JsonNode node = objectMapper.readTree(response.body());
        return new ReleaseCreated(
            node.path("tag_name").asText(tag),
            node.path("html_url").asText("https://github.com/" + owner + "/" + repo + "/releases/tag/" + tag),
            node.path("upload_url").asText(""));
    }

    /**
     * Sube un artefacto (ROM, ZIP, …) a la {@code uploadUrl} de la release creada.
     *
     * @param token     token con scope {@code repo}
     * @param uploadUrl {@code upload_url} devuelto al crear la release (con o sin plantilla)
     * @param asset     archivo a subir (solo se envía el nombre base como identificador)
     * @throws IOException si GitHub llama 422 (nombre repetido), 401 o 404
     */
    public void uploadAsset(String token, String uploadUrl, Path asset)
            throws IOException, InterruptedException {

        if (asset == null || !java.nio.file.Files.isRegularFile(asset)) {
            throw new IOException("Artefacto no encontrado: " + asset);
        }

        String base = stripUploadTemplate(uploadUrl);
        String name = URLEncoder.encode(asset.getFileName().toString(), StandardCharsets.UTF_8).replace("+", "%20");
        String url = base + (base.contains("?") ? "&" : "?") + "name=" + name;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(120))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + token)
            .header("User-Agent", "Samaruc-Editor")
            .header("Content-Type", contentTypeFor(asset.getFileName().toString()))
            .POST(HttpRequest.BodyPublishers.ofFile(asset))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw new IOException("Token de GitHub inválido o revocado (HTTP 401). Revisa Ajustes > GitHub.");
        }
        if (response.statusCode() == 422) {
            throw new IOException("GitHub rechazó el artefacto " + asset.getFileName()
                + ": ya existe un asset con ese nombre en la release (HTTP 422)");
        }
        if (response.statusCode() == 404) {
            throw new IOException("No se encontró la release o su URL de subida (HTTP 404)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API status: " + response.statusCode());
        }
    }

    /** Quita la plantilla {@code {?name,label}} del final de {@code upload_url}. */
    static String stripUploadTemplate(String uploadUrl) {
        if (uploadUrl == null) {
            return "";
        }
        Matcher m = UPLOAD_TEMPLATE_PATTERN.matcher(uploadUrl);
        return m.replaceFirst("");
    }

    /** Tipo de contenido para subir el asset (ZIPs y binarios de ROM/ejecutables). */
    static String contentTypeFor(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }

    /**
     * Extrae {@code owner}/{@code repo} de una URL remota git, soportando HTTPS,
     * SSH escasa y formato {@code git@host:owner/repo.git}.
     *
     * @return owner y repo, o {@code Optional.empty()} si no se reconocen.
     */
    public static Optional<OwnerRepo> parseOwnerRepo(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return Optional.empty();
        }
        String url = remoteUrl.trim();
        // https://github.com/{owner}/{repo}[.git]
        Matcher https = Pattern.compile("^https?://[^/]+/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?$", Pattern.CASE_INSENSITIVE)
            .matcher(url);
        if (https.matches()) {
            return Optional.of(new OwnerRepo(https.group(1), https.group(2)));
        }
        // git@host:owner/repo[.git]
        Matcher scp = Pattern.compile("^(?:[^/]*@)?[^/:]+:([\\w.-]+)/([\\w.-]+?)(?:\\.git)?$")
            .matcher(url);
        if (scp.matches()) {
            return Optional.of(new OwnerRepo(scp.group(1), scp.group(2)));
        }
        // ssh://git@host/owner/repo[.git]
        Matcher sshUrl = Pattern.compile("^ssh://[^/]+/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?$")
            .matcher(url);
        if (sshUrl.matches()) {
            return Optional.of(new OwnerRepo(sshUrl.group(1), sshUrl.group(2)));
        }
        return Optional.empty();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}