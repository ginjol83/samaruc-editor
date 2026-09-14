package com.retroeditor.service;

import java.io.File;
import java.util.List;

import com.retroeditor.model.EditorTheme;

/**
 * Repositorio de modos de apariencia personalizados.
 */
public interface ThemeRepository {
    List<EditorTheme> load(File themesFile);
    void save(File themesFile, List<EditorTheme> themes);
}