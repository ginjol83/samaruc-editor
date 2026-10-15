package com.retroeditor.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del armado de prompts con contexto. Es codigo puro: sin red, sin JavaFX y sin el agente.
 */
class AiPromptBuilderTest {

    private static AiPromptBuilder builder(int maxLogLines, int maxContextKb) {
        return new AiPromptBuilder(new AiSettings(null, 0, AiSettings.Mode.SERVE, null,
                maxContextKb, 5, maxLogLines));
    }

    private static AiPromptBuilder.Context context(String file, String selection, String log) {
        return new AiPromptBuilder.Context(file, selection, log, "z88dk-spectrum", "SPECTRUM");
    }

    @Test
    void explainErrorIncludesToolchainAndLog() {
        String prompt = builder(40, 64).explainCompilationError(
                context("main.c", null, "main.c:12: error: undefined identifier 'print'"), null);

        assertTrue(prompt.contains("z88dk-spectrum"));
        assertTrue(prompt.contains("SPECTRUM"));
        assertTrue(prompt.contains("main.c:12: error"));
        assertTrue(prompt.contains("main.c"));
    }

    @Test
    void explainErrorWithoutLogStillAsksTheQuestion() {
        String prompt = builder(40, 64).explainCompilationError(AiPromptBuilder.Context.empty(), null);

        assertTrue(prompt.toLowerCase().contains("error de compilacion"));
        assertFalse(prompt.contains("Salida de compilacion"),
                "sin consola no debe inventar un bloque de log vacio");
    }

    @Test
    void keepsOnlyTheTailOfALongLog() {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            log.append("linea ").append(i).append('\n');
        }

        String prompt = builder(5, 64).explainCompilationError(context("main.c", null, log.toString()), null);

        assertTrue(prompt.contains("linea 100"));
        assertTrue(prompt.contains("linea 96"));
        assertFalse(prompt.contains("linea 95"), "el log se recorta por el final");
    }

    @Test
    void clampsOversizedSelectionWithAWarning() {
        String selection = "x".repeat(2048);

        String prompt = builder(40, 1).explainSelection(context("main.c", selection, null), null);

        assertTrue(prompt.contains("contexto recortado"));
        assertTrue(prompt.length() < selection.length() + 400);
    }

    @Test
    void keepsShortSelectionIntact() {
        String prompt = builder(40, 64).explainSelection(context("main.c", "void main() {}", null), null);

        assertTrue(prompt.contains("void main() {}"));
        assertFalse(prompt.contains("contexto recortado"));
    }

    @Test
    void appendsTheUserQuestionWhenPresent() {
        String prompt = builder(40, 64).explainSelection(
                context("main.c", "void main() {}", null), "¿y si quiero IRQ?");

        assertTrue(prompt.contains("¿y si quiero IRQ?"));
    }

    @Test
    void chatWithoutContextIsJustTheQuestion() {
        String prompt = builder(40, 64).chat(AiPromptBuilder.Context.empty(), "explica el proyecto");

        assertTrue(prompt.contains("Peticion: explica el proyecto"));
        assertFalse(prompt.contains("plataforma:"));
    }

    @Test
    void blankContextFieldsAreDropped() {
        AiPromptBuilder.Context context = new AiPromptBuilder.Context("  ", "", "   ", null, "");

        assertFalse(context.hasSelection());
        assertFalse(context.hasBuildLog());
        assertEquals(null, context.fileName());
    }

    @Test
    void tailHandlesBlankAndZero() {
        assertEquals("", AiPromptBuilder.tail(null, 10));
        assertEquals("", AiPromptBuilder.tail("  ", 10));
        assertEquals("", AiPromptBuilder.tail("a\nb", 0));
    }

    @Test
    void tailHandlesFewerLinesThanTheLimit() {
        assertEquals("a\nb", AiPromptBuilder.tail("a\nb", 10));
    }

    @Test
    void tailNormalizesWindowsLineEndings() {
        assertEquals("a\nb", AiPromptBuilder.tail("a\r\nb", 10));
    }

    @Test
    void clampsEmptyText() {
        assertEquals("", AiPromptBuilder.clampKb(null, 10));
        assertEquals("", AiPromptBuilder.clampKb("", 10));
    }

    }