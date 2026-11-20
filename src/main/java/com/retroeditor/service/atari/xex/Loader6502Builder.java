package com.retroeditor.service.atari.xex;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generador del pequeno cargador 6502 que copia el programa Atari BASIC
 * tokenizado a su direccion de ejecucion, inicializa la zero page de BASIC,
 * repara los punteros y arranca el interprete sin pasar por el cargador de
 * ficheros de BASIC.
 *
 * <p>El cargador se ejecuta desde {@code $0600} y debe caber en una pagina para
 * no invadir la zona que BASIC usa como LOMEM en los Atari XL/XE.</p>
 */
final class Loader6502Builder {

    static final int MEMLO = 0x02E7;
    static final int ZP_LOMEM = 0x80;
    static final int ZP_VNTP = 0x82;
    static final int ZP_NXTSTD = 0xA7;
    static final int ZP_STINDEX = 0xA8;
    static final int ZP_SRC = 0xCB;
    static final int ZP_DST = 0xCD;
    static final int ZP_COUNT = 0xCF;

    static final int XRUN = 0xB74D;
    static final int EXECNL = 0xA95E;

    private static final int MAX_LOADER_SIZE = 0x100;
    private static final int POINTER_TABLE_SIZE = 16;

    private Loader6502Builder() {
    }

    static byte[] build(int loadAddress, int sourceAddress, int payloadLength, int[] offsets)
            throws XexException {
        if (payloadLength <= 0) {
            throw new XexException("El programa Atari BASIC tokenizado esta vacio");
        }
        if (payloadLength > 0x8000) {
            throw new XexException("El programa Atari BASIC es demasiado grande para un XEX");
        }
        if (offsets == null || offsets.length != 6) {
            throw new XexException("La cabecera Atari BASIC no contiene los seis punteros esperados");
        }
        byte[] loader = emit(loadAddress, sourceAddress, payloadLength, offsets, 0);
        for (int attempt = 0; attempt < 4; attempt++) {
            int loaderEnd = loadAddress + loader.length;
            byte[] next = emit(loadAddress, sourceAddress, payloadLength, offsets, loaderEnd);
            if (next.length == loader.length) {
                if (next.length > MAX_LOADER_SIZE) {
                    throw new XexException("El cargador XEX no cabe en una pagina: " + next.length + " bytes");
                }
                return next;
            }
            loader = next;
        }
        throw new XexException("No se pudo estabilizar el tamano del cargador XEX");
    }

    private static byte[] emit(int loadAddress,
                               int sourceAddress,
                               int payloadLength,
                               int[] offsets,
                               int loaderEnd) throws XexException {
        CodeBuilder code = new CodeBuilder(loadAddress);
        int payloadOffset = offsets[0];
        int payloadLow = payloadLength & 0xFF;
        int payloadHigh = (payloadLength >> 8) & 0xFF;

        code.label("START");
        ldaAbs(code, MEMLO);
        staZp(code, ZP_LOMEM);
        ldaAbs(code, MEMLO + 1);
        staZp(code, ZP_LOMEM + 1);
        ldaImm(code, 0);
        staZp(code, 0x92);
        staZp(code, 0xCA);
        ldaImm(code, 0x0A);
        staZp(code, 0xC9);

        // dest = LOMEM + payloadOffset
        ldaZp(code, ZP_LOMEM);
        code.op(0x18);
        code.op2(0x69, payloadOffset & 0xFF);
        staZp(code, ZP_DST);
        ldaZp(code, ZP_LOMEM + 1);
        code.op2(0x69, (payloadOffset >> 8) & 0xFF);
        staZp(code, ZP_DST + 1);

        // if (dest + payloadLength > $C000) return; no pisar I/O ni la ROM.
        ldaZp(code, ZP_DST);
        code.op(0x18);
        code.op2(0x69, payloadLow);
        ldaZp(code, ZP_DST + 1);
        code.op2(0x69, payloadHigh);
        staZp(code, ZP_COUNT + 1);
        code.op2(0xC9, 0xC0);
        code.branch(0x90, "IO_OK");

        code.label("ABORT");
        code.op(0x60);

        code.label("IO_OK");
        code.label("DIRECTION");
        ldaZp(code, ZP_DST + 1);
        code.op2(0xC9, (loaderEnd >> 8) & 0xFF);
        code.branch(0x90, "ABORT");
        if ((sourceAddress & 0xFF) == 0) {
            ldaZp(code, ZP_DST + 1);
            code.op2(0xC9, (sourceAddress >> 8) & 0xFF);
            code.branch(0xB0, "BACKWARD");
        } else {
            code.op(0x38);
            ldaZp(code, ZP_DST);
            code.op2(0xE9, sourceAddress & 0xFF);
            ldaZp(code, ZP_DST + 1);
            code.op2(0xE9, (sourceAddress >> 8) & 0xFF);
            code.branch(0xB0, "BACKWARD");
        }

        code.label("FORWARD");
        ldaImm(code, sourceAddress & 0xFF);
        staZp(code, ZP_SRC);
        ldaImm(code, (sourceAddress >> 8) & 0xFF);
        staZp(code, ZP_SRC + 1);
        ldaImm(code, payloadLow);
        staZp(code, ZP_COUNT);
        ldaImm(code, payloadHigh);
        staZp(code, ZP_COUNT + 1);
        code.op2(0xA0, 0x00);
        code.label("F_LOOP");
        code.op2(0xB1, ZP_SRC);
        code.op2(0x91, ZP_DST);
        inc16(code, ZP_SRC, "F_SRC");
        inc16(code, ZP_DST, "F_DST");
        decrementCount(code, "F");
        code.branch(0xD0, "F_LOOP");
        code.jump("SET_POINTERS");

        code.label("BACKWARD");
        // src = source + len - 1; dest = dest + len - 1.
        ldaImm(code, sourceAddress & 0xFF);
        code.op(0x18);
        code.op2(0x69, payloadLow);
        staZp(code, ZP_SRC);
        ldaImm(code, (sourceAddress >> 8) & 0xFF);
        code.op2(0x69, payloadHigh);
        staZp(code, ZP_SRC + 1);
        dec16(code, ZP_SRC, "B_SRC_END");

        ldaZp(code, ZP_DST);
        code.op(0x18);
        code.op2(0x69, payloadLow);
        staZp(code, ZP_DST);
        ldaZp(code, ZP_DST + 1);
        code.op2(0x69, payloadHigh);
        staZp(code, ZP_DST + 1);
        dec16(code, ZP_DST, "B_DST_END");

        ldaImm(code, payloadLow);
        staZp(code, ZP_COUNT);
        ldaImm(code, payloadHigh);
        staZp(code, ZP_COUNT + 1);
        code.op2(0xA0, 0x00);
        code.label("B_LOOP");
        code.op2(0xB1, ZP_SRC);
        code.op2(0x91, ZP_DST);
        dec16(code, ZP_SRC, "B_SRC");
        dec16(code, ZP_DST, "B_DST");
        decrementCount(code, "B");
        code.branch(0xD0, "B_LOOP");

        code.label("SET_POINTERS");
        code.op2(0xA2, 0x00);
        code.label("PTR_LOOP");
        ldaZp(code, ZP_LOMEM);
        code.op(0x18);
        code.absIndexedX(0x7D, "TABLE");
        code.op2(0x95, ZP_VNTP);
        ldaZp(code, ZP_LOMEM + 1);
        code.absIndexedX(0x7D, "TABLE_HI");
        code.op2(0x95, ZP_VNTP + 1);
        code.op(0xE8);
        code.op(0xE8);
        code.op2(0xE0, POINTER_TABLE_SIZE);
        code.branch(0xD0, "PTR_LOOP");

        // TSTEND debe devolver carry para que XRUN no intente cargar un fichero.
        ldaImm(code, 0xFF);
        staZp(code, ZP_STINDEX);
        ldaImm(code, 0x00);
        staZp(code, ZP_NXTSTD);
        code.op2(0xA2, 0xFF);
        code.op(0x9A);
        code.op3(0x20, XRUN & 0xFF, (XRUN >> 8) & 0xFF);
        code.op3(0x4C, EXECNL & 0xFF, (EXECNL >> 8) & 0xFF);

        while (code.size() < MAX_LOADER_SIZE - POINTER_TABLE_SIZE) {
            code.op(0xEA);
        }
        code.label("TABLE");
        for (int i = 0; i < POINTER_TABLE_SIZE / 2; i++) {
            int value = i < offsets.length ? offsets[i] : offsets[offsets.length - 1];
            if (i == 0) {
                code.op(value & 0xFF);
                code.label("TABLE_HI");
                code.op((value >> 8) & 0xFF);
            } else {
                putWord(code, value);
            }
        }

        return code.toByteArray();
    }

    private static void putWord(CodeBuilder code, int value) {
        code.op(value & 0xFF);
        code.op((value >> 8) & 0xFF);
    }

    private static void ldaZp(CodeBuilder code, int address) {
        code.op2(0xA5, address);
    }

    private static void ldaAbs(CodeBuilder code, int address) {
        code.op3(0xAD, address & 0xFF, (address >> 8) & 0xFF);
    }

    private static void ldaImm(CodeBuilder code, int value) {
        code.op2(0xA9, value);
    }

    private static void staZp(CodeBuilder code, int address) {
        code.op2(0x85, address);
    }

    private static void inc16(CodeBuilder code, int address, String label) {
        code.op2(0xE6, address);
        code.branch(0xD0, label);
        code.op2(0xE6, address + 1);
        code.label(label);
    }

    private static void dec16(CodeBuilder code, int address, String label) {
        ldaZp(code, address);
        code.branch(0xD0, label);
        code.op2(0xC6, address + 1);
        code.label(label);
        code.op2(0xC6, address);
    }

    private static void decrementCount(CodeBuilder code, String prefix) {
        ldaZp(code, ZP_COUNT);
        code.branch(0xD0, prefix + "_COUNT_NO_BORROW");
        code.op2(0xC6, ZP_COUNT + 1);
        code.label(prefix + "_COUNT_NO_BORROW");
        code.op2(0xC6, ZP_COUNT);
        ldaZp(code, ZP_COUNT);
        code.op2(0x05, ZP_COUNT + 1);
    }

    /** Ensamblador minimo con etiquetas y parches de direcciones internas. */
    private static final class CodeBuilder {

        private final int baseAddress;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final Map<String, Integer> labels = new HashMap<>();
        private final List<Fixup> absoluteFixups = new ArrayList<>();
        private final Map<Integer, String> pendingAbsolute = new HashMap<>();

        private CodeBuilder(int baseAddress) {
            this.baseAddress = baseAddress;
        }

        void label(String name) {
            if (labels.putIfAbsent(name, address()) != null) {
                throw new IllegalStateException("Etiqueta duplicada: " + name);
            }
        }

        int size() {
            return out.size();
        }

        void op(int value) {
            out.write(value & 0xFF);
        }

        void op2(int opcode, int operand) {
            op(opcode);
            op(operand);
        }

        void op3(int opcode, int low, int high) {
            op(opcode);
            op(low);
            op(high);
        }

        void absIndexedX(int opcode, String label) {
            op(opcode);
            int fixup = address();
            op(0);
            op(0);
            absoluteFixups.add(new Fixup(label, fixup));
        }

        void branch(int opcode, String label) {
            op(opcode);
            int operandAddress = address();
            op(0);
            pendingAbsolute.put(operandAddress, label);
        }

        void jump(String label) {
            op(0x4C);
            int fixup = address();
            op(0);
            op(0);
            absoluteFixups.add(new Fixup(label, fixup));
        }

        byte[] toByteArray() {
            for (Map.Entry<Integer, String> entry : pendingAbsolute.entrySet()) {
                int operandAddress = entry.getKey();
                int target = resolve(entry.getValue());
                int offset = target - (operandAddress + 1);
                if (offset < -128 || offset > 127) {
                    throw new IllegalStateException(
                            "Salto relativo fuera de rango hacia " + entry.getValue() + ": " + offset);
                }
                byte[] bytes = out.toByteArray();
                bytes[operandAddress - baseAddress] = (byte) (offset & 0xFF);
                out.reset();
                out.write(bytes, 0, bytes.length);
            }
            for (Fixup fixup : absoluteFixups) {
                int fixupAddress = fixup.address();
                int target = resolve(fixup.label());
                byte[] bytes = out.toByteArray();
                bytes[fixupAddress - baseAddress] = (byte) (target & 0xFF);
                bytes[fixupAddress - baseAddress + 1] = (byte) ((target >> 8) & 0xFF);
                out.reset();
                out.write(bytes, 0, bytes.length);
            }
            return out.toByteArray();
        }

        private int resolve(String label) {
            Integer value = labels.get(label);
            if (value == null) {
                throw new IllegalStateException("Etiqueta desconocida: " + label);
            }
            return value;
        }

        private int address() {
            return baseAddress + out.size();
        }

        private record Fixup(String label, int address) {
        }
    }
}
