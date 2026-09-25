package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TodoScanServiceTest {

    private final TodoScanService service = new TodoScanService();

    @Test
    void detectsTodoAndFixmeInProject(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("main.c"),
                "void loop() {\n    // TODO: optimizar el bucle\n    int a = 0; // FIXME revisar\n}\n");
        Files.writeString(tempDir.resolve("notes.txt"), "todo: nada aqui\n");

        List<TodoScanService.TodoItem> items = service.scanProject(tempDir.toFile());
        assertEquals(3, items.size());
        long cItems = items.stream().filter(i -> i.getFile().getName().equals("main.c")).count();
        assertEquals(2, cItems);
        TodoScanService.TodoItem first = items.stream()
                .filter(i -> i.getFile().getName().equals("main.c"))
                .findFirst().orElseThrow();
        assertEquals(2, first.getLine());
        assertEquals("optimizar el bucle", first.getMessage());
    }

    @Test
    void ignoresCleanFile(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("main.c"), "int clean = 1;\n// normal comment\n");
        List<TodoScanService.TodoItem> items = service.scanProject(tempDir.toFile());
        assertTrue(items.isEmpty());
    }

    @Test
    void scansSingleFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("loop.asm");
        Files.writeString(file, "; HACK: acelera la rutina\n nop\n");
        List<TodoScanService.TodoItem> items = service.scanFile(file.toFile());
        assertEquals(1, items.size());
        assertEquals(1, items.get(0).getLine());
    }
}