package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.retroeditor.util.AppInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomeReleaseNotesServiceTest {

    private final HomeReleaseNotesService service = new HomeReleaseNotesService();

    @TempDir
    Path tempDir;

    @Test
    void loadsVersionAndLatestItemsFromChangelog() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.test</groupId>
              <artifactId>demo</artifactId>
              <version>2.3.4</version>
            </project>
            """, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("CHANGELOG.md"), """
            # Changelog

            ## 2.3.4 - 2026-07-05
            - Added tile editor
            - Added JSON highlighting

            ## 2.3.3 - 2026-06-20
            - Previous release
            """, StandardCharsets.UTF_8);

        HomeReleaseNotesService.ReleaseNotes notes = service.load(tempDir, "fallback");

        assertEquals("2.3.4", notes.version());
        assertTrue(notes.fromChangelog());
        assertEquals("2.3.4 - 2026-07-05", notes.changelogSection());
        assertEquals(2, notes.items().size());
        assertEquals("Added tile editor", notes.items().get(0));
    }

    @Test
    void usesFallbackWhenChangelogMissing() {
        HomeReleaseNotesService.ReleaseNotes notes = service.load(tempDir, "no notes");

        assertFalse(notes.fromChangelog());
        assertEquals(AppInfo.getVersion(), notes.version());
        assertEquals(1, notes.items().size());
        assertEquals("no notes", notes.items().get(0));
    }
    @Test
    void loadsItemsWithSubSectionsFromChangelog() throws Exception {
        Files.writeString(tempDir.resolve("CHANGELOG.md"), """
            # Changelog
            
            ## [1.2.0] - 2026-07-10
            ### Added
            - Feature A
            - Feature B
            ### Fixed
            - Bug X
            
            ## 1.1.0
            - Old feature
            """, StandardCharsets.UTF_8);

        HomeReleaseNotesService.ReleaseNotes notes = service.load(tempDir, "fallback");

        assertTrue(notes.fromChangelog());
        assertEquals(AppInfo.getVersion(), notes.version()); // Pom missing falls back to app version
        assertEquals("1.2.0", notes.changelogSection());
        assertEquals(3, notes.items().size());
        assertEquals("[Added] Feature A", notes.items().get(0));
        assertEquals("[Added] Feature B", notes.items().get(1));
        assertEquals("[Fixed] Bug X", notes.items().get(2));
    }
}

