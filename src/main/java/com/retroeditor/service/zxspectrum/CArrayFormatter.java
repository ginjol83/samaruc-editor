package com.retroeditor.service.zxspectrum;

/**
 * Generador de código C para arrays en formato ZX Spectrum 1bpp.
 *
 * Responsabilidades:
 * - Formatear bytes a sintaxis C válida
 * - Sanitizar nombres de símbolos (siguiendo estándar C)
 * - Generar comentarios informativos
 * - Opcionalmente incluir defines para ancho/alto
 *
 * Diseño:
 * - Sin dependencias externas
 * - Métodos seguros (validan entrada)
 * - Salida C reproducible y consistente
 */
public class CArrayFormatter {
    private static final int BYTES_PER_LINE = 16;
    private static final String INDENT = "    ";

    /**
     * Formatea un array de bytes como código C.
     *
     * @param data array de bytes a formatear
     * @param options opciones (arrayName, includeConst, includeDefines, etc.)
     * @return string con código C completo y listo para pegar
     */
    public String formatAsC(byte[] data, ZxBitmapExportOptions options) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("El array de datos no puede estar vacío");
        }

        options.validate();

        StringBuilder sb = new StringBuilder();

        // Comentario de header
        if (options.headerComment() != null && !options.headerComment().isEmpty()) {
            sb.append("// ").append(options.headerComment()).append("\n");
        }
        sb.append("// Imagen: ").append(options.width()).append("x").append(options.height());
        sb.append(" píxeles, 1 byte cada 8 píxeles horizontales\n");
        sb.append("// Total: ").append(data.length).append(" bytes (1bpp, layout ");
        sb.append(options.layout().name().toLowerCase()).append(")\n");
        sb.append("\n");

        // Defines opcionales
        if (options.includeWidthHeightDefines()) {
            String symbol = sanitizeSymbol(options.arrayName());
            sb.append("#define ").append(symbol.toUpperCase()).append("_WIDTH ").append(options.width()).append("\n");
            sb.append("#define ").append(symbol.toUpperCase()).append("_HEIGHT ").append(options.height()).append("\n");
            sb.append("\n");
        }

        // Array declaration
        String sanitizedName = sanitizeSymbol(options.arrayName());
        if (options.includeConst()) {
            sb.append("const ");
        }
        sb.append("unsigned char ").append(sanitizedName).append("[] = {\n");

        // Bytes formateados
        for (int i = 0; i < data.length; i++) {
            if (i % BYTES_PER_LINE == 0) {
                sb.append(INDENT);
            }
            sb.append(String.format("0x%02X", data[i] & 0xFF));

            if (i < data.length - 1) {
                sb.append(", ");
            }

            if ((i + 1) % BYTES_PER_LINE == 0 && i < data.length - 1) {
                int row = i / (options.width() / 8);
                sb.append("  // Row ").append(row).append("\n");
            }
        }

        sb.append("\n").append("};\n");

        return sb.toString();
    }

    /**
     * Sanitiza un nombre para que sea válido como símbolo en C.
     *
     * Reglas:
     * - Reemplaza caracteres inválidos con '_'
     * - Asegura que no empiece con dígito
     * - Si queda vacío, usa nombre por defecto
     *
     * @param symbolName nombre a sanitizar
     * @return nombre válido en C
     */
    private String sanitizeSymbol(String symbolName) {
        String raw = symbolName != null ? symbolName.trim() : "";
        if (raw.isEmpty()) {
            raw = "bitmap_data";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean isValid = (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_';
            sb.append(isValid ? c : '_');
        }

        if (sb.length() == 0) {
            return "bitmap_data";
        }

        char first = sb.charAt(0);
        if (first >= '0' && first <= '9') {
            sb.insert(0, '_');
        }

        return sb.toString();
    }
}
