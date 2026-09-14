package com.retroeditor.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.retroeditor.model.BuiltInThemes;
import com.retroeditor.service.ThemeCssGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FXUtilsEditorCssTest {

    /**
     * JavaFX decodifica las URLs {@code data:} no base64 tratando {@code +}
     * como un más literal (sólo {@code %XX} se deshace). Si el CSS se codifica
     * con {@code URLEncoder} puro, los espacios quedan como {@code +} y el
     * parser rechaza la hoja (no se aplica el tema oscuro).
     */
    @Test
    void dataCssUrlRoundTripsWithoutLiteralPlus() {
        String css = ThemeCssGenerator.generateCss(BuiltInThemes.modernDark());
        assertTrue(css.contains(".code-area .lineno"), "la muestra debe contener selectores con espacio");

        String url = FXUtils.dataCssUrl(css);
        assertNotNull(url);
        assertTrue(url.startsWith("data:text/css;charset=utf-8,"), "prefijo de media type");

        String data = url.substring(url.indexOf(',') + 1);
        assertFalse(data.contains("+"), "los espacios deben ser %20, no '+': " + data);

        String decoded = URLDecoder.decode(data.replace("+", "%2B"), StandardCharsets.UTF_8);
        assertEquals(css, decoded, "la decodificación tipo DataURI debe reconstruir el CSS exacto");
    }

    @Test
    void dataCssUrlEncodesSpaceAsPercent20() {
        String url = FXUtils.dataCssUrl(".a .b { -fx-fill: #fff; }");
        assertNotNull(url);
        assertTrue(url.contains(".a%20.b"), "el espacio del selector debe quedar como %20");
    }

    @Test
    void dataCssUrlRejectsBlankAndNull() {
        assertNull(FXUtils.dataCssUrl(null));
        assertNull(FXUtils.dataCssUrl("   "));
    }
}