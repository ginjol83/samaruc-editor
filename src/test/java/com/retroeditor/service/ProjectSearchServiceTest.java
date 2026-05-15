package com.retroeditor.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * Pruebas unitarias de {@link ProjectSearchService}.
 *
 * <p>Valida el comportamiento de busqueda de texto sobre un arbol de archivos,
 * incluyendo casos nominales y validaciones de entrada (raiz invalida y termino vacio).
 */
class ProjectSearchServiceTest {

    private final ProjectSearchService service = new ProjectSearchService();

    @TempDir
    Path tempDir;

    /**
     * Escenario: dos archivos en proyecto, solo uno contiene el termino buscado.
     *
     * <p>Se espera exactamente una coincidencia, con ruta del archivo correcta
     * e indice de match no negativo dentro del contenido.
     */
    @Test
    void searchProjectFindsMatchesInFiles() throws IOException {
        Path file1 = tempDir.resolve("main.c");
        Path file2 = tempDir.resolve("other.txt");
        Files.writeString(file1, "int main() { return 0; }");
        Files.writeString(file2, "sin coincidencia");

        List<ProjectSearchService.Match> matches = service.searchProject(tempDir.toFile(), "main");

        Assertions.assertEquals(1, matches.size());
        Assertions.assertEquals(file1.toFile().getAbsolutePath(), matches.get(0).getFile().getAbsolutePath());
        Assertions.assertTrue(matches.get(0).getIndex() >= 0);
    }

    /**
     * Escenario negativo: la raiz del proyecto no se proporciona.
     *
     * <p>El servicio debe responder con lista vacia sin lanzar excepcion.
     */
    @Test
    void searchProjectReturnsEmptyForInvalidRoot() {
        List<ProjectSearchService.Match> matches = service.searchProject(null, "main");
        Assertions.assertTrue(matches.isEmpty());
    }

    /**
     * Escenario negativo: el termino de busqueda contiene solo espacios.
     *
     * <p>No debe iniciar recorrido de archivos y el resultado esperado es vacio.
     */
    @Test
    void searchProjectReturnsEmptyForBlankTerm() {
        List<ProjectSearchService.Match> matches = service.searchProject(tempDir.toFile(), "  ");
        Assertions.assertTrue(matches.isEmpty());
    }
}

