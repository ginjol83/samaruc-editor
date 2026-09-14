package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.retroeditor.model.BuiltInThemes;
import com.retroeditor.model.EditorTheme;

/**
 * Pruebas unitarias de {@link ThemeCssGenerator}.
 */
class ThemeCssGeneratorTest {

    @Test
    void nullThemeProducesDarkCssWithDefaults() {
        String css = ThemeCssGenerator.generateCss(null);

        assertNotNull(css);
        assertTrue(css.contains(".code-area"));
        assertTrue(css.contains("-fx-background-color: #0f172a"));
        assertTrue(css.contains("-fx-font-size: 13.0px"));
        assertTrue(css.contains("-fx-font-size: 12.0px"));
        assertTrue(css.contains(".code-area .keyword"));
        assertTrue(css.contains("-fx-font-weight: bold"));
        assertFalse(css.contains("rgba(0, 0, 0, 0)"));
        assertFalse(css.contains("null"));
    }

    @Test
    void customThemeColorsAreInterpolated() {
        EditorTheme theme = BuiltInThemes.modernDark().copy();
        theme.setColor(EditorTheme.KEY_SYNTAX_KEYWORD, "#ff0000");
        theme.setColor(EditorTheme.KEY_EDITOR_BACKGROUND, "#100030");

        String css = ThemeCssGenerator.generateCss(theme);

        assertTrue(css.contains(".code-area .keyword"));
        assertTrue(css.contains("-fx-fill: #ff0000"));
        assertTrue(css.contains("-fx-background-color: #100030"));
    }

    @Test
    void explicitLineNumberSizeIsRespected() {
        EditorTheme theme = BuiltInThemes.modernDark().copy();
        theme.setLineNumberSize(15);

        String css = ThemeCssGenerator.generateCss(theme);

        assertTrue(css.contains("-fx-font-size: 15.0px"),
            "deberia emitir el tamano de numeracion explicito");
    }

    @Test
    void autoLineNumberSizeFallsBackToFontSizeMinusOne() {
        EditorTheme theme = BuiltInThemes.modernDark().copy();
        theme.setLineNumberSize(0);

        String css = ThemeCssGenerator.generateCss(theme);

        assertTrue(css.contains("-fx-font-size: 12.0px"),
            "sin tamano explicito, la numeracion debe ser fontSize - 1");
    }

    @Test
    void missingColorsFallBackToBaseThemeNeverBlank() {
        EditorTheme theme = new EditorTheme();
        theme.setName("Vacio");

        String css = ThemeCssGenerator.generateCss(theme);

        for (String selector : new String[]{".code-area"}) {
            assertTrue(css.contains(selector));
        }
        assertFalse(css.contains(": ;"));
        assertFalse(css.contains("-fx-fill: ;"));
        assertFalse(css.contains("-fx-background-color: ;"));
    }

    @Test
    void fontFamilyQuotesAreEscaped() {
        EditorTheme theme = BuiltInThemes.modernDark().copy();
        theme.setFontFamily("Mi \"Fuente\"");

        String css = ThemeCssGenerator.generateCss(theme);

        assertTrue(css.contains("font-family: \"Mi \\\"Fuente\\\"\""));
    }

    @Test
    void lightThemeUsesLightBasePalette() {
        EditorTheme theme = BuiltInThemes.classic().copy();
        String css = ThemeCssGenerator.generateCss(theme);

        assertTrue(css.contains("-fx-background-color: #f8fafc"));
        assertTrue(css.contains(".code-area .keyword"));
        assertTrue(css.contains("-fx-fill: #7c3aed"));
    }

    @Test
    void contentHashIsStableAndDistinguishesContent() {
        String cssA = ThemeCssGenerator.generateCss(BuiltInThemes.modernDark());
        String cssB = ThemeCssGenerator.generateCss(BuiltInThemes.classic());

        assertEquals(ThemeCssGenerator.contentHash(cssA), ThemeCssGenerator.contentHash(cssA));
        assertFalse(ThemeCssGenerator.contentHash(cssA).equals(ThemeCssGenerator.contentHash(cssB)));
        assertEquals("empty", ThemeCssGenerator.contentHash("  "));
        assertEquals("empty", ThemeCssGenerator.contentHash(null));
    }
}