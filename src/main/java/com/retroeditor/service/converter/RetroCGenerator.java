package com.retroeditor.service.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera codigo C a partir del resultado del conversor PNG a C.
 *
 * La salida incluye un comentario con la paleta real del hardware y, en el
 * caso del Master System y el Game Gear, los bytes de CRAM que hay que
 * escribir para que los indices generados muestren esos colores.
 */
public final class RetroCGenerator {
    private static final int BYTES_PER_LINE = 16;

    /**
     * Genera el codigo C de la conversion.
     *
     * @param conversion resultado del conversor
     * @param options opciones de exportacion
     * @return codigo C listo para compilar
     */
    public String generate(RetroImageConversion conversion, RetroExportOptions options) {
        if (conversion == null) {
            throw new IllegalArgumentException("La conversion no puede ser nula.");
        }
        if (options == null) {
            throw new IllegalArgumentException("Las opciones no pueden ser nulas.");
        }
        options.validate();

        StringBuilder out = new StringBuilder();
        String symbol = sanitizeSymbol(options.arrayName());
        RetroImageMode mode = conversion.mode();

        if (options.headerComment() != null && !options.headerComment().isBlank()) {
            out.append("// ").append(options.headerComment()).append('\n');
        }
        out.append("// ").append(mode.getPlatform().getDisplayName())
           .append(" - ").append(mode.getDisplayName()).append('\n');
        out.append("// Tamano: ").append(conversion.width()).append('x').append(conversion.height())
           .append(" pixeles, ").append(mode.getBitsPerPixel()).append("bpp, ")
           .append(conversion.totalBytes()).append(" bytes en total\n");

        if (options.includePaletteComment()) {
            appendPalette(out, conversion);
        }
        if (options.includeDefines()) {
            appendDefines(out, symbol, conversion);
        }

        List<String> names = RetroImagePacker.layerNames(mode, symbol);
        for (int i = 0; i < conversion.layers().size(); i++) {
            RetroImageLayer layer = conversion.layers().get(i);
            String layerName = i < names.size() ? names.get(i) : symbol + "_layer" + i;
            appendLayer(out, layer, layerName, options.includeConst());
        }
        return out.toString();
    }

    private void appendPalette(StringBuilder out, RetroImageConversion conversion) {
        RetroImageMode mode = conversion.mode();
        out.append("//\n// Paleta usada (indice -> color del hardware");
        if (mode.getPlatform() == RetroImagePlatform.C64) {
            out.append(", numero de color del VIC-II");
        }
        out.append("):\n");
        int[] palette = conversion.palette();
        for (int i = 0; i < palette.length; i++) {
            out.append(String.format(Locale.ROOT, "//  %2d: #%06X", i, palette[i] & 0xFFFFFF));
            if (mode.getPlatform() == RetroImagePlatform.C64) {
                out.append(String.format(Locale.ROOT, "  (VIC-II %d)", conversion.hardwareIndex(i)));
            }
            if (i == 0 && mode.hasTransparentFirstColor()) {
                out.append("  (transparente en sprites)");
            }
            out.append('\n');
        }
        appendCram(out, conversion);
        if (mode == RetroImageMode.C64_BITMAP) {
            int background = RetroImagePacker.c64BitmapBackgroundPosition(
                    conversion.indices(), conversion.hardwareIndices());
            out.append(String.format(Locale.ROOT,
                    "// Fondo global: escribir %d en $D021 (indice %d, #%06X)%n",
                    conversion.hardwareIndex(background), background, conversion.palette()[background] & 0xFFFFFF));
            out.append("// El valor 00 de los pixeles usa ese fondo, 01 y 10 el nibble\n");
            out.append("// alto y bajo de la matriz de pantalla de la celda, y 11 el nibble bajo de su\n");
            out.append("// color RAM\n");
        }
        if (mode == RetroImageMode.C64_CHARSET) {
            out.append("// En el color RAM el nibble alto es el primer plano y el bajo el fondo\n");
        }
        if (mode == RetroImageMode.ATARI_MODE_7) {
            out.append("// Nibble alto = PRIOR 3, nibble bajo = color del playfield\n");
        }
        out.append("//\n");
    }

    private void appendCram(StringBuilder out, RetroImageConversion conversion) {
        int[] cram = conversion.cram();
        if (cram == null) {
            return;
        }
        if (conversion.mode() == RetroImageMode.GAME_GEAR_TILES) {
            out.append("// Paleta de 12 bits para escribir como palabra en $C000 (little endian): ");
            for (int i = 0; i < cram.length; i++) {
                out.append(String.format(Locale.ROOT, "0x%02X%02X%s", (cram[i] >> 8) & 0xFF, cram[i] & 0xFF,
                        i == cram.length - 1 ? "" : " "));
            }
            out.append('\n');
            return;
        }
        out.append("// Bytes de CRAM para escribir en $C000: ");
        for (int i = 0; i < cram.length; i++) {
            out.append(String.format(Locale.ROOT, "0x%02X%s", cram[i] & 0xFF, i == cram.length - 1 ? "" : " "));
        }
        out.append('\n');
    }

    private void appendDefines(StringBuilder out, String symbol, RetroImageConversion conversion) {
        out.append("#define ").append(symbol.toUpperCase(Locale.ROOT)).append("_WIDTH  ")
           .append(conversion.width()).append('\n');
        out.append("#define ").append(symbol.toUpperCase(Locale.ROOT)).append("_HEIGHT ")
           .append(conversion.height()).append('\n');
        out.append("#define ").append(symbol.toUpperCase(Locale.ROOT)).append("_BYTES  ")
           .append(conversion.totalBytes()).append("\n\n");
    }

    private void appendLayer(StringBuilder out, RetroImageLayer layer, String name, boolean includeConst) {
        if (!layer.description().isEmpty()) {
            out.append("// ").append(name).append(": ").append(layer.description()).append('\n');
        }
        if (includeConst) {
            out.append("const ");
        }
        out.append("unsigned char ").append(name).append("[] = {\n");
        byte[] data = layer.data();
        List<String> bytes = new ArrayList<>(data.length);
        for (byte value : data) {
            bytes.add(String.format(Locale.ROOT, "0x%02X", value & 0xFF));
        }
        for (int i = 0; i < bytes.size(); i += BYTES_PER_LINE) {
            out.append("    ");
            int end = Math.min(i + BYTES_PER_LINE, bytes.size());
            for (int j = i; j < end; j++) {
                out.append(bytes.get(j));
                if (j < end - 1) {
                    out.append(", ");
                }
            }
            if (end < bytes.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("};\n\n");
    }

    /** Convierte un nombre arbitrario en un simbolo valido de C. */
    public static String sanitizeSymbol(String name) {
        String raw = name == null ? "" : name.trim();
        if (raw.isEmpty()) {
            return RetroExportOptions.DEFAULT_ARRAY_NAME;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
            sb.append(valid ? c : '_');
        }
        char first = sb.charAt(0);
        if (first >= '0' && first <= '9') {
            sb.insert(0, '_');
        }
        return sb.toString();
    }
}
