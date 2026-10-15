package com.retroeditor.service.ai;

import java.io.File;
import java.util.Properties;

import com.retroeditor.service.ConfigRepository;
import com.retroeditor.service.PropertiesConfigRepository;

/**
 * Guarda los ajustes de IA en su propio fichero, {@code ~/.samaruc-editor/ai.properties},
 * y no mezclados en {@code ~/.retroeditor.properties}: son preferencias de una
 * integracion opcional y asi se pueden borrar sin tocar el resto de la configuracion.
 */
public class AiSettingsRepository {

    private static final String DIRECTORY_NAME = ".samaruc-editor";
    private static final String FILE_NAME = "ai.properties";

    private final ConfigRepository repository;

    public AiSettingsRepository() {
        this(new PropertiesConfigRepository());
    }

    public AiSettingsRepository(ConfigRepository repository) {
        this.repository = repository != null ? repository : new PropertiesConfigRepository();
    }

    public static File defaultSettingsFile() {
        return new File(new File(System.getProperty("user.home"), DIRECTORY_NAME), FILE_NAME);
    }

    public AiSettings load() {
        return load(defaultSettingsFile());
    }

    public AiSettings load(File file) {
        Properties props = repository.load(file);
        return AiSettings.fromProperties(props);
    }

    public void save(AiSettings settings) {
        save(defaultSettingsFile(), settings);
    }

    public void save(File file, AiSettings settings) {
        repository.save(file, settings != null ? settings.toProperties() : AiSettings.defaults().toProperties());
    }
}