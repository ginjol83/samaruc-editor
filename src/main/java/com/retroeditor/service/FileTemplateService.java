package com.retroeditor.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Servicio de plantillas para nuevos archivos de proyecto.
 * Carga el contenido de las plantillas desde los recursos y permite crear
 * archivos a partir de ellas evitando colisiones de nombres.
 */
public class FileTemplateService {

    public record FileTemplate(String id, String name, String description, String fileName, String content) {
    }

    private record TemplateDefinition(String id, String name, String description,
                                      String nameKey, String descriptionKey,
                                      String fileName, String resourcePath) {
    }

    private static final List<TemplateDefinition> DEFINITIONS = List.of(
        new TemplateDefinition(
            "gb-c",
            "Game Boy (C / GBDK)",
            "Plantilla basica de juego para Game Boy con GBDK-2020.",
            "template.gb-c.name",
            "template.gb-c.description",
            "gb_main.c",
            "/templates/gb_main.c"),
        new TemplateDefinition(
            "zx-c",
            "ZX Spectrum (C / Z88DK)",
            "Plantilla basica para ZX Spectrum con Z88DK.",
            "template.zx-c.name",
            "template.zx-c.description",
            "zx_main.c",
            "/templates/zx_main.c"),
        new TemplateDefinition(
            "cpc-c",
            "Amstrad CPC (C / Z88DK)",
            "Plantilla basica para Amstrad CPC con Z88DK.",
            "template.cpc-c.name",
            "template.cpc-c.description",
            "cpc_main.c",
            "/templates/cpc_main.c"),
        new TemplateDefinition(
            "asm",
            "Ensamblador (z80 / Game Boy)",
            "Plantilla de ensamblador z80 para Game Boy.",
            "template.asm.name",
            "template.asm.description",
            "asm_main.asm",
            "/templates/asm_main.asm"),
        new TemplateDefinition(
            "c",
            "C generico",
            "Plantilla generica en C para cualquier plataforma.",
            "template.c.name",
            "template.c.description",
            "main.c",
            "/templates/generic_main.c"),
        new TemplateDefinition(
            "native-c",
            "C normal (gcc)",
            "Plantilla basica de C normal para compilar y ejecutar de forma nativa con GCC.",
            "template.native-c.name",
            "template.native-c.description",
            "main.c",
            "/templates/native_main.c"),
        new TemplateDefinition(
            "native-c-calc",
            "C normal (gcc) - calculadora",
            "Ejemplo de consola en C normal con bucles y entrada por teclado.",
            "template.native-c-calc.name",
            "template.native-c-calc.description",
            "calc.c",
            "/templates/native_calc.c"),
        new TemplateDefinition(
            "cc65",
            "CC65 (Atari XE/XL)",
            "Plantilla basica para Atari XE/XL con CC65.",
            "template.cc65.name",
            "template.cc65.description",
            "main.c",
            "/templates/cc65_main.c"),
        new TemplateDefinition(
            "dos-c",
            "MS-DOS (C / Open Watcom / Turbo C)",
            "Plantilla basica para MS-DOS en C con conio y stdio.",
            "template.dos-c.name",
            "template.dos-c.description",
            "dos_main.c",
            "/templates/dos_main.c"),
        new TemplateDefinition(
            "dos-asm",
            "MS-DOS Assembly (x86 / NASM)",
            "Plantilla basica de ensamblador x86 para MS-DOS (.com).",
            "template.dos-asm.name",
            "template.dos-asm.description",
            "dos_main.asm",
            "/templates/dos_main.asm"),
        new TemplateDefinition(
            "sms-c",
            "Sega Master System (C / SDCC)",
            "Plantilla basica para Sega Master System en C con SMSLib.",
            "template.sms-c.name",
            "template.sms-c.description",
            "sms_main.c",
            "/templates/sms_main.c"),
        new TemplateDefinition(
            "sms-asm",
            "Sega Master System Assembly (Z80)",
            "Plantilla de ensamblador Z80 para Sega Master System.",
            "template.sms-asm.name",
            "template.sms-asm.description",
            "sms_main.asm",
            "/templates/sms_main.asm")
    );

    /**
     * Devuelve las plantillas disponibles con su contenido cargado desde los recursos.
     * @return Lista de plantillas; se omiten las que no puedan cargarse.
     */
    public List<FileTemplate> getTemplates() {
        return getTemplates(null);
    }

    /**
     * Devuelve las plantillas disponibles traduciendo nombre y descripcion mediante
     * el traductor proporcionado. Si el traductor no resuelve una clave, se usa el
     * texto por defecto (espanol).
     * @param translator Funcion que resuelve una clave i18n a su texto; puede ser null.
     * @return Lista de plantillas; se omiten las que no puedan cargarse.
     */
    public List<FileTemplate> getTemplates(Function<String, String> translator) {
        List<FileTemplate> templates = new ArrayList<>();
        for (TemplateDefinition definition : DEFINITIONS) {
            String content = loadContent(definition.resourcePath());
            if (content != null) {
                templates.add(new FileTemplate(
                    definition.id(),
                    resolveText(translator, definition.nameKey(), definition.name()),
                    resolveText(translator, definition.descriptionKey(), definition.description()),
                    definition.fileName(),
                    content));
            }
        }
        return templates;
    }

    private String resolveText(Function<String, String> translator, String key, String fallback) {
        if (translator != null) {
            String translated = translator.apply(key);
            if (translated != null && !translated.isBlank()) {
                return translated;
            }
        }
        return fallback;
    }

    /**
     * Crea un archivo a partir de una plantilla en el directorio dado.
     * Si el nombre ya existe, se genera un nombre alternativo (ej. main-2.c).
     * @param template Plantilla a usar.
     * @param parentDirectory Directorio destino.
     * @return Ruta del archivo creado.
     * @throws IOException Si no se puede escribir el archivo.
     */
    public Path createTemplateFile(FileTemplate template, Path parentDirectory) throws IOException {
        if (template == null) {
            throw new IllegalArgumentException("La plantilla no puede ser null");
        }
        if (parentDirectory == null || !Files.isDirectory(parentDirectory)) {
            throw new IOException("Directorio de destino no valido: " + parentDirectory);
        }

        Path target = uniqueFile(parentDirectory, template.fileName());
        Files.writeString(target, template.content(), StandardCharsets.UTF_8);
        return target;
    }

    private String loadContent(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }

    private Path uniqueFile(Path directory, String fileName) throws IOException {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) return candidate;

        String base = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        for (int i = 2; i < 1000; i++) {
            Path alternative = directory.resolve(base + "-" + i + extension);
            if (!Files.exists(alternative)) return alternative;
        }
        throw new IOException("No se pudo generar un nombre unico para " + fileName);
    }
}
