package com.retroeditor.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Implementacion de ConfigRepository basada en java.util.Properties.
 */
public class PropertiesConfigRepository implements ConfigRepository {

    @Override
    public Properties load(File configFile) {
        Properties props = new Properties();
        if (configFile == null || !configFile.exists()) return props;

        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
        } catch (Exception ignored) {
        }

        return props;
    }

    @Override
    public void save(File configFile, Properties properties) {
        if (configFile == null || properties == null) return;

        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        } catch (Exception ignored) {
        }

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.store(fos, "Configuracion de Samaruc");
        } catch (Exception ignored) {
        }
    }
}

