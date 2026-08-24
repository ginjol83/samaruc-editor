package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class StaticAnalysisServiceTest {

    @Test
    void analyzesValidCAnCodeWithoutFalsePositives() {
        String code = """
            #include <graphics.h>
            #define MAX_SPRITES 16

            void update() {
                int x = 120;
                for(int i = 0; i < MAX_SPRITES; i++) {
                    x += i;
                }
                clg();
            }
            """;
        List<StaticAnalysisService.Diagnostic> diags = StaticAnalysisService.analyzeCode(code);
        assertTrue(diags.isEmpty(), "Valid C code should produce no linter errors");
    }

    @Test
    void detectsUnexpectedTextAfterSemicolon() {
        String code = "int x = 5; unexpectedText\n";
        List<StaticAnalysisService.Diagnostic> diags = StaticAnalysisService.analyzeCode(code);
        assertEquals(1, diags.size());
        assertEquals(1, diags.get(0).line());
        assertTrue(diags.get(0).isError());
        assertTrue(diags.get(0).message().contains("punto y coma"));
    }

    @Test
    void functionalTestMultipleErrorsAndEdgeCases() {
        String code = """
            // Archivo de prueba de linter
            void main() {
                int a = 10; extraToken
                int b = 20;
                // Comentario con punto y coma; no debe fallar
                int c = 30; // comentario final
            }
            """;
        List<StaticAnalysisService.Diagnostic> diags = StaticAnalysisService.analyzeCode(code);
        assertEquals(1, diags.size(), "Should detect exactly 1 syntax error on line 3");
        assertEquals(3, diags.get(0).line());
        assertTrue(diags.get(0).isError());
    }

    @Test
    void ignoresCommentsAndPreprocessorDirectives() {
        String code = """
            #include <stdio.h>
            /* Comentario de bloque
               con puntos y comas; por aquí */
            #define MACRO(x) (x; x)
            """;
        List<StaticAnalysisService.Diagnostic> diags = StaticAnalysisService.analyzeCode(code);
        assertTrue(diags.isEmpty(), "Preprocessor directives and comments should not trigger linter errors");
    }
}
