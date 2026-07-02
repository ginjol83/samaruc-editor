package com.retroeditor.controller.search;

import java.io.File;

/**
 * Puerto para abrir archivos desde controladores de búsqueda sin dependencia directa.
 */
public interface FileOpenPort {
    void openFileFromExplorer(File file);
}

