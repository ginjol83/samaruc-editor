package com.retroeditor.service.zxspectrum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.retroeditor.model.zxscreen.ZxScreenDocument;

class ZxScreenPersistenceServiceTest {

    private final ZxScreenPersistenceService service = new ZxScreenPersistenceService();

    private ZxScreenDocument sampleDocument() {
        ZxScreenDocument document = new ZxScreenDocument();
        List<Integer> attributes = new ArrayList<>();
        for (int index = 0; index < ZxScreenEditorModel.ATTR_COLS * ZxScreenEditorModel.ATTR_ROWS; index++) {
            attributes.add(index & 0xFF);
        }
        document.setAttributes(attributes);
        document.setSelectedInk(ZxScreenAttribute.RED);
        document.setSelectedPaper(ZxScreenAttribute.BLUE);
        document.setSelectedBright(true);
        return document;
    }

    /**
     * Escribe el JSON a mano porque save() valida: para probar la validacion de load()
     * hace falta un fichero invalido que nunca pasaria por save().
     */
    private Path writeRawDocument(Path file, String format, int version, int attributeCount) throws Exception {
        StringBuilder json = new StringBuilder();
        json.append("{\"format\":\"").append(format).append('"');
        json.append(",\"version\":").append(version);
        json.append(",\"attrCols\":").append(ZxScreenEditorModel.ATTR_COLS);
        json.append(",\"attrRows\":").append(ZxScreenEditorModel.ATTR_ROWS);
        json.append(",\"selectedInk\":").append(ZxScreenAttribute.WHITE);
        json.append(",\"selectedPaper\":").append(ZxScreenAttribute.BLACK);
        json.append(",\"selectedBright\":false,\"selectedFlash\":false");
        json.append(",\"attributes\":[");
        for (int index = 0; index < attributeCount; index++) {
            if (index > 0) json.append(',');
            json.append(index & 0xFF);
        }
        json.append("]}");
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void saveAndLoadRoundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("screen.zxscreen");
        service.save(file, sampleDocument());

        ZxScreenDocument loaded = service.load(file);
        assertEquals(ZxScreenDocument.FORMAT, loaded.getFormat());
        assertEquals(ZxScreenDocument.CURRENT_VERSION, loaded.getVersion());
        assertEquals(ZxScreenEditorModel.ATTR_COLS, loaded.getAttrCols());
        assertEquals(ZxScreenEditorModel.ATTR_ROWS, loaded.getAttrRows());
        assertEquals(ZxScreenAttribute.RED, loaded.getSelectedInk());
        assertEquals(ZxScreenAttribute.BLUE, loaded.getSelectedPaper());
        assertTrue(loaded.isSelectedBright());
        assertFalse(loaded.isSelectedFlash());
        assertEquals(sampleDocument().getAttributes(), loaded.getAttributes());
    }

    @Test
    void saveCreatesMissingDirectories(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested").resolve("screen.zxscreen");
        service.save(file, sampleDocument());
        assertTrue(file.toFile().isFile());
    }

    @Test
    void loadRejectsForeignFormat(@TempDir Path dir) throws Exception {
        Path file = writeRawDocument(dir.resolve("foreign.zxscreen"), "samaruc.tilemap",
                ZxScreenDocument.CURRENT_VERSION, ZxScreenEditorModel.ATTR_COLS * ZxScreenEditorModel.ATTR_ROWS);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.load(file));
        assertTrue(error.getMessage().contains("samaruc.tilemap"));
    }

    @Test
    void loadRejectsUnsupportedVersion(@TempDir Path dir) throws Exception {
        Path file = writeRawDocument(dir.resolve("future.zxscreen"), ZxScreenDocument.FORMAT,
                ZxScreenDocument.CURRENT_VERSION + 1, ZxScreenEditorModel.ATTR_COLS * ZxScreenEditorModel.ATTR_ROWS);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.load(file));
        assertTrue(error.getMessage().contains("no soportada"));
    }

    @Test
    void loadRejectsWrongDimensions(@TempDir Path dir) throws Exception {
        Path file = writeRawDocument(dir.resolve("small.zxscreen"), ZxScreenDocument.FORMAT,
                ZxScreenDocument.CURRENT_VERSION, 3);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.load(file));
        assertTrue(error.getMessage().contains("dimensiones"));
    }

    @Test
    void saveRejectsColorOutOfPalette(@TempDir Path dir) {
        ZxScreenDocument document = sampleDocument();
        document.setSelectedInk(9);
        assertThrows(IllegalArgumentException.class, () -> service.save(dir.resolve("bad.zxscreen"), document));
    }

    @Test
    void saveRejectsNullDocument(@TempDir Path dir) {
        Path file = dir.resolve("null.zxscreen");
        assertThrows(IllegalArgumentException.class, () -> service.save(file, null));
    }

    @Test
    void zxScreenFileDetection() {
        assertTrue(ZxScreenPersistenceService.isZxScreenFile(new File("screen.zxscreen")));
        assertTrue(ZxScreenPersistenceService.isZxScreenFile(new File("SCREEN.ZXSCREEN")));
        assertFalse(ZxScreenPersistenceService.isZxScreenFile(new File("screen.tilemap")));
        assertFalse(ZxScreenPersistenceService.isZxScreenFile(null));
    }
}