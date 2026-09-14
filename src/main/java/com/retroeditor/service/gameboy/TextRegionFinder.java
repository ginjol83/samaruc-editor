package com.retroeditor.service.gameboy;

import java.util.ArrayList;
import java.util.List;

/**
 * Escanea un ROM de Game Boy buscando regiones que contienen texto.
 * Usa heurísticas: secuencias de bytes decodificables, terminadores conocidos, etc.
 */
public class TextRegionFinder {

    private static final int MIN_STRING_LENGTH = 3;
    private static final int MAX_STRING_LENGTH = 256;
    private static final int SCAN_STEP = 1;

    private final GbRomParser rom;
    private final GbTextDecoder decoder;

    public TextRegionFinder(GbRomParser rom, GbTextDecoder decoder) {
        this.rom = rom;
        this.decoder = decoder;
    }

    /**
     * Escanea un bank completo buscando strings de texto.
     */
    public List<TextStringEntry> scanBank(int bankNumber) {
        List<TextStringEntry> results = new ArrayList<>();
        int bankSize = rom.getBankSize();

        for (int offset = 0; offset < bankSize - MIN_STRING_LENGTH; offset += SCAN_STEP) {
            int absoluteOffset = rom.toAbsoluteOffset(bankNumber, offset);
            int firstByte = rom.readByte(absoluteOffset);

            if (!isPotentialStringStart(firstByte)) continue;

            // Intentar decodificar una cadena desde esta posición
            List<TextStringEntry> decoded = decoder.decodeRegion(rom, bankNumber, offset, MAX_STRING_LENGTH);
            for (TextStringEntry entry : decoded) {
                if (entry.decodedText().length() >= MIN_STRING_LENGTH) {
                    // Evitar duplicados por offset
                    boolean duplicate = results.stream()
                        .anyMatch(e -> e.bank() == entry.bank() && e.offset() == entry.offset());
                    if (!duplicate) {
                        results.add(entry);
                    }
                }
            }
        }
        return results;
    }

    /**
     * Escanea todos los banks del ROM.
     */
    public List<TextStringEntry> scanAllBanks() {
        List<TextStringEntry> allResults = new ArrayList<>();
        for (int bank = 0; bank < rom.getBankCount(); bank++) {
            allResults.addAll(scanBank(bank));
        }
        return allResults;
    }

    /**
     * Escaneo rápido: solo busca secuencias con terminadores conocidos.
     */
    public List<TextStringEntry> quickScan() {
        List<TextStringEntry> results = new ArrayList<>();

        for (int bank = 0; bank < rom.getBankCount(); bank++) {
            int bankSize = rom.getBankSize();
            for (int offset = 0; offset < bankSize - MIN_STRING_LENGTH; offset++) {
                int absoluteOffset = rom.toAbsoluteOffset(bank, offset);

                // Buscar terminadores de 0x50 (estilo Pokémon)
                for (int scanLen = MIN_STRING_LENGTH; scanLen <= MAX_STRING_LENGTH; scanLen++) {
                    int endAbs = absoluteOffset + scanLen;
                    if (endAbs >= rom.getRomSize()) break;

                    int endByte = rom.readByte(endAbs);
                    if (endByte == 0x50) {
                        byte[] data = rom.readBytes(absoluteOffset, scanLen);
                        String decoded = decoder.decode(data);
                        if (isReadableText(decoded)) {
                            results.add(new TextStringEntry(bank, offset, data, decoded, "", 
                                TextEncoding.SENTINEL_0x50));
                            offset += scanLen;
                            break;
                        }
                    }
                }
            }
        }
        return results;
    }

    private boolean isPotentialStringStart(int b) {
        if (b < 0) return false;
        // Bytes que nunca son inicio de string
        if (b == 0x00 || b == 0xFF || b == 0x50) return false;
        // Verificar si el decoder puede interpretarlo
        String decoded = decoder.getFontTable().decode(b);
        return decoded != null;
    }

    private boolean isReadableText(String text) {
        if (text == null || text.length() < MIN_STRING_LENGTH) return false;
        int readable = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x20 && c < 0x7F) readable++;
        }
        return (double) readable / text.length() > 0.7;
    }

    /**
     * Devuelve un resumen de los banks escaneados.
     */
    public String getScanSummary(List<TextStringEntry> results) {
        int totalBanks = rom.getBankCount();
        long banksWithText = results.stream().mapToInt(TextStringEntry::bank).distinct().count();
        return String.format(
            "ROM: %s | %d banks | %d strings encontrados en %d/%d banks",
            rom.getTitle(), totalBanks, results.size(), banksWithText, totalBanks
        );
    }
}
