package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas unitarias de {@link CompilationDiagnosticParserService}.
 *
 * <p>Este conjunto valida que el parser reconozca formatos de salida de compilacion
 * usados por distintas toolchains (mensajes genericos y z88dk), y que transforme
 * lineas de consola en ubicaciones navegables (archivo + linea + severidad).
 */
class CompilationDiagnosticParserServiceTest {

    private final CompilationDiagnosticParserService service = new CompilationDiagnosticParserService();

    @TempDir
    Path tempDir;

    /**
     * Escenario: la consola contiene un mensaje de finalizacion en espanol.
     *
     * <p>Verifica que el parser extrae correctamente el codigo numerico de salida
     * cuando aparece en una cadena del tipo "Compilacion finalizada. Codigo de salida: N".
     */
    @Test
    void parseCompilationExitCodeSpanish() {
        Integer code = service.parseCompilationExitCode("Compilacion finalizada. Codigo de salida: 1");
        Assertions.assertEquals(1, code);
    }

    /**
     * Escenario: diagnostico en formato clasico "file:line: error: ...".
     *
     * <p>Valida que se resuelven archivo y linea mediante el resolver inyectado,
     * y que la severidad queda marcada como error (no warning).
     */
    @Test
    void parseCompilerDiagnosticLocationFileLine() throws IOException {
        Path source = tempDir.resolve("main.c");
        Files.writeString(source, "int main() { return 0; }");

        CompilationDiagnosticParserService.DiagnosticLocation location = service.parseCompilerDiagnosticLocation(
            source.toFile(),
            "main.c:12: error: expected ';'",
            raw -> {
                if ("main.c".equals(raw)) return source.toFile();
                return null;
            }
        );

        Assertions.assertNotNull(location);
        Assertions.assertEquals(source.toFile().getAbsolutePath(), location.getFile().getAbsolutePath());
        Assertions.assertEquals(12, location.getLine());
        Assertions.assertFalse(location.isWarning());
    }

    /**
     * Escenario: salida prefijada por z88dk con patron "zcc: 'file' L: &lt;linea&gt; warning: ...".
     *
     * <p>Comprueba que el parser detecta el numero de linea desde "L:" y clasifica
     * la entrada como warning.
     */
    @Test
    void parseCompilerDiagnosticLocationZ88dkPrefixed() throws IOException {
        Path source = tempDir.resolve("game.c");
        Files.writeString(source, "void f(){}\n");

        CompilationDiagnosticParserService.DiagnosticLocation location = service.parseCompilerDiagnosticLocation(
            source.toFile(),
            "zcc: 'game.c' L: 34 warning: something",
            raw -> {
                if ("game.c".equals(raw)) return source.toFile();
                return null;
            }
        );

        Assertions.assertNotNull(location);
        Assertions.assertEquals(34, location.getLine());
        Assertions.assertTrue(location.isWarning());
    }

    /**
     * Escenario: linea de consola en formato "file:line: warning ...".
     *
     * <p>Verifica la conversion a una ubicacion navegable cuando existe resolver,
     * manteniendo la linea correcta y el archivo absoluto.
     */
    @Test
    void parseConsoleFileLineResolvesExistingFile() throws IOException {
        Path source = tempDir.resolve("demo.c");
        Files.writeString(source, "int x;\n");

        CompilationDiagnosticParserService.DiagnosticLocation location = service.parseConsoleFileLine(
            "demo.c:9: warning 123",
            raw -> {
                if ("demo.c".equals(raw)) return source.toFile();
                return null;
            }
        );

        Assertions.assertNotNull(location);
        Assertions.assertEquals(9, location.getLine());
        Assertions.assertEquals(source.toFile().getAbsolutePath(), location.getFile().getAbsolutePath());
    }

    /**
     * Escenario negativo: no se proporciona resolver de rutas.
     *
     * <p>Sin resolver no se puede mapear el nombre de archivo crudo a un archivo real,
     * por lo que el resultado esperado es {@code null}.
     */
    @Test
    void parseCompilerDiagnosticLocationReturnsNullWithoutResolver() {
        CompilationDiagnosticParserService.DiagnosticLocation location = service.parseCompilerDiagnosticLocation(
            new File("x.c"),
            "x.c:10: error: boom",
            null
        );
        Assertions.assertNull(location);
    }
}

