package com.retroeditor.service.atari.xex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class XexWriterTest {

    @Test
    void writesSegmentsAndRunAddress() throws Exception {
        XexWriter writer = new XexWriter();
        writer.addSegment(0x0600, new byte[] {0x01, 0x02});
        writer.setRunAddress(0x0600);

        assertArrayEquals(new byte[] {
                (byte) 0xFF, (byte) 0xFF, 0x00, 0x06, 0x01, 0x06, 0x01, 0x02,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xE0, 0x02, (byte) 0xE1, 0x02, 0x00, 0x06
        }, writer.toByteArray());
    }

    @Test
    void writesEmptyFileWhenNoSegments() throws Exception {
        assertArrayEquals(new byte[0], new XexWriter().toByteArray());
    }

    @Test
    void rejectsOverlappingSegments() throws Exception {
        XexWriter writer = new XexWriter();
        writer.addSegment(0x4000, new byte[16]);
        assertThrows(XexException.class, () -> writer.addSegment(0x400F, new byte[2]));
    }

    @Test
    void rejectsSegmentPastAddressSpace() throws Exception {
        XexWriter writer = new XexWriter();
        assertThrows(XexException.class, () -> writer.addSegment(0xFFFF, new byte[2]));
    }

    @Test
    void rejectsEmptyOrNullSegment() {
        XexWriter writer = new XexWriter();
        assertThrows(XexException.class, () -> writer.addSegment(0x4000, new byte[0]));
        assertThrows(XexException.class, () -> writer.addSegment(0x4000, null));
    }

    @Test
    void rejectsRunAddressOverlappingSegment() throws Exception {
        XexWriter writer = new XexWriter();
        writer.addSegment(0x02E0, new byte[2]);
        writer.setRunAddress(0x0600);
        assertThrows(XexException.class, writer::toByteArray);
    }

    @Test
    void rejectsInvalidRunAddress() {
        XexWriter writer = new XexWriter();
        assertThrows(XexException.class, () -> writer.setRunAddress(-1));
        assertThrows(XexException.class, () -> writer.setRunAddress(0x10000));
    }

    @Test
    void exposesAddedSegments() throws Exception {
        XexWriter writer = new XexWriter();
        writer.addSegment(0x0600, new byte[] {0x01});
        assertEquals(1, writer.getSegments().size());
        assertEquals(0x0600, writer.getSegments().get(0).startAddress());
        assertEquals(0x0600, writer.getSegments().get(0).endAddress());
    }
}
