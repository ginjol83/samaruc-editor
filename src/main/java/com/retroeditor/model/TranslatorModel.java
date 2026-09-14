package com.retroeditor.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.retroeditor.service.gameboy.TextEncoding;

/**
 * Modelo serializable para archivos .smt (Samaruc Machine Translation).
 * Contiene todo el estado necesario para persistir una sesión de traducción de ROMs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranslatorModel {

    public static final int CURRENT_VERSION = 1;
    public static final String FILE_EXTENSION = "smt";

    private int version = CURRENT_VERSION;
    private String romPath;
    private String fontTable = "pokemon";
    private String fontTablePath;
    private String encoding = "SENTINEL_0x50";
    private List<TranslatorEntry> entries = new ArrayList<>();
    private Map<String, String> detectedMappings = new LinkedHashMap<>();

    public TranslatorModel() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getRomPath() {
        return romPath;
    }

    public void setRomPath(String romPath) {
        this.romPath = romPath;
    }

    public String getFontTable() {
        return fontTable;
    }

    public void setFontTable(String fontTable) {
        this.fontTable = fontTable;
    }

    public String getFontTablePath() {
        return fontTablePath;
    }

    public void setFontTablePath(String fontTablePath) {
        this.fontTablePath = fontTablePath;
    }

    public TextEncoding getEncoding() {
        try {
            return TextEncoding.valueOf(encoding);
        } catch (IllegalArgumentException e) {
            return TextEncoding.SENTINEL_0x50;
        }
    }

    public void setEncoding(TextEncoding encoding) {
        this.encoding = encoding.name();
    }

    public String getEncodingName() {
        return encoding;
    }

    public void setEncodingName(String encoding) {
        this.encoding = encoding;
    }

    public List<TranslatorEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<TranslatorEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public Map<String, String> getDetectedMappings() {
        return detectedMappings;
    }

    public void setDetectedMappings(Map<String, String> detectedMappings) {
        this.detectedMappings = detectedMappings != null ? new LinkedHashMap<>(detectedMappings) : new LinkedHashMap<>();
    }

    /**
     * Añade una entrada de traducción al modelo.
     */
    public void addEntry(TranslatorEntry entry) {
        this.entries.add(entry);
    }

    /**
     * Representa una entrada individual de traducción en el archivo .smt.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TranslatorEntry {
        private int bank;
        private String offset;
        private String originalBytes;
        private String decodedText;
        private String translatedText;

        public TranslatorEntry() {
        }

        public TranslatorEntry(int bank, String offset, String originalBytes, String decodedText, String translatedText) {
            this.bank = bank;
            this.offset = offset;
            this.originalBytes = originalBytes;
            this.decodedText = decodedText;
            this.translatedText = translatedText;
        }

        public int getBank() {
            return bank;
        }

        public void setBank(int bank) {
            this.bank = bank;
        }

        public String getOffset() {
            return offset;
        }

        public void setOffset(String offset) {
            this.offset = offset;
        }

        public String getOriginalBytes() {
            return originalBytes;
        }

        public void setOriginalBytes(String originalBytes) {
            this.originalBytes = originalBytes;
        }

        public String getDecodedText() {
            return decodedText;
        }

        public void setDecodedText(String decodedText) {
            this.decodedText = decodedText;
        }

        public String getTranslatedText() {
            return translatedText;
        }

        public void setTranslatedText(String translatedText) {
            this.translatedText = translatedText;
        }
    }
}
