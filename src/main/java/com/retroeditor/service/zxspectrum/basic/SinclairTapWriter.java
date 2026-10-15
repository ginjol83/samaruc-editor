package com.retroeditor.service.zxspectrum.basic;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Empaqueta un programa BASIC ya tokenizado en un fichero de cinta {@code .tap}
 * del ZX Spectrum.
 *
 * <p>Un {@code .tap} es una secuencia de bloques, cada uno precedido de su
 * longitud en 2 bytes little-endian. El contenido de cada bloque empieza con un
 * byte de bandera ({@code 0x00} cabecera, {@code 0xFF} datos) al que sigue la
 * carga util y un byte de checksum (XOR de la bandera y de todos los bytes).
 * Una cabecera de programa tiene 17 bytes de carga util: tipo ({@code 0}=programa),
 * nombre de 10 caracteres, longitud del programa (2), linea de arranque (2) y de
 * nuevo la longitud (2). La linea de arranque {@code 32768} ({@code 0x8000})
 * indica que no hay autoarranque.</p>
 */
public final class SinclairTapWriter {

    /** Valor de la linea de arranque que significa "sin autoarranque". */
    public static final int NO_AUTOSTART = 0x8000;

    private static final int HEADER_FLAG = 0x00;
    private static final int DATA_FLAG = 0xFF;
    private static final int PROGRAM_TYPE = 0x00;

    private SinclairTapWriter() {
    }

    /**
     * Serializa {@code program} (programa nativo, sin cabecera) como un {@code .tap}
     * con el nombre de fichero indicado.
     *
     * @param autostartLine linea a ejecutar automaticamente, o {@link #NO_AUTOSTART}
     *                      para no autoarrancar.
     */
    public static byte[] toTap(byte[] program, String entryName, int autostartLine) {
        byte[] body = program == null ? new byte[0] : program;
        int autostart = (autostartLine >= 0 && autostartLine <= 9999) ? autostartLine : NO_AUTOSTART;

        byte[] header = new byte[17];
        header[0] = PROGRAM_TYPE;
        writeName(header, entryName);
        putWord(header, 11, body.length);
        putWord(header, 13, autostart);
        putWord(header, 15, body.length);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBlock(out, HEADER_FLAG, header, 0, header.length);
        writeBlock(out, DATA_FLAG, body, 0, body.length);
        return out.toByteArray();
    }

    private static void writeName(byte[] header, String entryName) {
        String name = entryName == null ? "" : entryName.trim().toUpperCase(java.util.Locale.ROOT);
        byte[] raw = name.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < 10; i++) {
            header[1 + i] = i < raw.length ? raw[i] : (byte) 0x20;
        }
    }

    private static void writeBlock(ByteArrayOutputStream out, int flag, byte[] data, int offset, int length) {
        int blockLength = length + 2;
        out.write(blockLength & 0xFF);
        out.write((blockLength >> 8) & 0xFF);
        out.write(flag);
        int checksum = flag;
        for (int i = 0; i < length; i++) {
            int b = data[offset + i] & 0xFF;
            out.write(b);
            checksum ^= b;
        }
        out.write(checksum & 0xFF);
    }

    private static void putWord(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
