package com.retroeditor.service.gameboy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera tablas .tbl automáticamente analizando un ROM de Game Boy.
 * Usa heurísticas para detectar qué bytes corresponden a qué caracteres:
 * - Secuencias ascendentes de bytes (letras, dígitos)
 * - Patrones de terminadores conocidos
 * - Strings comunes en juegos retro
 * - Análisis de frecuencia de bytes
 */
public class TblGeneratorService {

    private static final int MIN_SEQUENCE_LENGTH = 3;
    private static final int MAX_SCAN_RANGE = 0x8000; // 32KB por bank

    // Strings comunes en juegos GB que ayudan a inferir mapeos
    private static final String[][] COMMON_PATTERNS = {
        // { bytes_hex_sequencia, caracteres Esperado }
        // Letras mayúsculas secuenciales (A=0, B=1, C=2...)
        {"00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10 11 12 13 14 15 16 17 18 19",
         "ABCDEFGHIJKLMNOPQRSTUVWXYZ"},
        // Dígitos
        {"00 01 02 03 04 05 06 07 08 09", "0123456789"},
        // Letras minúsculas
        {"1A 1B 1C 1D 1E 1F 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F 30 31 32 33",
         "abcdefghijklmnopqrstuvwxyz"},
        // Pokémon: sentinela 0x50 después de texto
        {"48 45 4C 4C 4F 50", "HELLO@"},
        // Dígitos alternativos
        {"34 35 36 37 38 39 3A 3B 3C 3D", "0123456789"},
    };

    private final GbRomParser rom;

    public TblGeneratorService(GbRomParser rom) {
        this.rom = rom;
    }

    /**
     * Analiza el ROM completo y genera una tabla de fuentes candidata.
     *
     * @return GbFontTable con los mapeos detectados (puede estar parcial)
     */
    public GbFontTable detectTable() {
        GbFontTable table = new GbFontTable("Auto-detectada: " + rom.getTitle());

        // Paso 1: Buscar secuencias ascendentes de bytes
        detectAscendingSequences(table);

        // Paso 2: Buscar patrones de strings comunes
        detectCommonPatterns(table);

        // Paso 3: Análisis de frecuencia
        detectByFrequency(table);

        return table;
    }

    /**
     * Detecta secuencias ascendentes de bytes en el ROM.
     * Muchos juegos usan font tables donde A=0, B=1, C=2, etc.
     */
    private void detectAscendingSequences(GbFontTable table) {
        List<int[]> letterRanges = new ArrayList<>();

        // Primera pasada: localizar secuencias de 26 bytes ascendentes (A-Z)
        for (int bank = 0; bank < rom.getBankCount(); bank++) {
            int bankSize = Math.min(rom.getBankSize(), MAX_SCAN_RANGE);
            for (int offset = 0; offset < bankSize - 26; offset++) {
                int abs = rom.toAbsoluteOffset(bank, offset);
                if (isAscendingSequence(abs, 26)) {
                    int startByte = rom.readByte(abs);
                    if (startByte >= 0x00 && startByte <= 0x40) {
                        letterRanges.add(new int[]{abs, startByte});
                        for (int i = 0; i < 26; i++) {
                            int tileIdx = (startByte + i) & 0xFF;
                            char letter = (char) ('A' + i);
                            if (table.decode(tileIdx) == null) {
                                table.put(tileIdx, String.valueOf(letter));
                            }
                        }
                    }
                }
            }
        }

        // Segunda pasada: secuencias de 10 bytes ascendentes (0-9)
        // evitando solaparse con rangos de letras ya detectados
        for (int bank = 0; bank < rom.getBankCount(); bank++) {
            int bankSize = Math.min(rom.getBankSize(), MAX_SCAN_RANGE);
            for (int offset = 0; offset < bankSize - 10; offset++) {
                int abs = rom.toAbsoluteOffset(bank, offset);
                if (!isAscendingSequence(abs, 10)) continue;

                int startByte = rom.readByte(abs);
                if (startByte < 0x00 || startByte > 0x40) continue;

                boolean overlapsLetters = letterRanges.stream().anyMatch(range -> {
                    int rangeStart = range[0];
                    int rangeByte = range[1];
                    // Rango de bytes que ocupan las letras
                    int lettersByteStart = rangeByte;
                    int lettersByteEnd = rangeByte + 26;
                    return startByte < lettersByteEnd && startByte + 10 > lettersByteStart
                        && abs >= rangeStart && abs < rangeStart + 26;
                });
                if (overlapsLetters) continue;

                for (int i = 0; i < 10; i++) {
                    int tileIdx = (startByte + i) & 0xFF;
                    char digit = (char) ('0' + i);
                    if (table.decode(tileIdx) == null) {
                        table.put(tileIdx, String.valueOf(digit));
                    }
                }
            }
        }
    }

    private boolean isAscendingSequence(int absoluteStart, int length) {
        int first = rom.readByte(absoluteStart);
        if (first < 0) return false;

        for (int i = 1; i < length; i++) {
            int current = rom.readByte(absoluteStart + i);
            if (current < 0) return false;
            if (current != first + i) return false;
        }
        return true;
    }

    /**
     * Busca patrones de strings comunes en juegos retro.
     */
    private void detectCommonPatterns(GbFontTable table) {
        for (String[] pattern : COMMON_PATTERNS) {
            String hexSequence = pattern[0];
            String characters = pattern[1];

            byte[] searchBytes = parseHexString(hexSequence);
            if (searchBytes == null) continue;

            List<int[]> found = findBytePattern(searchBytes);
            for (int[] location : found) {
                int bank = location[0];
                int offset = location[1];
                int abs = rom.toAbsoluteOffset(bank, offset);

                for (int i = 0; i < characters.length() && i < searchBytes.length; i++) {
                    int tileIdx = rom.readByte(abs + i);
                    if (tileIdx >= 0) {
                        String ch = String.valueOf(characters.charAt(i));
                        if (table.decode(tileIdx) == null) {
                            table.put(tileIdx, ch);
                        }
                    }
                }
            }
        }
    }

    /**
     * Busca un patrón de bytes en todos los banks del ROM.
     */
    private List<int[]> findBytePattern(byte[] pattern) {
        List<int[]> results = new ArrayList<>();
        if (pattern == null || pattern.length == 0) return results;

        for (int bank = 0; bank < rom.getBankCount(); bank++) {
            int bankSize = Math.min(rom.getBankSize(), MAX_SCAN_RANGE);
            for (int offset = 0; offset <= bankSize - pattern.length; offset++) {
                int abs = rom.toAbsoluteOffset(bank, offset);
                if (matchAt(abs, pattern)) {
                    results.add(new int[]{bank, offset});
                    // No saltar demasiado para encontrar múltiples ocurrencias
                    offset += pattern.length - 1;
                }
            }
        }
        return results;
    }

    private boolean matchAt(int absoluteOffset, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            int b = rom.readByte(absoluteOffset + i);
            if (b < 0 || (b & 0xFF) != (pattern[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Analiza frecuencia de bytes para detectar caracteres comunes.
     */
    private void detectByFrequency(GbFontTable table) {
        Map<Integer, Integer> freq = new LinkedHashMap<>();

        for (int bank = 0; bank < rom.getBankCount(); bank++) {
            int bankSize = Math.min(rom.getBankSize(), MAX_SCAN_RANGE);
            for (int offset = 0; offset < bankSize; offset++) {
                int abs = rom.toAbsoluteOffset(bank, offset);
                int b = rom.readByte(abs);
                if (b >= 0x20 && b < 0x60) {
                    freq.merge(b, 1, Integer::sum);
                }
            }
        }

        // Bytes muy frecuentes en rango 0x20-0x5F son probablemente texto
        // Asignar caracteres ASCII si no tienen mapeo aún
        freq.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(50)
            .forEach(entry -> {
                int tileIdx = entry.getKey();
                if (table.decode(tileIdx) == null && entry.getValue() > 5) {
                    // Si el byte está en rango ASCII imprimible, mapearlo directamente
                    if (tileIdx >= 0x20 && tileIdx < 0x7F) {
                        table.put(tileIdx, String.valueOf((char) tileIdx));
                    }
                }
            });
    }

    /**
     * Detecta terminadores de cadena (bytes que terminan strings).
     */
    public Map<Integer, String> detectTerminators() {
        Map<Integer, String> terminators = new LinkedHashMap<>();

        // Contar frecuencia de bytes que aparecen justo después de secuencias de texto
        Map<Integer, Integer> postText = new LinkedHashMap<>();

        for (int bank = 0; bank < rom.getBankCount(); bank++) {
            int bankSize = Math.min(rom.getBankSize(), MAX_SCAN_RANGE);
            for (int offset = 0; offset < bankSize - 1; offset++) {
                int abs = rom.toAbsoluteOffset(bank, offset);
                int b = rom.readByte(abs);
                int next = rom.readByte(abs + 1);

                // Si el byte actual es "imprimible" y el siguiente no lo es,
                // el siguiente es candidato a terminador
                if (b >= 0x20 && b < 0x7F && (next < 0x20 || next >= 0x7F || next == 0x50 || next == 0xFF || next == 0x00)) {
                    postText.merge(next, 1, Integer::sum);
                }
            }
        }

        // Los más frecuentes post-texto son terminadores
        postText.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(5)
            .forEach(entry -> {
                if (entry.getValue() > 3) {
                    String name = switch (entry.getKey()) {
                        case 0x50 -> "Sentinel 0x50";
                        case 0xFF -> "Sentinel 0xFF";
                        case 0x00 -> "Null terminator";
                        default -> String.format("Unknown (%02X)", entry.getKey());
                    };
                    terminators.put(entry.getKey(), name);
                }
            });

        return terminators;
    }

    /**
     * Convierte un string hex "00 01 02" a byte[].
     */
    private byte[] parseHexString(String hex) {
        try {
            String[] parts = hex.trim().split("\\s+");
            byte[] result = new byte[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = (byte) Integer.parseInt(parts[i], 16);
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Genera un informe de lo que se detectó.
     */
    public String generateReport(GbFontTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Informe de auto-detección .tbl ===\n");
        sb.append("ROM: ").append(rom.getTitle()).append("\n");
        sb.append("Banks: ").append(rom.getBankCount()).append("\n");
        sb.append("Tipo cartucho: ").append(rom.getCartridgeTypeDescription()).append("\n\n");

        sb.append("Mapeos detectados: ").append(table.size()).append("\n\n");

        if (table.size() == 0) {
            sb.append("⚠ No se detectaron mapeos automáticamente.\n");
            sb.append("Posibles causas:\n");
            sb.append("- El ROM usa una font table no estándar\n");
            sb.append("- Los bytes de texto están comprimidos\n");
            sb.append("- El formato de encoding no es compatible\n");
        } else {
            sb.append("Primeros 30 mapeos:\n");
            int count = 0;
            for (var entry : table.getMappings().entrySet()) {
                sb.append(String.format("  %02X → %s\n", entry.getKey(), entry.getValue()));
                if (++count >= 30) break;
            }
        }

        Map<Integer, String> terminators = detectTerminators();
        if (!terminators.isEmpty()) {
            sb.append("\nTerminadores detectados:\n");
            terminators.forEach((k, v) ->
                sb.append(String.format("  %02X → %s\n", k, v)));
        }

        return sb.toString();
    }
}
