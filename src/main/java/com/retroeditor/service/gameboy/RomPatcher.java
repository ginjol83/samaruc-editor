package com.retroeditor.service.gameboy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Aplica traducciones a un ROM de Game Boy.
 * Soporta escritura directa y generación de parches IPS.
 */
public class RomPatcher {

    private final GbRomParser rom;

    public RomPatcher(GbRomParser rom) {
        this.rom = rom;
    }

    /**
     * Escribe las traducciones directamente en el ROM.
     * Valida que la traducción no exceda la longitud original.
     *
     * @return errores encontrados (vacío si todo OK)
     */
    public List<String> validateTranslations(List<TextStringEntry> entries) {
        List<String> errors = new java.util.ArrayList<>();

        for (TextStringEntry entry : entries) {
            String translation = entry.translatedText();
            if (translation == null || translation.isEmpty()) continue;

            if (translation.length() > entry.originalBytes().length) {
                errors.add(String.format(
                    "[%d:%04X] Traducción \"%s\" (%d chars) excede longitud original (%d bytes)",
                    entry.bank(), entry.offset(), translation, translation.length(), entry.originalBytes().length
                ));
            }
        }
        return errors;
    }

    /**
     * Aplica las traducciones al ROM en memoria.
     * Devuelve una copia modificada del ROM.
     */
    public byte[] applyTranslations(List<TextStringEntry> entries) {
        byte[] romData = rom.getRomData();
        GbFontTable fontTable = new GbFontTable("tmp");

        for (TextStringEntry entry : entries) {
            String translation = entry.translatedText();
            if (translation == null || translation.isEmpty()) continue;

            // Obtener tabla del decoder del entry
            // Re-codificar usando la misma tabla
            byte[] translatedBytes = encodeTranslation(translation, entry);

            int absoluteOffset = rom.toAbsoluteOffset(entry.bank(), entry.offset());
            System.arraycopy(translatedBytes, 0, romData, absoluteOffset, translatedBytes.length);

            // Rellenar bytes sobrantes con sentinela
            for (int i = translatedBytes.length; i < entry.originalBytes().length; i++) {
                romData[absoluteOffset + i] = getTerminatorByte(entry.encoding());
            }
        }
        return romData;
    }

    /**
     * Guarda el ROM parcheado en un archivo.
     */
    public void savePatchedRom(byte[] patchedData, Path outputPath) throws IOException {
        Files.write(outputPath, patchedData);
    }

    /**
     * Genera un parche IPS básico.
     * IPS formato: "PATCH" + registros (offset(3 BE) + length(2 BE) + data) + "EOF"
     */
    public byte[] generateIpsPatch(List<TextStringEntry> entries) {
        var baos = new java.io.ByteArrayOutputStream();
        try {
            // Cabecera IPS
            baos.write("PATCH".getBytes());

            for (TextStringEntry entry : entries) {
                String translation = entry.translatedText();
                if (translation == null || translation.isEmpty()) continue;

                byte[] translatedBytes = encodeTranslation(translation, entry);
                int absoluteOffset = rom.toAbsoluteOffset(entry.bank(), entry.offset());

                // Offset (3 bytes big-endian)
                baos.write((absoluteOffset >> 16) & 0xFF);
                baos.write((absoluteOffset >> 8) & 0xFF);
                baos.write(absoluteOffset & 0xFF);

                // Longitud (2 bytes big-endian)
                baos.write((translatedBytes.length >> 8) & 0xFF);
                baos.write(translatedBytes.length & 0xFF);

                // Datos
                baos.write(translatedBytes);
            }

            // EOF
            baos.write("EOF".getBytes());
        } catch (IOException e) {
            // ByteArrayOutputStream no lanza IOException
        }
        return baos.toByteArray();
    }

    /**
     * Guarda un parche IPS en archivo.
     */
    public void saveIpsPatch(List<TextStringEntry> entries, Path outputPath) throws IOException {
        byte[] ips = generateIpsPatch(entries);
        Files.write(outputPath, ips);
    }

    private byte[] encodeTranslation(String text, TextStringEntry original) {
        // Usar la tabla del entry original para re-codificar
        GbFontTable table = new GbFontTable("temp");
        // Reconstruir tabla desde los bytes originales y el texto decodificado
        for (int i = 0; i < original.originalBytes().length && i < original.decodedText().length(); i++) {
            int tileIdx = original.originalBytes()[i] & 0xFF;
            String ch = String.valueOf(original.decodedText().charAt(i));
            table.put(tileIdx, ch);
        }

        byte[] result = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int encoded = table.encode(ch);
            result[i] = (byte) (encoded >= 0 ? encoded : 0x3F); // 0x3F = '?' genérico
        }
        return result;
    }

    private byte getTerminatorByte(TextEncoding encoding) {
        return switch (encoding) {
            case SENTINEL_0x50 -> 0x50;
            case SENTINEL_0xFF -> (byte) 0xFF;
            case SENTINEL_0x00 -> 0x00;
            case FIXED_LENGTH -> 0x00;
            case CUSTOM_TILE_TABLE -> 0x00;
            case SHIFT_JIS -> 0x00;
        };
    }
}
