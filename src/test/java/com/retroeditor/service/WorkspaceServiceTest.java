package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.model.WorkspaceModel;

class WorkspaceServiceTest {

    private final WorkspaceService service = new WorkspaceService();

    @TempDir
    Path tempDir;

    @Test
    void loadWorkspaceWithoutFileReturnsModelWithProjectName() {
        Path project = tempDir.resolve("proyecto");
        try {
            Files.createDirectory(project);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        WorkspaceModel model = service.loadWorkspace(project.toFile());

        assertNotNull(model);
        assertEquals("proyecto", model.getProjectName());
    }

    @Test
    void saveAndLoadRoundTripsSettings() throws Exception {
        Path project = tempDir.resolve("proyecto");
        Files.createDirectory(project);

        WorkspaceModel model = service.loadWorkspace(project.toFile());
        model.setSetting("compilador_seleccionado", "gcc");
        model.setSetting("gcc_opt_level", "-O2");
        model.setSetting("gcc_extra_args", "-Wall -Wextra -g");
        model.setSetting("gcc_includes", "C:\\proyecto\\include");

        service.saveWorkspace(project.toFile(), model);

        WorkspaceModel reloaded = service.loadWorkspace(project.toFile());
        assertEquals("proyecto", reloaded.getProjectName());
        assertEquals("gcc", reloaded.getSetting("compilador_seleccionado"));
        assertEquals("-O2", reloaded.getSetting("gcc_opt_level"));
        assertEquals("-Wall -Wextra -g", reloaded.getSetting("gcc_extra_args"));
        assertEquals("C:\\proyecto\\include", reloaded.getSetting("gcc_includes"));
    }
}
