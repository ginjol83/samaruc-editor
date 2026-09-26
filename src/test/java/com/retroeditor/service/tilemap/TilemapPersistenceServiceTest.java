package com.retroeditor.service.tilemap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.model.tilemap.TilemapDocument;
import com.retroeditor.model.tilemap.TilemapTileData;

class TilemapPersistenceServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void saveAndLoad_RoundTripsMapAndNativeTileData() throws Exception {
        TilemapPersistenceService service = new TilemapPersistenceService();
        Path file = tempDir.resolve("map.tilemap");
        TilemapDocument document = new TilemapDocument();
        document.setPlatform(TilemapPlatform.GAMEBOY.name());
        document.setMapWidth(2);
        document.setMapHeight(2);
        document.setTileWidth(2);
        document.setTileHeight(2);
        document.setMap(Arrays.asList(0, 1, 2, 3));
        document.setCollisions(Arrays.asList(false, true, false, true));
        document.setSelectedTile(1);
        document.setTiles(List.of(new TilemapTileData(2, 2, Arrays.asList(
                0xFF000000, 0xFFFFFFFF,
                0xFF808080, 0xFF000000))));

        service.save(file, document);
        TilemapDocument loaded = service.load(file);

        assertEquals("samaruc.tilemap", loaded.getFormat());
        assertEquals(1, loaded.getVersion());
        assertEquals(TilemapPlatform.GAMEBOY.name(), loaded.getPlatform());
        assertEquals(2, loaded.getMapWidth());
        assertEquals(2, loaded.getMapHeight());
        assertEquals(document.getMap(), loaded.getMap());
        assertEquals(document.getCollisions(), loaded.getCollisions());
        assertEquals(1, loaded.getSelectedTile());
        assertEquals(2, loaded.getTiles().get(0).getNativeData().size());
        assertTrue(loaded.getTiles().get(0).getNativeData().stream().allMatch(value -> value >= 0 && value <= 255));
    }

    @Test
    void load_RejectsFutureVersion() throws Exception {
        TilemapPersistenceService service = new TilemapPersistenceService();
        Path file = tempDir.resolve("future.tilemap");
        java.nio.file.Files.writeString(file, "{\"format\":\"samaruc.tilemap\",\"version\":2,\"platform\":\"GAMEBOY\","
                + "\"mapWidth\":1,\"mapHeight\":1,\"tileWidth\":8,\"tileHeight\":8,"
                + "\"map\":[0],\"collisions\":[false],\"tiles\":[]}", java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> service.load(file));
    }

    @Test
    void save_RejectsMapWithWrongDimensions() {
        TilemapPersistenceService service = new TilemapPersistenceService();
        TilemapDocument document = new TilemapDocument();
        document.setMapWidth(2);
        document.setMapHeight(2);
        document.setMap(List.of(0));
        document.setCollisions(List.of(false));

        assertThrows(IllegalArgumentException.class, () -> service.save(tempDir.resolve("invalid.tilemap"), document));
    }
}
