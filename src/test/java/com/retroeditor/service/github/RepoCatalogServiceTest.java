package com.retroeditor.service.github;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Pruebas unitarias de {@link RepoCatalogService}.
 */
class RepoCatalogServiceTest {

    private final RepoCatalogService service = new RepoCatalogService();

    @Test
    void loadCatalogReturnsNonEmptyList() throws Exception {
        List<CuratedRepo> catalog = service.loadCatalog();
        Assertions.assertFalse(catalog.isEmpty(), "El catálogo no debería estar vacío");
    }

    @Test
    void loadCatalogContainsExpectedCategories() throws Exception {
        List<CuratedRepo> catalog = service.loadCatalog();
        boolean hasGbdk = catalog.stream().anyMatch(r -> "gbdk".equals(r.category()));
        boolean hasTool = catalog.stream().anyMatch(r -> "tool".equals(r.category()));
        Assertions.assertTrue(hasGbdk, "Debería haber repos GBDK");
        Assertions.assertTrue(hasTool, "Debería haber repos de herramientas");
    }

    @Test
    void getByCategoryFiltersCorrectly() throws Exception {
        List<CuratedRepo> gbdkRepos = service.getByCategory("gbdk");
        Assertions.assertFalse(gbdkRepos.isEmpty());
        for (CuratedRepo r : gbdkRepos) {
            Assertions.assertEquals("gbdk", r.category());
        }
    }

    @Test
    void getByCategoryReturnsAllForNull() throws Exception {
        List<CuratedRepo> all = service.getByCategory(null);
        List<CuratedRepo> fullCatalog = service.loadCatalog();
        Assertions.assertEquals(fullCatalog.size(), all.size());
    }

    @Test
    void searchFindsMatchingRepos() throws Exception {
        List<CuratedRepo> results = service.search("Game Boy");
        Assertions.assertFalse(results.isEmpty(), "Debería encontrar repos con 'Game Boy'");
    }

    @Test
    void searchReturnsAllForBlankQuery() throws Exception {
        List<CuratedRepo> all = service.search("  ");
        List<CuratedRepo> fullCatalog = service.loadCatalog();
        Assertions.assertEquals(fullCatalog.size(), all.size());
    }

    @Test
    void getCategoriesReturnsDistinctSorted() throws Exception {
        List<String> categories = service.getCategories();
        Assertions.assertFalse(categories.isEmpty());
        // No duplicates
        Assertions.assertEquals(categories.size(), categories.stream().distinct().count());
    }

    @Test
    void invalidateCacheAllowsReload() throws Exception {
        service.loadCatalog();
        service.invalidateCache();
        List<CuratedRepo> reloaded = service.loadCatalog();
        Assertions.assertFalse(reloaded.isEmpty());
    }
}
