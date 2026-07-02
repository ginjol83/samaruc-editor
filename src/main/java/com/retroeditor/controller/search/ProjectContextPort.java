package com.retroeditor.controller.search;

import java.io.File;

/**
 * Puerto para obtener contexto del proyecto actual sin acoplar a MainController.
 */
public interface ProjectContextPort {
    File getCurrentProjectDir();
}

