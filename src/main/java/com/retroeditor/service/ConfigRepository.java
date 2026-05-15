package com.retroeditor.service;

import java.io.File;
import java.util.Properties;

/**
 * Repositorio para cargar/guardar configuracion persistente.
 */
public interface ConfigRepository {
    Properties load(File configFile);
    void save(File configFile, Properties properties);
}

