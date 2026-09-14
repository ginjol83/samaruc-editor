package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.retroeditor.model.EditorTheme;

/**
 * Implementación de {@link ThemeRepository} basada en un archivo JSON.
 *
 * <p>Guarda la lista de modos personalizados (nunca los incorporados, que se
 * definen en código) en {@code ~/.retroeditor-themes.json}. Si el archivo no
 * existe o está corrupto, devuelve una lista vacía sin lanzar excepciones.</p>
 */
public class JsonThemeRepository implements ThemeRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonThemeRepository() {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public List<EditorTheme> load(File themesFile) {
        if (themesFile == null || !themesFile.isFile()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(themesFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, EditorTheme.class)));
        } catch (IOException e) {
            System.err.println("Error al cargar modos de apariencia: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void save(File themesFile, List<EditorTheme> themes) {
        if (themesFile == null || themes == null) return;

        try {
            File parent = themesFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        } catch (Exception ignored) {
        }

        try {
            objectMapper.writeValue(themesFile, themes);
        } catch (IOException e) {
            System.err.println("Error al guardar modos de apariencia: " + e.getMessage());
        }
    }
}