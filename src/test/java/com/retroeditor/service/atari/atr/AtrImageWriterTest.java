package com.retroeditor.service.atari.atr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AtrImageWriterTest {

    private static final int EXPECTED_SIZE = 16 + 720 * 128;

    private static byte[] content(int length, int seed) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) ((i * 31 + seed) & 0xFF);
        }
        return data;
    }

    @Test
    void writesValidAtrHeader() {
        byte[] image = new AtrImageWriter().toByteArray();
        assertEquals(EXPECTED_SIZE, image.length);
        assertEquals(0x96, image[0] & 0xFF);
        assertEquals(0x02, image[1] & 0xFF);
        assertEquals(5760, (image[2] & 0xFF) | (image[3] & 0xFF) << 8);
        assertEquals(128, (image[4] & 0xFF) | (image[5] & 0xFF) << 8);
    }

    @Test
    void freshDiskExposesDos2VtocAndFullFreeSpace() {
        AtrImageWriter writer = new AtrImageWriter();
        assertEquals(AtrImageWriter.INITIAL_FREE_SECTORS, writer.freeSectorCount());

        byte[] image = writer.toByteArray();
        int vtoc = 16 + (AtrImageWriter.VTOC_SECTOR - 1) * 128;
        assertEquals(0x02, image[vtoc] & 0xFF);
        assertEquals(707, (image[vtoc + 1] & 0xFF) | (image[vtoc + 2] & 0xFF) << 8);
        assertEquals(707, (image[vtoc + 3] & 0xFF) | (image[vtoc + 4] & 0xFF) << 8);
        // sectores 0..3 reservados; sectores 4..7 libres -> 0x0F
        assertEquals(0x0F, image[vtoc + 10] & 0xFF);
    }

    @Test
    void emptyDiskHasNoFiles() {
        AtrImageReader reader = AtrImageReader.parse(new AtrImageWriter().toByteArray());
        assertTrue(reader.listFiles().isEmpty());
    }

    @Test
    void roundTripsSingleSectorFile() {
        byte[] original = content(100, 1);
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("AUTORUN.BAS", original);

        AtrImageReader reader = AtrImageReader.parse(writer.toByteArray());
        AtrFileEntry entry = reader.findFile("AUTORUN.BAS");
        assertNotNull(entry);
        assertEquals(1, entry.sectorCount());
        assertEquals(4, entry.startSector());
        assertArrayEquals(original, reader.readFile(entry));
    }

    @Test
    void roundTripsMultiSectorFile() {
        byte[] original = content(125 * 3 + 40, 7);
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("PROG.BAS", original);

        AtrImageReader reader = AtrImageReader.parse(writer.toByteArray());
        AtrFileEntry entry = reader.findFile("PROG.BAS");
        assertNotNull(entry);
        assertEquals(4, entry.sectorCount());
        assertArrayEquals(original, reader.readFile(entry));
    }

    @Test
    void contentExactlyOneSectorNeedsOneSector() {
        byte[] original = content(125, 3);
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("EXACT.BAS", original);

        AtrImageReader reader = AtrImageReader.parse(writer.toByteArray());
        AtrFileEntry entry = reader.findFile("EXACT.BAS");
        assertEquals(1, entry.sectorCount());
        assertArrayEquals(original, reader.readFile(entry));
    }

    @Test
    void supportsMultipleFiles() {
        byte[] first = content(200, 11);
        byte[] second = content(60, 22);
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("FIRST.BAS", first);
        writer.addFile("SECOND.BAS", second);

        AtrImageReader reader = AtrImageReader.parse(writer.toByteArray());
        List<AtrFileEntry> entries = reader.listFiles();
        assertEquals(2, entries.size());
        assertArrayEquals(first, reader.readFile(reader.findFile("FIRST.BAS")));
        assertArrayEquals(second, reader.readFile(reader.findFile("SECOND.BAS")));
    }

    @Test
    void normalizesNamesToUppercaseEightThree() {
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("autorun.bas", content(10, 5));
        AtrImageReader reader = AtrImageReader.parse(writer.toByteArray());
        AtrFileEntry entry = reader.listFiles().get(0);
        assertEquals("AUTORUN", entry.name());
        assertEquals("BAS", entry.extension());
        assertEquals("AUTORUN.BAS", entry.fullName());
    }

    @Test
    void directoryEntryUsesDos2InUseFlag() {
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("FLAG.BAS", content(10, 5));
        byte[] image = writer.toByteArray();
        int directory = 16 + (AtrImageWriter.DIR_FIRST_SECTOR - 1) * 128;
        assertEquals(0x42, image[directory] & 0xFF);
    }

    @Test
    void freeSectorCountDropsByAllocatedSectors() {
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("A.BAS", content(125 * 2, 1));
        assertEquals(AtrImageWriter.INITIAL_FREE_SECTORS - 2, writer.freeSectorCount());
    }

    @Test
    void firstFreeSectorIsFour() {
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("A.BAS", content(10, 1));
        AtrImageReader reader = AtrImageReader.parse(writer.toByteArray());
        assertEquals(4, reader.listFiles().get(0).startSector());
    }

    @Test
    void allocatedSectorIsMarkedUsedInBitmap() {
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile("A.BAS", content(10, 1));
        byte[] image = writer.toByteArray();
        int vtoc = 16 + (AtrImageWriter.VTOC_SECTOR - 1) * 128;
        int sector = 4;
        int bit = (image[vtoc + 10 + (sector >> 3)] >> (7 - (sector & 7))) & 1;
        assertEquals(0, bit, "el sector 4 debe estar ocupado");
        int free = 5;
        int freeBit = (image[vtoc + 10 + (free >> 3)] >> (7 - (free & 7))) & 1;
        assertEquals(1, freeBit, "el sector 5 debe estar libre");
    }

    @Test
    void rejectsImageWithoutAtrMagic() {
        byte[] broken = new AtrImageWriter().toByteArray();
        broken[0] = 0x00;
        assertThrows(IllegalArgumentException.class, () -> AtrImageReader.parse(broken));
    }

    @Test
    void rejectsTruncatedImage() {
        byte[] image = new AtrImageWriter().toByteArray();
        byte[] truncated = new byte[image.length - 1000];
        System.arraycopy(image, 0, truncated, 0, truncated.length);
        assertThrows(IllegalArgumentException.class, () -> AtrImageReader.parse(truncated));
    }

    @Test
    void missingFileReturnsNull() {
        AtrImageReader reader = AtrImageReader.parse(new AtrImageWriter().toByteArray());
        assertNull(reader.findFile("NOPE.BAS"));
    }
}
