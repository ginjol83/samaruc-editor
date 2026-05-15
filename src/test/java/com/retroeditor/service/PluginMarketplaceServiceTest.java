package com.retroeditor.service;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.retroeditor.model.PluginMarketplaceItem;

/**
 * Pruebas unitarias de parseo y normalizacion del marketplace remoto.
 */
class PluginMarketplaceServiceTest {

    private final PluginMarketplaceService service = new PluginMarketplaceService();

    /**
     * Valida que el parser acepta el formato real del repositorio:
     * objeto raiz con `plugins` como mapa por id.
     */
    @Test
    void parseCatalogReadsPluginsMap() throws Exception {
        String json = """
            {
              \"plugins\": {
                \"demo-plugin\": {
                  \"name\": \"Demo Plugin\",
                  \"version\": \"1.2.3\",
                  \"description\": \"Plugin de prueba\",
                  \"author\": \"QA\",
                  \"category\": \"tools\",
                  \"downloadUrl\": \"https://example.org/demo-plugin-1.2.3.jar\",
                  \"fileSize\": \"1 MB\",
                  \"verified\": true
                }
              }
            }
            """;

        List<PluginMarketplaceItem> items = service.parseCatalog(json);

        Assertions.assertEquals(1, items.size());
        PluginMarketplaceItem item = items.get(0);
        Assertions.assertEquals("demo-plugin", item.id());
        Assertions.assertEquals("Demo Plugin", item.name());
        Assertions.assertEquals("1.2.3", item.version());
        Assertions.assertEquals("https://example.org/demo-plugin-1.2.3.jar", item.downloadUrl());
        Assertions.assertTrue(item.verified());
    }

    /**
     * Si falta `downloadUrl`, el item no debe mostrarse como instalable.
     */
    @Test
    void parseCatalogSkipsItemsWithoutDownloadUrl() throws Exception {
        String json = """
            {
              \"plugins\": {
                \"invalid-plugin\": {
                  \"name\": \"Invalid\",
                  \"version\": \"1.0.0\"
                }
              }
            }
            """;

        List<PluginMarketplaceItem> items = service.parseCatalog(json);
        Assertions.assertTrue(items.isEmpty());
    }

    /**
     * Comprueba que el nombre de destino del JAR se toma de la URL si existe.
     */
    @Test
    void buildFileNamePrefersJarFromUrl() {
        PluginMarketplaceItem item = new PluginMarketplaceItem(
            "demo-plugin", "Demo", "1.0", "", "", "", "https://example.org/plugins/demo-1.0.0.jar", "", false
        );

        String fileName = service.buildFileName(item);

        Assertions.assertEquals("demo-1.0.0.jar", fileName);
    }

    @Test
    void normalizeGithubBlobUrlConvertsToRaw() {
        String blobUrl = "https://github.com/ginjol83/plugins-repo/blob/main/plugins.json";

        String normalized = service.normalizeGithubBlobUrl(blobUrl);

        Assertions.assertEquals(
            "https://raw.githubusercontent.com/ginjol83/plugins-repo/main/plugins.json",
            normalized
        );
    }

    @Test
    void normalizeGithubBlobUrlKeepsNonGithubUrls() {
        String url = "https://example.org/plugins.json";

        String normalized = service.normalizeGithubBlobUrl(url);

        Assertions.assertEquals(url, normalized);
    }
}

