package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTemplateServiceTest {

    private final FileTemplateService service = new FileTemplateService();

    @TempDir
    Path tempDir;

    @Test
    void exposesNativeCAndGccTemplates() {
        List<FileTemplateService.FileTemplate> templates = service.getTemplates();

        Optional<FileTemplateService.FileTemplate> main = templates.stream()
            .filter(t -> "native-c".equals(t.id()))
            .findFirst();
        Optional<FileTemplateService.FileTemplate> calc = templates.stream()
            .filter(t -> "native-c-calc".equals(t.id()))
            .findFirst();

        assertTrue(main.isPresent(), "Expected native-c template");
        assertTrue(calc.isPresent(), "Expected native-c-calc template");
        assertTrue(main.get().content().contains("main"));
        assertTrue(calc.get().content().contains("calculadora"));
    }

    @Test
    void createsUniqueFilesFromNativeTemplates() throws Exception {
        FileTemplateService.FileTemplate main = service.getTemplates().stream()
            .filter(t -> "native-c".equals(t.id()))
            .findFirst()
            .orElseThrow();

        Path created = service.createTemplateFile(main, tempDir);
        assertNotNull(created);
        assertTrue(Files.exists(created));
        assertEquals("main.c", created.getFileName().toString());

        Path second = service.createTemplateFile(main, tempDir);
        assertEquals("main-2.c", second.getFileName().toString());
        assertTrue(Files.exists(second));
    }

    @Test
    void translatesNamesAndDescriptionsWhenTranslatorProvided() {
        List<FileTemplateService.FileTemplate> translated = service.getTemplates(key -> {
            switch (key) {
                case "template.native-c.name": return "Native C";
                case "template.native-c.description": return "Native C template";
                case "template.native-c-calc.name": return "Calculator";
                default: return null;
            }
        });

        FileTemplateService.FileTemplate main = translated.stream()
            .filter(t -> "native-c".equals(t.id()))
            .findFirst()
            .orElseThrow();
        FileTemplateService.FileTemplate calc = translated.stream()
            .filter(t -> "native-c-calc".equals(t.id()))
            .findFirst()
            .orElseThrow();

        assertEquals("Native C", main.name());
        assertEquals("Native C template", main.description());
        assertEquals("Calculator", calc.name());
    }

    @Test
    void fallsBackToDefaultTextForMissingTranslation() {
        List<FileTemplateService.FileTemplate> templates = service.getTemplates(key -> null);

        FileTemplateService.FileTemplate main = templates.stream()
            .filter(t -> "native-c".equals(t.id()))
            .findFirst()
            .orElseThrow();

        assertEquals("C normal (gcc)", main.name());
        assertTrue(main.description().contains("GCC"));
    }
}
