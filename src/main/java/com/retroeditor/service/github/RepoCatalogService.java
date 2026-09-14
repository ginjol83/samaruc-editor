package com.retroeditor.service.github;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Carga y gestiona el catálogo curado de repositorios retro.
 * El catálogo se embebe en el JAR como recurso classpath.
 */
public class RepoCatalogService {

    private static final String CATALOG_RESOURCE = "/catalog/curated-repos.json";

    private final ObjectMapper objectMapper;
    private List<CuratedRepo> catalog;

    public RepoCatalogService() {
        this(new ObjectMapper());
    }

    RepoCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Carga el catálogo completo desde el classpath.
     */
    public List<CuratedRepo> loadCatalog() throws IOException {
        if (catalog != null) return catalog;

        try (InputStream is = getClass().getResourceAsStream(CATALOG_RESOURCE)) {
            if (is == null) {
                throw new IOException("Catálogo no encontrado: " + CATALOG_RESOURCE);
            }
            catalog = parseCatalog(is);
            return catalog;
        }
    }

    /**
     * Parsea el JSON del catálogo.
     */
    List<CuratedRepo> parseCatalog(InputStream is) throws IOException {
        JsonNode root = objectMapper.readTree(is);
        if (!root.isArray()) return List.of();

        List<CuratedRepo> entries = new ArrayList<>();
        for (JsonNode node : root) {
            String owner = node.path("owner").asText("");
            String name = node.path("repo").asText("");
            String category = node.path("category").asText("general");
            String descriptionEs = node.path("descriptionEs").asText("");
            String descriptionEn = node.path("descriptionEn").asText("");
            int stars = node.path("stars").asInt(0);

            if (!owner.isEmpty() && !name.isEmpty()) {
                entries.add(CuratedRepo.fromJson(owner, name, category, descriptionEs, descriptionEn, stars));
            }
        }
        return entries;
    }

    /**
     * Filtra el catálogo por categoría.
     */
    public List<CuratedRepo> getByCategory(String category) throws IOException {
        if (catalog == null) loadCatalog();
        if (category == null || category.isEmpty() || "all".equals(category)) {
            return catalog;
        }
        return catalog.stream()
            .filter(r -> category.equals(r.category()))
            .collect(Collectors.toList());
    }

    /**
     * Busca en el catálogo por texto.
     */
    public List<CuratedRepo> search(String query) throws IOException {
        if (catalog == null) loadCatalog();
        if (query == null || query.isBlank()) return catalog;

        String lowerQuery = query.toLowerCase();
        return catalog.stream()
            .filter(r -> r.repo().fullName().toLowerCase().contains(lowerQuery)
                || r.repo().description().toLowerCase().contains(lowerQuery)
                || r.category().toLowerCase().contains(lowerQuery))
            .collect(Collectors.toList());
    }

    /**
     * Devuelve todas las categorías disponibles.
     */
    public List<String> getCategories() throws IOException {
        if (catalog == null) loadCatalog();
        return catalog.stream()
            .map(CuratedRepo::category)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Invalida la caché del catálogo (para recargar).
     */
    public void invalidateCache() {
        catalog = null;
    }
}
