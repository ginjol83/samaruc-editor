package com.retroeditor.service.gameboy;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tabla de fuentes de Game Boy: mapea índices de tile (0x00-0xFF) a caracteres.
 * Soporta tablas .tbl (formato usado por la comunidad de ROM hacking).
 * Incluye tablas predefinidas para juegos populares.
 */
public class GbFontTable {

    private final Map<Integer, String> tileToChar;
    private final Map<String, Integer> charToTile;
    private String name;

    public GbFontTable(String name) {
        this.name = name;
        this.tileToChar = new LinkedHashMap<>();
        this.charToTile = new LinkedHashMap<>();
    }

    /**
     * Carga una tabla .tbl desde un archivo.
     * Formato: cada línea es "tile_index=caracter" (hex).
     * Ejemplo: 00=A, 01=B, 02=C, etc.
     */
    public static GbFontTable loadTblFile(Path path) throws IOException {
        GbFontTable table = new GbFontTable(path.getFileName().toString());
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith(";")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;

                int index;
                try {
                    String hex = parts[0].trim();
                    if (hex.toLowerCase().startsWith("0x")) {
                        index = Integer.parseInt(hex.substring(2), 16);
                    } else {
                        index = Integer.parseInt(hex, 16);
                    }
                } catch (NumberFormatException e) {
                    continue;
                }

                String character = parts[1].trim();
                table.put(index, character);
            }
        }
        return table;
    }

    /**
     * Crea una tabla estándar ASCII para Game Boy (tiles 0x20-0x7E = ASCII).
     */
    public static GbFontTable createAsciiTable() {
        GbFontTable table = new GbFontTable("ASCII estándar");
        for (int i = 0x20; i <= 0x7E; i++) {
            table.put(i, String.valueOf((char) i));
        }
        table.put(0x50, "."); // Sentinela Pokémon
        return table;
    }

    /**
     * Tabla predefinida para Pokémon Red/Blue/Yellow (una de las más comunes).
     */
    public static GbFontTable createPokemonTable() {
        GbFontTable table = new GbFontTable("Pokémon Red/Blue/Yellow");
        // Letras mayúsculas
        table.put(0x00, "A"); table.put(0x01, "B"); table.put(0x02, "C");
        table.put(0x03, "D"); table.put(0x04, "E"); table.put(0x05, "F");
        table.put(0x06, "G"); table.put(0x07, "H"); table.put(0x08, "I");
        table.put(0x09, "J"); table.put(0x0A, "K"); table.put(0x0B, "L");
        table.put(0x0C, "M"); table.put(0x0D, "N"); table.put(0x0E, "O");
        table.put(0x0F, "P"); table.put(0x10, "Q"); table.put(0x11, "R");
        table.put(0x12, "S"); table.put(0x13, "T"); table.put(0x14, "U");
        table.put(0x15, "V"); table.put(0x16, "W"); table.put(0x17, "X");
        table.put(0x18, "Y"); table.put(0x19, "Z");
        // Letras minúsculas
        table.put(0x1A, "a"); table.put(0x1B, "b"); table.put(0x1C, "c");
        table.put(0x1D, "d"); table.put(0x1E, "e"); table.put(0x1F, "f");
        table.put(0x20, "g"); table.put(0x21, "h"); table.put(0x22, "i");
        table.put(0x23, "j"); table.put(0x24, "k"); table.put(0x25, "l");
        table.put(0x26, "m"); table.put(0x27, "n"); table.put(0x28, "o");
        table.put(0x29, "p"); table.put(0x2A, "q"); table.put(0x2B, "r");
        table.put(0x2C, "s"); table.put(0x2D, "t"); table.put(0x2E, "u");
        table.put(0x2F, "v"); table.put(0x30, "w"); table.put(0x31, "x");
        table.put(0x32, "y"); table.put(0x33, "z");
        // Números
        table.put(0x34, "0"); table.put(0x35, "1"); table.put(0x36, "2");
        table.put(0x37, "3"); table.put(0x38, "4"); table.put(0x39, "5");
        table.put(0x3A, "6"); table.put(0x3B, "7"); table.put(0x3C, "8");
        table.put(0x3D, "9");
        // Signos
        table.put(0x3E, "!"); table.put(0x3F, "?");
        table.put(0x40, "."); table.put(0x41, ","); table.put(0x42, ":");
        table.put(0x43, "/"); table.put(0x44, "'");
        table.put(0x45, "-"); table.put(0x46, "\"");
        table.put(0x47, "("); table.put(0x48, ")");
        table.put(0x49, "="); table.put(0x4A, ";");
        // Sentinela
        table.put(0x50, "@"); // Usado como sentinela de fin de cadena en Pokémon
        // Caracteres especiales
        table.put(0x4B, "♂"); table.put(0x4C, "♀");
        table.put(0x4D, "$"); table.put(0x4E, "×");
        table.put(0x7F, " ");
        return table;
    }

    /**
     * Tabla genérica para Dragon Quest / Warrior.
     */
    public static GbFontTable createDragonQuestTable() {
        GbFontTable table = new GbFontTable("Dragon Quest");
        table.put(0x00, " ");  // Espacio
        table.put(0x01, "A"); table.put(0x02, "B"); table.put(0x03, "C");
        table.put(0x04, "D"); table.put(0x05, "E"); table.put(0x06, "F");
        table.put(0x07, "G"); table.put(0x08, "H"); table.put(0x09, "I");
        table.put(0x0A, "J"); table.put(0x0B, "K"); table.put(0x0C, "L");
        table.put(0x0D, "M"); table.put(0x0E, "N"); table.put(0x0F, "O");
        table.put(0x10, "P"); table.put(0x11, "Q"); table.put(0x12, "R");
        table.put(0x13, "S"); table.put(0x14, "T"); table.put(0x15, "U");
        table.put(0x16, "V"); table.put(0x17, "W"); table.put(0x18, "X");
        table.put(0x19, "Y"); table.put(0x1A, "Z");
        table.put(0x1B, "a"); table.put(0x1C, "b"); table.put(0x1D, "c");
        table.put(0x1E, "d"); table.put(0x1F, "e"); table.put(0x20, "f");
        table.put(0x21, "g"); table.put(0x22, "h"); table.put(0x23, "i");
        table.put(0x24, "j"); table.put(0x25, "k"); table.put(0x26, "l");
        table.put(0x27, "m"); table.put(0x28, "n"); table.put(0x29, "o");
        table.put(0x2A, "p"); table.put(0x2B, "q"); table.put(0x2C, "r");
        table.put(0x2D, "s"); table.put(0x2E, "t"); table.put(0x2F, "u");
        table.put(0x30, "v"); table.put(0x31, "w"); table.put(0x32, "x");
        table.put(0x33, "y"); table.put(0x34, "z");
        table.put(0x35, "0"); table.put(0x36, "1"); table.put(0x37, "2");
        table.put(0x38, "3"); table.put(0x39, "4"); table.put(0x3A, "5");
        table.put(0x3B, "6"); table.put(0x3C, "7"); table.put(0x3D, "8");
        table.put(0x3E, "9");
        table.put(0x3F, "!"); table.put(0x40, "?"); table.put(0x41, ".");
        table.put(0x42, ","); table.put(0x43, ":"); table.put(0x44, "'");
        table.put(0x45, "-"); table.put(0x46, "…");
        table.put(0x50, "\n"); // Fin de línea en DQ
        return table;
    }

    /**
     * Añade una asignación tile → carácter.
     */
    public void put(int tileIndex, String character) {
        tileToChar.put(tileIndex, character);
        charToTile.put(character, tileIndex);
    }

    /**
     * Decodifica un byte a carácter usando la tabla.
     * Devuelve null si no hay mapeo.
     */
    public String decode(int tileIndex) {
        return tileToChar.get(tileIndex);
    }

    /**
     * Codifica un carácter a índice de tile.
     * Devuelve -1 si no hay mapeo.
     */
    public int encode(String character) {
        Integer index = charToTile.get(character);
        return index != null ? index : -1;
    }

    /**
     * Decodifica un array de bytes a texto completo.
     * Los bytes sin mapeo se muestran como [XX].
     */
    public String decodeBytes(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            int idx = b & 0xFF;
            String ch = decode(idx);
            if (ch != null) {
                sb.append(ch);
            } else {
                sb.append(String.format("[%02X]", idx));
            }
        }
        return sb.toString();
    }

    /**
     * Devuelve todos los mapeos tile → carácter.
     */
    public Map<Integer, String> getMappings() {
        return new LinkedHashMap<>(tileToChar);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int size() {
        return tileToChar.size();
    }

    /**
     * Guarda la tabla de fuentes en formato .tbl.
     * Formato: cada línea es "hex_index=caracter".
     *
     * @param path Ruta del archivo .tbl a crear
     */
    public void saveTblFile(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("// Tabla de fuentes generada por Samaruc Editor\n");
        sb.append("// ").append(name).append("\n");
        sb.append("// Mapeos: ").append(size()).append("\n\n");

        for (Map.Entry<Integer, String> entry : tileToChar.entrySet()) {
            int index = entry.getKey();
            String character = entry.getValue();
            sb.append(String.format("%02X=%s\n", index, character));
        }

        Files.writeString(path, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Genera el contenido .tbl como string (sin escribir a disco).
     */
    public String toTblString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> entry : tileToChar.entrySet()) {
            sb.append(String.format("%02X=%s\n", entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "GbFontTable[" + name + ", " + size() + " mapeos]";
    }
}
