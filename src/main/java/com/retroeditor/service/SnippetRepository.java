package com.retroeditor.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.retroeditor.service.SnippetService.NamedSnippet;

/**
 * Persistencia de snippets en {@code ~/.samaruc-editor/snippets.properties}
 * mediante {@link ConfigRepository}. El orden de la lista se conserva con
 * claves explícitas ({@code snippet.N.name} / {@code snippet.N.template}).
 */
public class SnippetRepository {

    private static final String NAME_KEY  = "snippet.%d.name";
    private static final String TEXT_KEY  = "snippet.%d.template";

    private final ConfigRepository configRepository;
    private final File snippetsFile;

    public SnippetRepository(ConfigRepository configRepository, File snippetsFile) {
        this.configRepository = configRepository;
        this.snippetsFile = snippetsFile;
    }

    /** Archivo por defecto en el directorio del usuario ({@code user.home}). */
    public static File defaultFile() {
        return new File(System.getProperty("user.home"), ".samaruc-editor/snippets.properties");
    }

    public List<NamedSnippet> load() {
        Properties props = configRepository.load(snippetsFile);
        List<NamedSnippet> result = new ArrayList<>();
        for (int i = 0; ; i++) {
            String name = props.getProperty(String.format(NAME_KEY, i));
            String template = props.getProperty(String.format(TEXT_KEY, i));
            if (name == null && template == null) break;
            if (name != null && !name.isBlank() && template != null) {
                result.add(new NamedSnippet(name.trim(), template));
            }
        }
        return result;
    }

    public void save(List<NamedSnippet> snippets) {
        Properties props = new Properties();
        int index = 0;
        for (NamedSnippet snippet : snippets) {
            if (snippet == null || snippet.name() == null || snippet.name().isBlank()) continue;
            props.setProperty(String.format(NAME_KEY, index), snippet.name().trim());
            props.setProperty(String.format(TEXT_KEY, index),
                snippet.template() != null ? snippet.template() : "");
            index++;
        }
        configRepository.save(snippetsFile, props);
    }
}