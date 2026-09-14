package com.retroeditor.service.gameboy;

/**
 * Representa una entrada de texto extraída de un ROM de Game Boy.
 * Almacena la ubicación en el ROM, los bytes originales y la traducción.
 */
public record TextStringEntry(
    int bank,
    int offset,
    byte[] originalBytes,
    String decodedText,
    String translatedText,
    TextEncoding encoding
) {
    public TextStringEntry {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new IllegalArgumentException("originalBytes must not be null or empty");
        }
    }

    /**
     * Crea una nueva entrada con traducción actualizada.
     */
    public TextStringEntry withTranslation(String translation) {
        return new TextStringEntry(bank, offset, originalBytes, decodedText, translation, encoding);
    }

    /**
     * Crea una entrada desde bytes raw (sin banco, offset 0).
     */
    public static TextStringEntry ofRaw(byte[] bytes, String decoded, TextEncoding encoding) {
        return new TextStringEntry(0, 0, bytes, decoded, "", encoding);
    }

    @Override
    public String toString() {
        return String.format("[%d:%04X] \"%s\" → \"%s\"", bank, offset, decodedText, translatedText);
    }
}
