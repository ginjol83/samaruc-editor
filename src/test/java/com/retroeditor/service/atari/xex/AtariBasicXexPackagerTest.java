package com.retroeditor.service.atari.xex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.retroeditor.service.atari.basic.AtariBasicTokenizer;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtariBasicXexPackagerTest {

    private static final String PROGRAM = "10 PRINT \"HOLA MUNDO\"\n20 END\n";

    private final AtariBasicXexPackager packager = new AtariBasicXexPackager();

    @Test
    void createsLoaderPayloadAndRunAddressSegments() throws Exception {
        byte[] tokenized = AtariBasicTokenizer.tokenize(PROGRAM);

        byte[] xex = packager.pack(tokenized);
        List<Segment> segments = parseSegments(xex);

        assertEquals(3, segments.size());
        Segment loader = segments.get(0);
        Segment payload = segments.get(1);
        Segment run = segments.get(2);

        assertEquals(AtariBasicXexPackager.LOADER_ADDRESS, loader.start());
        assertTrue(loader.data().length <= 0x100, "El cargador debe caber en una pagina");
        assertEquals(AtariBasicXexPackager.PAYLOAD_ADDRESS, payload.start());
        assertArrayEquals(Arrays.copyOfRange(tokenized, AtariBasicXexPackager.BASIC_HEADER_SIZE, tokenized.length),
                payload.data());
        assertEquals(XexWriter.RUNAD_ADDRESS, run.start());
        assertArrayEquals(new byte[] {
                (byte) (AtariBasicXexPackager.LOADER_ADDRESS & 0xFF),
                (byte) ((AtariBasicXexPackager.LOADER_ADDRESS >> 8) & 0xFF)
        }, run.data());
    }

    @Test
    void loaderCallsBasicInterpreterDirectly() throws Exception {
        byte[] xex = packager.pack(AtariBasicTokenizer.tokenize(PROGRAM));
        byte[] loader = parseSegments(xex).get(0).data();

        assertTrue(contains(loader, new byte[] {
                (byte) 0xAD, (byte) 0xE7, 0x02, (byte) 0x85, (byte) 0x80}));
        assertTrue(contains(loader, new byte[] {(byte) 0xA2, (byte) 0xFF, (byte) 0x9A,
                0x20, 0x4D, (byte) 0xB7}));
        assertTrue(contains(loader, new byte[] {0x4C, 0x5E, (byte) 0xA9}));
    }

    @Test
    void loaderForcesTstendToReportEndOfStatement() throws Exception {
        byte[] xex = packager.pack(AtariBasicTokenizer.tokenize(PROGRAM));
        byte[] loader = parseSegments(xex).get(0).data();

        assertTrue(contains(loader, new byte[] {(byte) 0xA9, (byte) 0xFF, (byte) 0x85, (byte) 0xA8}));
        assertTrue(contains(loader, new byte[] {(byte) 0xA9, 0x00, (byte) 0x85, (byte) 0xA7}));
    }

    @Test
    void loaderAddsLomemToPointerTable() throws Exception {
        byte[] xex = packager.pack(AtariBasicTokenizer.tokenize(PROGRAM));
        byte[] loader = parseSegments(xex).get(0).data();

        assertTrue(contains(loader, new byte[] {(byte) 0xA5, (byte) 0x80, 0x18, 0x7D}));
        assertTrue(contains(loader, new byte[] {(byte) 0xA5, (byte) 0x81, 0x7D}));
    }

    @Test
    void loaderEndsWithBasicPointerTable() throws Exception {
        byte[] tokenized = AtariBasicTokenizer.tokenize(PROGRAM);
        byte[] loader = parseSegments(packager.pack(tokenized)).get(0).data();

        int tableOffset = loader.length - 16;
        int starp = readWord(tokenized, 12);
        for (int i = 0; i < 8; i++) {
            int expected = i < 6 ? readWord(tokenized, (i + 1) * 2) : starp;
            assertEquals(expected & 0xFF, loader[tableOffset + i * 2] & 0xFF);
            assertEquals((expected >> 8) & 0xFF, loader[tableOffset + i * 2 + 1] & 0xFF);
        }
    }

    @Test
    void rejectsTruncatedProgram() {
        assertThrows(XexException.class, () -> packager.pack(new byte[AtariBasicXexPackager.BASIC_HEADER_SIZE]));
        assertThrows(XexException.class, () -> packager.pack(null));
    }

    @Test
    void rejectsNonZeroLomem() throws Exception {
        byte[] tokenized = AtariBasicTokenizer.tokenize(PROGRAM);
        tokenized[0] = 1;

        assertThrows(XexException.class, () -> packager.pack(tokenized));
    }

    @Test
    void rejectsIncoherentPointerTable() throws Exception {
        byte[] tokenized = AtariBasicTokenizer.tokenize(PROGRAM);
        int starp = readWord(tokenized, 12);
        writeWord(tokenized, 10, starp + 1);

        assertThrows(XexException.class, () -> packager.pack(tokenized));
    }

    @Test
    void rejectsProgramLargerThanAddressablePayload() {
        byte[] tooLarge = buildTokenized(AtariBasicXexPackager.MAX_PAYLOAD_SIZE + 1);

        assertThrows(XexException.class, () -> packager.pack(tooLarge));
    }

    private static byte[] buildTokenized(int payloadLength) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        putWord(out, 0);
        putWord(out, 0x100);
        putWord(out, 0x0FF);
        putWord(out, 0x100);
        putWord(out, 0x100);
        putWord(out, 0x100 + payloadLength - 6);
        putWord(out, 0x100 + payloadLength);
        out.write(new byte[payloadLength], 0, payloadLength);
        return out.toByteArray();
    }

    private static void putWord(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static List<Segment> parseSegments(byte[] xex) {
        List<Segment> segments = new ArrayList<>();
        int offset = 0;
        while (offset < xex.length) {
            assertEquals(0xFF, xex[offset] & 0xFF);
            assertEquals(0xFF, xex[offset + 1] & 0xFF);
            int start = readWord(xex, offset + 2);
            int end = readWord(xex, offset + 4);
            int length = end - start + 1;
            byte[] data = Arrays.copyOfRange(xex, offset + 6, offset + 6 + length);
            segments.add(new Segment(start, data));
            offset += 6 + length;
        }
        return segments;
    }

    private record Segment(int start, byte[] data) {
    }
}
