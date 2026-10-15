package com.retroeditor.service.atari.atr;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de imagenes ATR de Atari DOS 2.0S. Permite enumerar el directorio y
 * extraer el contenido de un fichero siguiendo su cadena de sectores, lo que
 * resulta util para verificar las imagenes generadas por {@link AtrImageWriter}
 * y para futuras importaciones.
 */
public final class AtrImageReader {

    private static final int ATR_HEADER_SIZE = 16;
    private static final int SECTOR_SIZE = 128;
    private static final int ENTRY_SIZE = 16;
    private static final int DIR_FIRST_SECTOR = 361;
    private static final int DIR_SECTOR_COUNT = 8;
    private static final int ENTRY_FLAG_IN_USE = 0x40;
    private static final int ENTRY_FLAG_DELETED = 0x80;
    private static final int MAX_CHAIN = 2048;

    private final byte[] body;

    private AtrImageReader(byte[] body) {
        this.body = body;
    }

    public static AtrImageReader parse(byte[] atr) {
        if (atr == null || atr.length < ATR_HEADER_SIZE) {
            throw new IllegalArgumentException("Imagen ATR demasiado corta");
        }
        if ((atr[0] & 0xFF) != 0x96 || (atr[1] & 0xFF) != 0x02) {
            throw new IllegalArgumentException("Cabecera ATR invalida");
        }
        int bodySize = readWord(atr, 2) * 16;
        int sectorSize = readWord(atr, 4);
        if (sectorSize != SECTOR_SIZE) {
            throw new IllegalArgumentException("Solo se soportan sectores de 128 bytes (simple densidad)");
        }
        if (atr.length < ATR_HEADER_SIZE + bodySize) {
            throw new IllegalArgumentException("La imagen ATR esta truncada");
        }
        byte[] body = new byte[bodySize];
        System.arraycopy(atr, ATR_HEADER_SIZE, body, 0, bodySize);
        return new AtrImageReader(body);
    }

    public List<AtrFileEntry> listFiles() {
        List<AtrFileEntry> entries = new ArrayList<>();
        for (int directoryIndex = 0; directoryIndex < DIR_SECTOR_COUNT * (SECTOR_SIZE / ENTRY_SIZE); directoryIndex++) {
            int sector = DIR_FIRST_SECTOR + directoryIndex / (SECTOR_SIZE / ENTRY_SIZE);
            int within = directoryIndex % (SECTOR_SIZE / ENTRY_SIZE);
            int offset = (sector - 1) * SECTOR_SIZE + within * ENTRY_SIZE;
            int flag = body[offset] & 0xFF;
            if ((flag & ENTRY_FLAG_IN_USE) == 0) {
                continue;
            }
            String name = readField(offset + 5, 8);
            String extension = readField(offset + 13, 3);
            int sectors = body[offset + 1] & 0xFF | (body[offset + 2] & 0xFF) << 8;
            int start = body[offset + 3] & 0xFF | (body[offset + 4] & 0xFF) << 8;
            entries.add(new AtrFileEntry(
                    directoryIndex, name, extension, sectors, start,
                    (flag & ENTRY_FLAG_DELETED) != 0));
        }
        return entries;
    }

    public AtrFileEntry findFile(String fullName) {
        for (AtrFileEntry entry : listFiles()) {
            if (entry.fullName().equalsIgnoreCase(fullName) && !entry.deleted()) {
                return entry;
            }
        }
        return null;
    }

    public byte[] readFile(AtrFileEntry entry) {
        List<Byte> data = new ArrayList<>();
        int sector = entry.startSector();
        int guard = 0;
        while (sector != 0 && guard++ < MAX_CHAIN) {
            int offset = (sector - 1) * SECTOR_SIZE;
            int bytes = body[offset + 127] & 0xFF;
            bytes = Math.min(bytes, SECTOR_SIZE - 3);
            for (int i = 0; i < bytes; i++) {
                data.add(body[offset + i]);
            }
            int next = (body[offset + 126] & 0xFF) | ((body[offset + 125] & 0x03) << 8);
            sector = next;
        }
        byte[] result = new byte[data.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = data.get(i);
        }
        return result;
    }

    private String readField(int offset, int length) {
        byte[] field = new byte[length];
        System.arraycopy(body, offset, field, 0, length);
        return new String(field, StandardCharsets.US_ASCII);
    }

    private static int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
}
