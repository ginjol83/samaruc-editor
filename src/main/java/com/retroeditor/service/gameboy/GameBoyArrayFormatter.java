package com.retroeditor.service.gameboy;

/**
 * Formateador de bytes a código C para Game Boy.
 * Genera arrays de bytes en formato listo para compilar.
 */
public class GameBoyArrayFormatter {
    private static final int BYTES_PER_LINE = 16;

    /**
     * Formatea un array de bytes en código C.
     *
     * @param data array de bytes
     * @param options opciones de exportación
     * @return código C formateado
     */
    public String formatAsC(byte[] data, GameBoyBitmapExportOptions options) {
        if (data == null || data.length == 0) {
            return "// Empty bitmap";
        }

        StringBuilder sb = new StringBuilder();

        // Comentario de generación
        if (options.comment() != null && !options.comment().isEmpty()) {
            sb.append("// ").append(options.comment()).append("\n");
        }

        // #defines con dimensiones
        if (options.includeDefines()) {
            sb.append("#define ").append(sanitizeSymbol(options.arrayName())).append("_WIDTH  ").append(options.width()).append("\n");
            sb.append("#define ").append(sanitizeSymbol(options.arrayName())).append("_HEIGHT ").append(options.height()).append("\n");
            sb.append("\n");
        }

        // Array de bytes
        String constKeyword = options.includeConst() ? "const " : "";
        String arrayName = sanitizeSymbol(options.arrayName());
        sb.append(constKeyword).append("unsigned char ").append(arrayName).append("[] = {\n");

        // Escribir bytes en líneas de BYTES_PER_LINE
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            
            if (i % BYTES_PER_LINE == 0) {
                sb.append("\n    ");
            } else {
                sb.append(" ");
            }

            sb.append(String.format("0x%02X", data[i] & 0xFF));
        }

        sb.append("\n};\n");

        return sb.toString();
    }

    /**
     * Sanitiza un nombre de símbolo C.
     */
    private String sanitizeSymbol(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "bitmap_data";
        }

        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }

        // No puede empezar con número
        if (!sb.isEmpty() && sb.charAt(0) >= '0' && sb.charAt(0) <= '9') {
            sb.insert(0, '_');
        }

        return sb.toString();
    }
}
