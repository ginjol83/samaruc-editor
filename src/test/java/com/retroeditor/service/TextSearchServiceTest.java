package com.retroeditor.service;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de {@link TextSearchService}.
 *
 * <p>Este conjunto cubre busqueda siguiente/anterior, sensibilidad a mayusculas,
 * modo regex y recuperacion de todas las coincidencias con sus rangos.
 */
class TextSearchServiceTest {

    private final TextSearchService service = new TextSearchService();

    /**
     * Escenario: el cursor inicial esta al final del texto y se busca hacia delante.
     *
     * <p>Con wrap habilitado, la busqueda debe reiniciar desde el inicio y devolver
     * el primer rango de coincidencia disponible.
     */
    @Test
    void findNextWrapsWhenNeeded() {
        TextSearchService.MatchRange match = service.findNext("abc def abc", "abc", 9, true, false);

        Assertions.assertNotNull(match);
        Assertions.assertEquals(0, match.getStart());
        Assertions.assertEquals(3, match.getEnd());
    }

    /**
     * Escenario: busqueda hacia atras ignorando mayusculas/minusculas.
     *
     * <p>Debe localizar la ultima coincidencia valida antes de la posicion inicial,
     * incluso cuando el patron y el texto usan diferente casing.
     */
    @Test
    void findPreviousCaseInsensitive() {
        TextSearchService.MatchRange match = service.findPrevious("Uno DOS tres dos", "dos", -1, false, false);

        Assertions.assertNotNull(match);
        Assertions.assertEquals(13, match.getStart());
        Assertions.assertEquals(16, match.getEnd());
    }

    /**
     * Escenario negativo: patron regex sintacticamente invalido.
     *
     * <p>El servicio debe fallar de forma controlada (sin excepcion propagada)
     * y devolver {@code null} para indicar ausencia de match utilizable.
     */
    @Test
    void findNextWithInvalidRegexReturnsNull() {
        TextSearchService.MatchRange match = service.findNext("abc", "[", 0, true, true);
        Assertions.assertNull(match);
    }

    /**
     * Escenario: extraer todas las coincidencias numericas via regex.
     *
     * <p>Verifica cantidad total de matches y metadatos de rango
     * (inicio y longitud) en resultados representativos.
     */
    @Test
    void findAllRegexReturnsAllMatches() {
        List<TextSearchService.SnippetMatch> matches = service.findAll("a1 b22 c333", "\\d+", true, true);
        Assertions.assertEquals(3, matches.size());
        Assertions.assertEquals(1, matches.get(0).getStart());
        Assertions.assertEquals(2, matches.get(1).getLength());
    }

    @Test
    void replaceFirstLiteralUsesNextMatch() {
        String updated = service.replaceFirst("abc def abc", "abc", "XXX", 0, true, false);
        Assertions.assertEquals("XXX def abc", updated);
    }

    @Test
    void replaceAllRegexReplacesEveryMatch() {
        String updated = service.replaceAll("a1 b22 c333", "\\d+", "#", true, true);
        Assertions.assertEquals("a# b# c#", updated);
    }
}
