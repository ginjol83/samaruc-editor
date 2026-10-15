package com.retroeditor.service.zxspectrum;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Genera el array C con el fichero de atributos de una pantalla del ZX Spectrum.
 *
 * El hardware solo lee 32x24 bytes, uno por cada caracter de 8x8 pixeles, en el bloque
 * 0x5800. El array se exporta en ese orden (fila a fila, de izquierda a derecha) para
 * poder volcarlo tal cual con LDIR/VDP o con un blit a memoria.
 */
public class ZxScreenCodeGenerator {

    /** Direccion del fichero de atributos en la RAM del ZX Spectrum. */
    public static final int ATTR_ADDRESS = 0x5800;

    private static final int BYTES_PER_ROW = ZxScreenEditorModel.ATTR_COLS;
    private static final String INDENT = "    ";
    private static final String DEFAULT_ARRAY_NAME = "screen_attrs";

    private static final String[] COLOR_NAMES = {
        "BLACK", "BLUE", "RED", "MAGENTA", "GREEN", "CYAN", "YELLOW", "WHITE"
    };

    /**
     * Genera el codigo C a partir del modelo del editor.
     *
     * @param arrayName nombre del simbolo C; si es nulo o vacio se usa {@code screen_attrs}
     */
    public String generate(String arrayName, ZxScreenEditorModel model) {
        if (model == null) {
            throw new IllegalArgumentException("El modelo de la pantalla no puede ser null.");
        }
        return generate(arrayName, model.getAttributeBytes());
    }

    /**
     * Genera el codigo C a partir de los bytes de atributo en bruto.
     *
     * @param attributes exactamente {@code ATTR_COLS * ATTR_ROWS} bytes
     */
    public String generate(String arrayName, byte[] attributes) {
        int expected = Math.multiplyExact(ZxScreenEditorModel.ATTR_COLS, ZxScreenEditorModel.ATTR_ROWS);
        if (attributes == null || attributes.length != expected) {
            throw new IllegalArgumentException(
                    "El fichero de atributos debe tener " + expected + " bytes (32x24).");
        }

        String symbol = sanitizeSymbol(arrayName);
        StringBuilder sb = new StringBuilder();

        sb.append("// Pantalla del ZX Spectrum: ")
                .append(ZxScreenEditorModel.ATTR_COLS).append('x').append(ZxScreenEditorModel.ATTR_ROWS)
                .append(" atributos, 1 byte por caracter de 8x8 pixeles\n");
        sb.append("// Total: ").append(expected).append(" bytes, listos para 0x5800 (")
                .append(ATTR_ADDRESS).append(")\n");
        sb.append("// Tinta = bits 2-0, fondo = bits 5-3, brillo = bit 6, parpadeo = bit 7\n");
        sb.append('\n');

        sb.append("#define ").append(symbol.toUpperCase()).append("_COLS ").append(ZxScreenEditorModel.ATTR_COLS).append('\n');
        sb.append("#define ").append(symbol.toUpperCase()).append("_ROWS ").append(ZxScreenEditorModel.ATTR_ROWS).append('\n');
        sb.append("#define ").append(symbol.toUpperCase()).append("_COUNT ").append(expected).append('\n');
        sb.append("#define ").append(symbol.toUpperCase()).append("_ADDRESS 0x5800\n");
        sb.append('\n');

        sb.append("const unsigned char ").append(symbol).append("[").append(symbol.toUpperCase()).append("_COUNT] = {\n");
        for (int row = 0; row < ZxScreenEditorModel.ATTR_ROWS; row++) {
            sb.append(INDENT).append("// Fila ").append(row).append('\n');
            sb.append(INDENT);
            for (int col = 0; col < BYTES_PER_ROW; col++) {
                int index = row * BYTES_PER_ROW + col;
                if (col > 0) {
                    sb.append(' ');
                }
                sb.append(String.format("0x%02X", attributes[index] & 0xFF));
                sb.append(col < BYTES_PER_ROW - 1 ? ',' : (row < ZxScreenEditorModel.ATTR_ROWS - 1 ? ',' : ' '));
            }
            sb.append('\n');
        }
        sb.append("};\n");

        appendLegend(sb, attributes);

        return sb.toString();
    }

    /**
     * Lista los atributos distintos que aparecen en la pantalla. Sirve para definir los
     * pares INK/PAPER que despues usa el codigo del juego, en lugar de tener que ir
     * descifrando los bytes a mano.
     */
    private void appendLegend(StringBuilder sb, byte[] attributes) {
        Map<Integer, Integer> used = new LinkedHashMap<>();
        for (byte attribute : attributes) {
            used.merge(attribute & 0xFF, 1, Integer::sum);
        }
        if (used.isEmpty()) {
            return;
        }
        sb.append('\n');
        sb.append("// Atributos usados (0xNN = tinta/fondo,Flags):\n");
        for (Map.Entry<Integer, Integer> entry : used.entrySet()) {
            ZxScreenAttribute attribute = ZxScreenAttribute.fromByte((byte) (int) entry.getKey());
            int count = entry.getValue();
            sb.append("//   0x").append(String.format("%02X", entry.getKey()))
                    .append("  INK=").append(pad(COLOR_NAMES[attribute.ink()]))
                    .append("  PAPER=").append(pad(COLOR_NAMES[attribute.paper()]));
            if (attribute.bright()) {
                sb.append("  BRIGHT");
            }
            if (attribute.flash()) {
                sb.append("  FLASH");
            }
            sb.append("  (").append(count).append(count == 1 ? " caracter)" : " caracteres)").append('\n');
        }
    }

    private String pad(String colorName) {
        StringBuilder sb = new StringBuilder(colorName);
        while (sb.length() < 7) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private String sanitizeSymbol(String symbolName) {
        String raw = symbolName != null ? symbolName.trim() : "";
        if (raw.isEmpty()) {
            return DEFAULT_ARRAY_NAME;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            sb.append(valid ? c : '_');
        }
        if (sb.length() == 0) {
            return DEFAULT_ARRAY_NAME;
        }
        if (Character.isDigit(sb.charAt(0))) {
            sb.insert(0, '_');
        }
        return sb.toString();
    }
}