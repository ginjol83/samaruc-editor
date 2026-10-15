package com.retroeditor.service.atari.atr;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Escritor de imagenes de disco ATR en formato Atari DOS 2.0S de una cara y
 * simple densidad (40 pistas x 18 sectores x 128 bytes = 92160 bytes de datos,
 * ATR de 92176 bytes). Es el formato minimo y universalmente legible por el
 * Atari OS y por Altirra, suficiente para depositar un programa BASIC
 * tokenizado y cargarlo con {@code LOAD}/{@code RUN}.
 *
 * <p>El mapa de sectores libres del VTOC numera las entradas 0..719: el bit 0
 * (sector fisico 0, inexistente) y el sector 720 no existen, mientras que los
 * sectores fisicos 1..719 usan como indice su propio numero. Cada bit puesto a
 * 1 significa libre.
 */
public final class AtrImageWriter {

    public static final int SECTOR_SIZE = 128;
    public static final int SECTOR_COUNT = 720;

    public static final int VTOC_SECTOR = 360;
    public static final int DIR_FIRST_SECTOR = 361;
    public static final int DIR_SECTOR_COUNT = 8;
    public static final int DIR_ENTRY_COUNT = DIR_SECTOR_COUNT * (SECTOR_SIZE / 16);

    /** Numero de sectores libres de un disco recien formateado (720 - 13 reservados). */
    public static final int INITIAL_FREE_SECTORS = 707;

    private static final int ENTRY_SIZE = 16;
    private static final int DATA_SIZE = 125;
    private static final int BOOT_SECTOR_COUNT = 3;
    private static final int MAX_SECTORS_PER_FILE = 2048;

    private static final int VTOC_TYPE = 0;
    private static final int VTOC_INIT_FREE = 1;
    private static final int VTOC_CUR_FREE = 3;
    private static final int VTOC_BITMAP = 10;

    private final byte[] body = new byte[SECTOR_COUNT * SECTOR_SIZE];
    /** Indice = numero de sector fisico (0 reservado, 1..719 usables). Valor true = libre. */
    private final boolean[] free = new boolean[SECTOR_COUNT];

    public AtrImageWriter() {
        for (int sector = 0; sector < SECTOR_COUNT; sector++) {
            free[sector] = true;
        }
        free[0] = false;
        for (int sector = 1; sector <= BOOT_SECTOR_COUNT; sector++) {
            free[sector] = false;
        }
        free[VTOC_SECTOR] = false;
        for (int sector = DIR_FIRST_SECTOR; sector < DIR_FIRST_SECTOR + DIR_SECTOR_COUNT; sector++) {
            free[sector] = false;
        }
    }

    /** Numero de sectores libres en el estado actual de la imagen. */
    public int freeSectorCount() {
        int count = 0;
        for (int sector = 1; sector < SECTOR_COUNT; sector++) {
            if (free[sector]) count++;
        }
        return count;
    }

    /**
     * Anade un fichero al directorio y asigna los sectores que necesita. El
     * nombre se normaliza al formato 8.3 (mayusculas, relleno con espacios).
     */
    public int addFile(String fileName, byte[] content) {
        if (content == null) {
            content = new byte[0];
        }
        int entry = findFreeEntry();
        if (entry < 0) {
            throw new IllegalStateException("El directorio del disco esta lleno");
        }
        int sectors = Math.max(1, (content.length + DATA_SIZE - 1) / DATA_SIZE);
        if (sectors > MAX_SECTORS_PER_FILE) {
            throw new IllegalArgumentException("El fichero es demasiado grande para el disco");
        }
        int[] allocation = allocate(sectors);
        writeDataSectors(allocation, content, entry);
        writeDirectoryEntry(entry, fileName, allocation[0], sectors);
        return entry;
    }

    /** Serializa la imagen completa con su cabecera ATR de 16 bytes. */
    public byte[] toByteArray() {
        writeVtoc();
        byte[] image = new byte[16 + body.length];
        image[0] = (byte) 0x96;
        image[1] = (byte) 0x02;
        putWord(image, 2, body.length / 16);
        putWord(image, 4, SECTOR_SIZE);
        System.arraycopy(body, 0, image, 16, body.length);
        return image;
    }

    private int findFreeEntry() {
        int offset = directoryOffset();
        for (int entry = 0; entry < DIR_ENTRY_COUNT; entry++) {
            if ((body[offset + entry * ENTRY_SIZE] & 0xFF) == 0) {
                return entry;
            }
        }
        return -1;
    }

    private int[] allocate(int count) {
        List<Integer> sectors = new ArrayList<>(count);
        for (int sector = 1; sector < SECTOR_COUNT && sectors.size() < count; sector++) {
            if (free[sector]) {
                free[sector] = false;
                sectors.add(sector);
            }
        }
        if (sectors.size() < count) {
            throw new IllegalStateException("No hay espacio suficiente en el disco");
        }
        int[] result = new int[sectors.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = sectors.get(i);
        }
        return result;
    }

    private void writeDataSectors(int[] allocation, byte[] content, int fileNumber) {
        for (int i = 0; i < allocation.length; i++) {
            int sector = allocation[i];
            int offset = (sector - 1) * SECTOR_SIZE;
            int remaining = content.length - i * DATA_SIZE;
            int bytes = Math.min(DATA_SIZE, Math.max(0, remaining));
            if (bytes > 0) {
                System.arraycopy(content, i * DATA_SIZE, body, offset, bytes);
            }
            int next = (i + 1 < allocation.length) ? allocation[i + 1] : 0;
            body[offset + 125] = (byte) ((fileNumber << 2) | ((next >> 8) & 0x03));
            body[offset + 126] = (byte) (next & 0xFF);
            body[offset + 127] = (byte) bytes;
        }
    }

    private void writeDirectoryEntry(int entry, String fileName, int startSector, int sectors) {
        int offset = directoryOffset() + entry * ENTRY_SIZE;
        body[offset] = (byte) 0x42; // en uso + creado por DOS 2
        body[offset + 1] = (byte) (sectors & 0xFF);
        body[offset + 2] = (byte) ((sectors >> 8) & 0xFF);
        body[offset + 3] = (byte) (startSector & 0xFF);
        body[offset + 4] = (byte) ((startSector >> 8) & 0xFF);
        writeDirectoryName(body, offset + 5, fileName);
    }

    static void writeDirectoryName(byte[] target, int offset, String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        int dot = normalized.indexOf('.');
        String name = dot >= 0 ? normalized.substring(0, dot) : normalized;
        String extension = dot >= 0 ? normalized.substring(dot + 1) : "";
        fillField(target, offset, 8, name);
        fillField(target, offset + 8, 3, extension);
    }

    private static void fillField(byte[] target, int offset, int length, String value) {
        byte[] upper = value.toUpperCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < length; i++) {
            target[offset + i] = i < upper.length ? upper[i] : (byte) 0x20;
        }
    }

    private void writeVtoc() {
        int offset = (VTOC_SECTOR - 1) * SECTOR_SIZE;
        body[offset + VTOC_TYPE] = (byte) 0x02;
        putWord(body, offset + VTOC_INIT_FREE, INITIAL_FREE_SECTORS);
        putWord(body, offset + VTOC_CUR_FREE, freeSectorCount());
        for (int sector = 0; sector < SECTOR_COUNT; sector++) {
            if (free[sector]) {
                int index = VTOC_BITMAP + (sector >> 3);
                body[offset + index] |= (byte) (1 << (7 - (sector & 7)));
            }
        }
    }

    private static int directoryOffset() {
        return (DIR_FIRST_SECTOR - 1) * SECTOR_SIZE;
    }

    private static void putWord(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
