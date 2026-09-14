package com.retroeditor.service.gameboy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pruebas unitarias de {@link GbFontTable}.
 */
class GbFontTableTest {

    @TempDir
    Path tempDir;

    @Test
    void pokemonTableDecodesKnownTiles() {
        GbFontTable table = GbFontTable.createPokemonTable();
        Assertions.assertEquals("A", table.decode(0x00));
        Assertions.assertEquals("Z", table.decode(0x19));
        Assertions.assertEquals("0", table.decode(0x34));
        Assertions.assertEquals("!", table.decode(0x3E));
    }

    @Test
    void pokemonTableReturnsNullForUnknownTile() {
        GbFontTable table = GbFontTable.createPokemonTable();
        Assertions.assertNull(table.decode(0xFE));
    }

    @Test
    void encodeAndDecodeAreSymmetric() {
        GbFontTable table = GbFontTable.createPokemonTable();
        int index = table.encode("A");
        Assertions.assertEquals(0, index);
        Assertions.assertEquals("A", table.decode(index));
    }

    @Test
    void decodeBytesDecodesEntireArray() {
        GbFontTable table = GbFontTable.createPokemonTable();
        byte[] data = {0x00, 0x01, 0x02}; // A, B, C
        String result = table.decodeBytes(data);
        Assertions.assertEquals("ABC", result);
    }

    @Test
    void decodeBytesShowsHexForUnknownTiles() {
        GbFontTable table = GbFontTable.createPokemonTable();
        byte[] data = {0x00, (byte) 0xFE, 0x02}; // A, [FE], C
        String result = table.decodeBytes(data);
        Assertions.assertEquals("A[FE]C", result);
    }

    @Test
    void dragonQuestTableHasSpaceAtZero() {
        GbFontTable table = GbFontTable.createDragonQuestTable();
        Assertions.assertEquals(" ", table.decode(0x00));
    }

    @Test
    void asciiTableMapsStandardCharacters() {
        GbFontTable table = GbFontTable.createAsciiTable();
        Assertions.assertEquals("A", table.decode(0x41));
        Assertions.assertEquals("a", table.decode(0x61));
        Assertions.assertEquals("0", table.decode(0x30));
    }

    @Test
    void sizeReturnsCorrectCount() {
        GbFontTable table = GbFontTable.createPokemonTable();
        Assertions.assertTrue(table.size() > 50);
    }

    @Test
    void getMappingsReturnsCopy() {
        GbFontTable table = GbFontTable.createPokemonTable();
        int originalSize = table.size();
        var mappings = table.getMappings();
        mappings.clear(); // Modificar la copia
        // La tabla original no debe cambiar
        Assertions.assertEquals(originalSize, table.size());
        Assertions.assertEquals("A", table.decode(0x00));
    }

    @Test
    void toTblStringContainsValidFormat() {
        GbFontTable table = new GbFontTable("test");
        table.put(0x00, "A");
        table.put(0x01, "B");
        table.put(0x50, "@");

        String tbl = table.toTblString();
        Assertions.assertTrue(tbl.contains("00=A"));
        Assertions.assertTrue(tbl.contains("01=B"));
        Assertions.assertTrue(tbl.contains("50=@"));
    }

    @Test
    void toTblStringNewlineSeparated() {
        GbFontTable table = new GbFontTable("test");
        table.put(0x00, "X");
        table.put(0x01, "Y");

        String tbl = table.toTblString();
        String[] lines = tbl.split("\n");
        Assertions.assertEquals(2, lines.length);
    }

    @Test
    void saveTblFileCreatesValidFile() throws Exception {
        GbFontTable table = new GbFontTable("export-test");
        table.put(0x00, "A");
        table.put(0x01, "B");
        table.put(0x02, "C");

        Path tblPath = tempDir.resolve("test.tbl");
        table.saveTblFile(tblPath);

        Assertions.assertTrue(Files.exists(tblPath));
        String content = Files.readString(tblPath);
        Assertions.assertTrue(content.contains("00=A"));
        Assertions.assertTrue(content.contains("01=B"));
        Assertions.assertTrue(content.contains("02=C"));
        Assertions.assertTrue(content.contains("export-test"));
    }

    @Test
    void saveTblFileThenLoadTblFileRoundtrip() throws Exception {
        GbFontTable original = GbFontTable.createPokemonTable();
        Path tblPath = tempDir.resolve("roundtrip.tbl");
        original.saveTblFile(tblPath);

        GbFontTable loaded = GbFontTable.loadTblFile(tblPath);
        Assertions.assertEquals(original.size(), loaded.size());
        Assertions.assertEquals("A", loaded.decode(0x00));
        Assertions.assertEquals("Z", loaded.decode(0x19));
        Assertions.assertEquals("0", loaded.decode(0x34));
    }

    @Test
    void saveTblFileWithSpecialCharacters() throws Exception {
        GbFontTable table = new GbFontTable("special");
        table.put(0x4B, "♂");
        table.put(0x4C, "♀");

        Path tblPath = tempDir.resolve("special.tbl");
        table.saveTblFile(tblPath);

        GbFontTable loaded = GbFontTable.loadTblFile(tblPath);
        Assertions.assertEquals("♂", loaded.decode(0x4B));
        Assertions.assertEquals("♀", loaded.decode(0x4C));
    }
}
