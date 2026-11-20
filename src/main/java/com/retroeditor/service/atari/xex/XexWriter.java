package com.retroeditor.service.atari.xex;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Constructor de ficheros XEX (Atari 8 bits). Cada segmento se serializa como
 * {@code FF FF} + direccion inicial + direccion final + datos, con las
 * direcciones en little-endian. El bloque de arranque es el segmento especial
 * {@code RUNAD} en {@code $02E0}.
 */
public final class XexWriter {

    public static final int RUNAD_ADDRESS = 0x02E0;
    private static final int MAX_ADDRESS = 0xFFFF;
    private static final int SEGMENT_MARKER = 0xFF;

    private final List<Segment> segments = new ArrayList<>();
    private Integer runAddress;

    public void addSegment(int startAddress, byte[] data) throws XexException {
        if (data == null || data.length == 0) {
            throw new XexException("El segmento XEX no puede estar vacio");
        }
        if (startAddress < 0 || startAddress > MAX_ADDRESS) {
            throw new XexException("Direccion inicial XEX fuera de rango: " + startAddress);
        }
        int endAddress = startAddress + data.length - 1;
        if (endAddress > MAX_ADDRESS) {
            throw new XexException("El segmento XEX se sale del espacio de direcciones");
        }
        ensureNoOverlap(startAddress, endAddress);
        segments.add(new Segment(startAddress, endAddress, data));
    }

    public void setRunAddress(int address) throws XexException {
        if (address < 0 || address > MAX_ADDRESS) {
            throw new XexException("Direccion de arranque XEX fuera de rango: " + address);
        }
        runAddress = address;
    }

    public List<Segment> getSegments() {
        return List.copyOf(segments);
    }

    public Integer getRunAddress() {
        return runAddress;
    }

    public byte[] toByteArray() throws XexException {
        if (runAddress != null) {
            ensureNoOverlap(RUNAD_ADDRESS, RUNAD_ADDRESS + 1);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Segment segment : segments) {
            writeSegment(out, segment.startAddress(), segment.endAddress(), segment.data());
        }
        if (runAddress != null) {
            writeSegment(out, RUNAD_ADDRESS, RUNAD_ADDRESS + 1,
                    new byte[] {(byte) (runAddress & 0xFF), (byte) ((runAddress >> 8) & 0xFF)});
        }
        return out.toByteArray();
    }

    private static void writeSegment(ByteArrayOutputStream out, int startAddress, int endAddress, byte[] data) {
        out.write(SEGMENT_MARKER);
        out.write(SEGMENT_MARKER);
        writeWord(out, startAddress);
        writeWord(out, endAddress);
        out.write(data, 0, data.length);
    }

    private static void writeWord(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private void ensureNoOverlap(int startAddress, int endAddress) throws XexException {
        for (Segment existing : segments) {
            if (startAddress <= existing.endAddress() && existing.startAddress() <= endAddress) {
                throw new XexException(String.format(
                        "Segmento XEX solapado: $%04X-$%04X con $%04X-$%04X",
                        startAddress, endAddress, existing.startAddress(), existing.endAddress()));
            }
        }
    }

    public record Segment(int startAddress, int endAddress, byte[] data) {

        public Segment {
            if (data == null) {
                throw new IllegalArgumentException("Los datos del segmento son obligatorios");
            }
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }
}
