package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutoSaveServiceTest {

    @AfterEach
    void resetDir() {
        AutoSaveService.setAutosaveDir(Path.of(System.getProperty("user.home"), ".samaruc-editor", "autosave"));
    }

    @Test
    void savesAndListsSnapshot(@TempDir Path tempDir) throws Exception {
        AutoSaveService.setAutosaveDir(tempDir);
        File source = tempDir.resolve("main.c").toFile();
        AutoSaveService.saveSnapshot(source, "int x = 1;");

        List<AutoSaveService.RecoverableFile> recoverable = AutoSaveService.listRecoverable();
        assertEquals(1, recoverable.size());
        assertEquals(source.getAbsoluteFile(), recoverable.get(0).getOriginalFile());
        assertEquals("int x = 1;", AutoSaveService.loadSnapshotContent(recoverable.get(0).getSnapshotFile()));
    }

    @Test
    void clearsSnapshot(@TempDir Path tempDir) throws Exception {
        AutoSaveService.setAutosaveDir(tempDir);
        File source = tempDir.resolve("main.c").toFile();
        AutoSaveService.saveSnapshot(source, "content");
        assertEquals(1, AutoSaveService.listRecoverable().size());

        AutoSaveService.clearSnapshot(source);
        assertTrue(AutoSaveService.listRecoverable().isEmpty());
    }

    @Test
    void clearAllRemovesEverything(@TempDir Path tempDir) throws Exception {
        AutoSaveService.setAutosaveDir(tempDir);
        AutoSaveService.saveSnapshot(tempDir.resolve("a.c").toFile(), "aa");
        AutoSaveService.saveSnapshot(tempDir.resolve("b.c").toFile(), "bb");
        assertEquals(2, AutoSaveService.listRecoverable().size());

        AutoSaveService.clearAll();
        assertTrue(AutoSaveService.listRecoverable().isEmpty());
    }
}