package com.retroeditor.controller.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construye los textos de muestra que se muestran en la vista previa de la
 * pestaña Apariencia. Para cada lenguaje se devuelve un fragmento que ejercita
 * la mayoría de los tokens editables (clave de la extensión sin punto).
 */
public final class ThemePreviewBuilder {

    private ThemePreviewBuilder() {
    }

    /**
     * Textos de ejemplo por extensión de lenguaje ({@code c}, {@code asm},
     * {@code md}, {@code json}).
     */
    public static Map<String, String> buildSampleTexts() {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("c", cSample());
        samples.put("asm", asmSample());
        samples.put("md", markdownSample());
        samples.put("json", jsonSample());
        return samples;
    }

    public static String sampleFor(String extension) {
        Map<String, String> samples = buildSampleTexts();
        String sample = samples.get(extension);
        return sample != null ? sample : cSample();
    }

    private static String cSample() {
        return "#include <stdio.h>\n"
            + "\n"
            + "// Comentario de linea\n"
            + "#define MAX 10   /* comentario multilinea */\n"
            + "\n"
            + "int main(void) {\n"
            + "    char letra = 'a';\n"
            + "    int valores[MAX] = {0};\n"
            + "    printf(\"letra: %c\\n\", letra);\n"
            + "    if (letra >= 'a' && letra <= 'z') {\n"
            + "        clrscr();\n"
            + "    }\n"
            + "    set_bkg_tiles(0, 0, 20, 18, (unsigned char*)valores);\n"
            + "    wait_vbl_done();\n"
            + "    return 0;\n"
            + "}\n";
    }

    private static String asmSample() {
        return "; Ejemplo de ASM (z80 / Game Boy)\n"
            + ".include \"hardware.inc\"\n"
            + "\n"
            + "SECTION \"Main\", ROM0\n"
            + "start:\n"
            + "    ld a, 1\n"
            + "    ld hl, $C000\n"
            + "loop:\n"
            + "    ld [hl], a\n"
            + "    inc hl\n"
            + "    jr loop\n"
            + "    ret\n";
    }

    private static String markdownSample() {
        return "# Titulo de ejemplo\n"
            + "\n"
            + "> Cita de bloque\n"
            + "\n"
            + "- Elemento de lista\n"
            + "- [ ] tarea pendiente\n"
            + "- [x] tarea hecha\n"
            + "\n"
            + "| Col A | Col B |\n"
            + "| ----- | ----- |\n"
            + "| v1    | v2    |\n"
            + "\n"
            + "[enlace](https://example.com) **negrita** _cursiva_\n"
            + "\n"
            + "`codigo inline` y bloque:\n"
            + "\n"
            + "```c\n"
            + "int x = 1;\n"
            + "```\n";
    }

    private static String jsonSample() {
        return "{\n"
            + "  \"clave\": \"valor\",\n"
            + "  \"entero\": 42,\n"
            + "  \"decimal\": -3.14,\n"
            + "  \"flag\": true,\n"
            + "  \"nulo\": null\n"
            + "}\n";
    }
}