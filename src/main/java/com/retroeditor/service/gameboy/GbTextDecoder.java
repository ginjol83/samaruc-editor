package com.retroeditor.service.gameboy;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodifica texto de un ROM de Game Boy usando una tabla de fuentes.
 * Soporta múltiples modos de terminación de cadena.
 */
public class GbTextDecoder {

    private final GbFontTable fontTable;
    private final TextEncoding encoding;
    private final int fixedLength;

    public GbTextDecoder(GbFontTable fontTable, TextEncoding encoding) {
        this(fontTable, encoding, 0);
    }

    public GbTextDecoder(GbFontTable fontTable, TextEncoding encoding, int fixedLength) {
        this.fontTable = fontTable;
        this.encoding = encoding;
        this.fixedLength = fixedLength;
    }

    /**
     * Decodifica bytes del ROM en una cadena de texto usando la tabla de fuentes.
     */
    public String decode(byte[] data) {
        if (data == null || data.length == 0) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;

            // Verificar terminación
            if (isTerminator(b, i)) break;

            String ch = fontTable.decode(b);
            if (ch != null) {
                sb.append(ch);
            } else {
                sb.append(String.format("[%02X]", b));
            }
        }
        return sb.toString();
    }

    /**
     * Decodifica y devuelve la lista de entries con sus offsets.
     */
    public List<TextStringEntry> decodeRegion(GbRomParser rom, int bank, int startOffset, int maxBytes) {
        List<TextStringEntry> entries = new ArrayList<>();
        int absoluteStart = rom.toAbsoluteOffset(bank, startOffset);
        int absoluteEnd = Math.min(absoluteStart + maxBytes, rom.getRomSize());

        int currentOffset = startOffset;
        while (rom.toAbsoluteOffset(bank, currentOffset) < absoluteEnd) {
            // Buscar inicio de cadena: primer byte decodificable
            int absPos = rom.toAbsoluteOffset(bank, currentOffset);
            int firstByte = rom.readByte(absPos);

            if (!isValidStringStart(firstByte)) {
                currentOffset++;
                continue;
            }

            // Leer hasta terminador
            int chainStart = currentOffset;
            List<Byte> chainBytes = new ArrayList<>();
            while (rom.toAbsoluteOffset(bank, currentOffset) < absoluteEnd) {
                absPos = rom.toAbsoluteOffset(bank, currentOffset);
                int b = rom.readByte(absPos);
                if (b < 0) break;

                if (isTerminator(b, 0)) {
                    currentOffset++;
                    break;
                }

                chainBytes.add((byte) b);
                currentOffset++;

                // Longitud máxima razonable para un string de texto
                if (chainBytes.size() >= 256) break;
            }

            if (chainBytes.size() >= 2) {
                byte[] bytes = new byte[chainBytes.size()];
                for (int j = 0; j < chainBytes.size(); j++) {
                    bytes[j] = chainBytes.get(j);
                }
                String decoded = decode(bytes);
                entries.add(new TextStringEntry(bank, chainStart, bytes, decoded, "", encoding));
            }
        }
        return entries;
    }

    /**
     * Verifica si un byte es un terminador válido según el encoding.
     */
    private boolean isTerminator(int b, int positionInString) {
        return switch (encoding) {
            case SENTINEL_0x50 -> b == 0x50;
            case SENTINEL_0xFF -> b == 0xFF;
            case SENTINEL_0x00 -> b == 0x00;
            case FIXED_LENGTH -> positionInString >= fixedLength;
            case CUSTOM_TILE_TABLE, SHIFT_JIS -> false;
        };
    }

    /**
     * Verifica si un byte podría ser el inicio de una cadena de texto.
     */
    private boolean isValidStringStart(int b) {
        if (b < 0) return false;
        if (encoding == TextEncoding.SENTINEL_0x00 && b == 0x00) return false;
        if (encoding == TextEncoding.SENTINEL_0xFF && b == 0xFF) return false;

        // Para tablas de tiles, verificar que el byte tiene un mapeo
        String ch = fontTable.decode(b);
        if (ch != null) return true;

        // Para sentinelas, puede ser un carácter válido
        if (encoding == TextEncoding.SENTINEL_0x50 && b == 0x50) return false;

        // Para tablas genéricas, bytes imprimibles son candidatos
        return b >= 0x20 && b < 0x7F;
    }

    public GbFontTable getFontTable() {
        return fontTable;
    }

    public TextEncoding getEncoding() {
        return encoding;
    }
}
