package com.retroeditor.service.atari.xex;

import java.util.Arrays;

/**
 * Empaqueta un programa Atari BASIC ya tokenizado (formato {@code .bas}) en un
 * fichero XEX autoarrancable.
 *
 * <p>El XEX contiene dos segmentos: el cargador en {@code $0600} y el programa
 * tokenizado en {@code $4000}. Al ejecutarse, el cargador inicializa la zero
 * page de BASIC desde MEMLO, copia el programa a {@code LOMEM + VNTP}
 * (normalmente {@code $0800}), repara los punteros y salta al interprete. Es
 * necesario que Atari BASIC
 * Rev. B/C este activo: el interprete no se incluye en el XEX.</p>
 */
public final class AtariBasicXexPackager {

    public static final int LOADER_ADDRESS = 0x0600;
    public static final int PAYLOAD_ADDRESS = 0x4000;
    public static final int BASIC_HEADER_SIZE = 14;
    public static final int MAX_PAYLOAD_SIZE = 0xC000 - PAYLOAD_ADDRESS;

    private static final int MAX_ADDRESS = 0xFFFF;

    /**
     * @param tokenizedBas salida de {@code AtariBasicTokenizer}: cabecera de
     *                     siete palabras (LOMEM, VNTP, VNTD, VVTP, STMTAB,
     *                     STMCUR, STARP) seguida de VNT, VVT y programa.
     */
    public byte[] pack(byte[] tokenizedBas) throws XexException {
        if (tokenizedBas == null || tokenizedBas.length <= BASIC_HEADER_SIZE) {
            throw new XexException("El programa Atari BASIC tokenizado esta vacio");
        }
        int lomem = readWord(tokenizedBas, 0);
        if (lomem != 0) {
            throw new XexException("Solo se admiten programas Atari BASIC con LOMEM a cero");
        }
        int[] offsets = new int[6];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = readWord(tokenizedBas, (i + 1) * 2);
            if (offsets[i] < 0 || offsets[i] > MAX_ADDRESS) {
                throw new XexException("Puntero Atari BASIC fuera de rango");
            }
        }
        validateOffsets(offsets);

        byte[] payload = Arrays.copyOfRange(tokenizedBas, BASIC_HEADER_SIZE, tokenizedBas.length);
        if (payload.length > MAX_PAYLOAD_SIZE) {
            throw new XexException("El programa Atari BASIC supera el maximo de "
                    + MAX_PAYLOAD_SIZE + " bytes para un XEX");
        }

        byte[] loader = Loader6502Builder.build(LOADER_ADDRESS, PAYLOAD_ADDRESS, payload.length, offsets);

        XexWriter writer = new XexWriter();
        writer.addSegment(LOADER_ADDRESS, loader);
        writer.addSegment(PAYLOAD_ADDRESS, payload);
        writer.setRunAddress(LOADER_ADDRESS);
        return writer.toByteArray();
    }

    private static void validateOffsets(int[] offsets) throws XexException {
        if (offsets[0] <= 0 || offsets[2] < offsets[0]) {
            throw new XexException("Cabecera Atari BASIC incoherente");
        }
        if (offsets[3] < offsets[2] || offsets[4] < offsets[3] || offsets[5] < offsets[4]) {
            throw new XexException("Tabla de programa Atari BASIC incoherente");
        }
        if (offsets[4] + 6 > offsets[5]) {
            throw new XexException("La linea inmediata Atari BASIC no cabe en el programa");
        }
    }

    private static int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
}
