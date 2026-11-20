package com.retroeditor.service.atari.xex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Ejecuta el cargador 6502 en un emulador minimo para validar la copia, la
 * reubicacion de punteros y las guardas de memoria sin depender de Altirra.
 */
class Loader6502EmulationTest {

    private static final int LOADER_ADDRESS = AtariBasicXexPackager.LOADER_ADDRESS;
    private static final int PAYLOAD_ADDRESS = AtariBasicXexPackager.PAYLOAD_ADDRESS;
    private static final int DEFAULT_PAYLOAD_LENGTH = 0x300;
    private static final int SENTINEL = 0xFF00;

    @Test
    void copiesPayloadAndRewritesPointersForDifferentLomemValues() throws Exception {
        int[] lomemValues = {0x1000, 0x3E00, 0x3F00, 0x3F80, 0x5000};
        for (int lomem : lomemValues) {
            Scenario scenario = runScenario(lomem, DEFAULT_PAYLOAD_LENGTH);
            int destination = lomem + 0x100;

            assertFalse(scenario.cpu.aborted, "Aborto inesperado con LOMEM=$" + hex(lomem));
            assertEquals(lomem, readWord(scenario.memory, Loader6502Builder.ZP_LOMEM),
                    "LOMEM no inicializado desde MEMLO=$" + hex(lomem));
            assertEquals(0, scenario.memory[0x92] & 0xFF, "Flag $92 no inicializado");
            assertEquals(0, scenario.memory[0xCA] & 0xFF, "Flag $CA no inicializado");
            assertEquals(0x0A, scenario.memory[0xC9] & 0xFF, "Flag $C9 no inicializado");
            assertTrue(scenario.cpu.xrunCalled, "No se llamo a XRUN con LOMEM=$" + hex(lomem));
            assertTrue(scenario.cpu.stackResetBeforeXrun, "Stack no reiniciado antes de XRUN");
            assertTrue(scenario.cpu.execnlReached, "No se llego a EXECNL con LOMEM=$" + hex(lomem));
            assertArrayEquals(scenario.originalPayload, slice(scenario.memory, destination, DEFAULT_PAYLOAD_LENGTH),
                    "Copia incorrecta con LOMEM=$" + hex(lomem));
            assertPointerTable(scenario, lomem);
            assertEquals(0xFF, scenario.memory[0xA8] & 0xFF, "STINDEX incorrecto");
            assertEquals(0x00, scenario.memory[0xA7] & 0xFF, "NXTSTD incorrecto");
        }
    }

    @Test
    void copiesPayloadsOfDifferentLengthsIncludingMaximum() throws Exception {
        int[] lengths = {6, 7, 0x100, 0x101, 0x8000};
        int lomem = 0x1000;
        for (int length : lengths) {
            Scenario scenario = runScenario(lomem, length);
            int destination = lomem + 0x100;

            assertFalse(scenario.cpu.aborted, "Aborto inesperado con longitud " + length);
            assertTrue(scenario.cpu.execnlReached, "No se llego a EXECNL con longitud " + length);
            assertArrayEquals(scenario.originalPayload, slice(scenario.memory, destination, length),
                    "Copia incorrecta con longitud " + length);
            assertPointerTable(scenario, lomem);
        }
    }

    @Test
    void abortsWhenDestinationOverlapsLoaderPage() throws Exception {
        Scenario scenario = runScenario(0x0400, DEFAULT_PAYLOAD_LENGTH);

        assertTrue(scenario.cpu.aborted);
        assertFalse(scenario.cpu.xrunCalled);
    }

    @Test
    void abortsWhenDestinationIsBelowLoader() throws Exception {
        Scenario scenario = runScenario(0x0100, 0x20);

        assertTrue(scenario.cpu.aborted);
        assertFalse(scenario.cpu.xrunCalled);
    }

    @Test
    void abortsWhenPayloadEndsInsideLoader() throws Exception {
        Scenario scenario = runScenario(LOADER_ADDRESS - 0x110, 0x20);

        assertTrue(scenario.cpu.aborted);
        assertFalse(scenario.cpu.xrunCalled);
    }

    @Test
    void abortsOnlyWhenDestinationStartsInsideLoader() throws Exception {
        Scenario reference = runScenario(0x1000, 0x20);
        int loaderEnd = LOADER_ADDRESS + reference.loaderLength;

        Scenario aborting = runScenario(loaderEnd - 1 - 0x100, 0x20);
        assertTrue(aborting.cpu.aborted);
        assertFalse(aborting.cpu.xrunCalled);

        Scenario allowed = runScenario(loaderEnd - 0x100, 0x20);
        assertFalse(allowed.cpu.aborted);
        assertTrue(allowed.cpu.execnlReached);
    }

    @Test
    void abortsWhenDestinationWouldReachIoArea() throws Exception {
        Scenario scenario = runScenario(0xBF00, DEFAULT_PAYLOAD_LENGTH);

        assertTrue(scenario.cpu.aborted);
        assertFalse(scenario.cpu.xrunCalled);
    }

    @Test
    void abortsOnlyWhenPayloadEndsAtIoBoundary() throws Exception {
        int payloadLength = 0x20;
        int abortingDestination = 0xC000 - payloadLength;

        Scenario aborting = runScenario(abortingDestination - 0x100, payloadLength);
        assertTrue(aborting.cpu.aborted);
        assertFalse(aborting.cpu.xrunCalled);

        Scenario allowed = runScenario(abortingDestination - 1 - 0x100, payloadLength);
        assertFalse(allowed.cpu.aborted);
        assertTrue(allowed.cpu.execnlReached);
        assertArrayEquals(allowed.originalPayload,
                slice(allowed.memory, abortingDestination - 1, payloadLength));
    }

    private static void assertPointerTable(Scenario scenario, int lomem) {
        for (int i = 0; i < 8; i++) {
            int pointer = (scenario.memory[0x82 + i * 2] & 0xFF)
                    | ((scenario.memory[0x83 + i * 2] & 0xFF) << 8);
            int offset = scenario.offsets[Math.min(i, scenario.offsets.length - 1)];
            assertEquals((lomem + offset) & 0xFFFF, pointer,
                    "Puntero " + i + " incorrecto para LOMEM=$" + hex(lomem));
        }
    }

    private static Scenario runScenario(int lomem, int payloadLength) throws Exception {
        byte[] tokenized = syntheticTokenized(payloadLength);
        int[] offsets = new int[6];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = (tokenized[(i + 1) * 2] & 0xFF) | ((tokenized[(i + 1) * 2 + 1] & 0xFF) << 8);
        }
        byte[] xex = new AtariBasicXexPackager().pack(tokenized);
        byte[] loader = firstSegmentData(xex);
        byte[] payload = secondSegmentData(xex);
        assertArrayEquals(Arrays.copyOfRange(tokenized, AtariBasicXexPackager.BASIC_HEADER_SIZE, tokenized.length),
                payload);

        byte[] memory = new byte[0x10000];
        System.arraycopy(loader, 0, memory, LOADER_ADDRESS, loader.length);
        System.arraycopy(payload, 0, memory, PAYLOAD_ADDRESS, payload.length);
        memory[Loader6502Builder.MEMLO] = (byte) (lomem & 0xFF);
        memory[Loader6502Builder.MEMLO + 1] = (byte) ((lomem >> 8) & 0xFF);

        Mini6502 cpu = new Mini6502(memory);
        cpu.prepareSentinel(SENTINEL);
        cpu.pc = LOADER_ADDRESS;
        cpu.run(10_000_000);

        return new Scenario(memory, cpu, offsets, payload.clone(), loader.length);
    }

    private static byte[] syntheticTokenized(int payloadLength) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        putWord(out, 0);
        putWord(out, 0x100);
        putWord(out, 0x0FF);
        putWord(out, 0x100);
        putWord(out, 0x100);
        putWord(out, 0x100 + payloadLength - 6);
        putWord(out, 0x100 + payloadLength);
        for (int i = 0; i < payloadLength; i++) {
            out.write((i * 31 + 7) & 0xFF);
        }
        return out.toByteArray();
    }

    private static byte[] firstSegmentData(byte[] xex) {
        int start = readWord(xex, 2);
        int end = readWord(xex, 4);
        return Arrays.copyOfRange(xex, 6, 6 + end - start + 1);
    }

    private static byte[] secondSegmentData(byte[] xex) {
        int firstLength = firstSegmentData(xex).length;
        int offset = 6 + firstLength;
        int start = readWord(xex, offset + 2);
        int end = readWord(xex, offset + 4);
        assertEquals(PAYLOAD_ADDRESS, start);
        return Arrays.copyOfRange(xex, offset + 6, offset + 6 + end - start + 1);
    }

    private static byte[] slice(byte[] data, int offset, int length) {
        return Arrays.copyOfRange(data, offset, offset + length);
    }

    private static void putWord(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static String hex(int value) {
        return String.format("%04X", value & 0xFFFF);
    }

    private record Scenario(byte[] memory, Mini6502 cpu, int[] offsets, byte[] originalPayload, int loaderLength) {
    }

    /** Subconjunto del 6502 suficiente para ejecutar el cargador generado. */
    private static final class Mini6502 {

        private final byte[] memory;
        private int a;
        private int x;
        private int y;
        private int sp;
        private int pc;
        private boolean carry;
        private boolean zero;
        private boolean aborted;
        private boolean xrunCalled;
        private boolean stackResetBeforeXrun;
        private boolean execnlReached;
        private int xrunReturn;

        private Mini6502(byte[] memory) {
            this.memory = memory;
        }

        private void prepareSentinel(int address) {
            sp = 0xFD;
            memory[0x01FE] = (byte) ((address - 1) & 0xFF);
            memory[0x01FF] = (byte) (((address - 1) >> 8) & 0xFF);
        }

        private void run(int maxSteps) {
            for (int step = 0; step < maxSteps; step++) {
                if (pc == SENTINEL) {
                    aborted = true;
                    return;
                }
                if (pc == Loader6502Builder.EXECNL) {
                    execnlReached = true;
                    return;
                }
                if (pc == Loader6502Builder.XRUN) {
                    xrunCalled = true;
                    pc = xrunReturn;
                    continue;
                }
                step();
            }
            fail("El cargador no termino; PC=$" + String.format("%04X", pc));
        }

        private void step() {
            int opcode = memory[pc] & 0xFF;
            pc = (pc + 1) & 0xFFFF;
            switch (opcode) {
                case 0xA5 -> a = readZeroPage();
                case 0xAD -> a = memory[readAbsolute()] & 0xFF;
                case 0xA9 -> a = readImmediate();
                case 0x85 -> memory[readZeroPageAddress()] = (byte) a;
                case 0x95 -> memory[(readZeroPageAddress() + x) & 0xFF] = (byte) a;
                case 0xA2 -> x = readImmediate();
                case 0xA0 -> y = readImmediate();
                case 0xC9 -> compare(a, readImmediate());
                case 0xE0 -> compare(x, readImmediate());
                case 0x05 -> {
                    a |= memory[readZeroPageAddress()] & 0xFF;
                    setZeroNegative(a);
                }
                case 0x18 -> carry = false;
                case 0x38 -> carry = true;
                case 0x69 -> add(readImmediate());
                case 0x7D -> add(readAbsoluteX());
                case 0xE9 -> subtract(readImmediate());
                case 0xE6 -> {
                    int address = readZeroPageAddress();
                    memory[address] = (byte) ((memory[address] + 1) & 0xFF);
                    setZeroNegative(memory[address] & 0xFF);
                }
                case 0xC6 -> {
                    int address = readZeroPageAddress();
                    memory[address] = (byte) ((memory[address] - 1) & 0xFF);
                    setZeroNegative(memory[address] & 0xFF);
                }
                case 0xE8 -> {
                    x = (x + 1) & 0xFF;
                    setZeroNegative(x);
                }
                case 0xB1 -> a = readIndirectY();
                case 0x91 -> memory[readIndirectYAddress()] = (byte) a;
                case 0x90 -> branchIf(!carry);
                case 0xB0 -> branchIf(carry);
                case 0xD0 -> branchIf(!zero);
                case 0x20 -> {
                    int target = readAbsolute();
                    int returnAddress = (pc - 1) & 0xFFFF;
                    if (target == Loader6502Builder.XRUN) {
                        xrunReturn = pc & 0xFFFF;
                        stackResetBeforeXrun = sp == 0xFF;
                    }
                    push((returnAddress >> 8) & 0xFF);
                    push(returnAddress & 0xFF);
                    pc = target;
                }
                case 0x4C -> pc = readAbsolute();
                case 0x9A -> sp = x;
                case 0x60 -> rts();
                default -> fail("Opcode no soportado $" + String.format("%02X", opcode)
                        + " en PC=$" + String.format("%04X", (pc - 1) & 0xFFFF));
            }
        }

        private void add(int value) {
            int sum = a + value + (carry ? 1 : 0);
            carry = sum > 0xFF;
            a = sum & 0xFF;
            setZeroNegative(a);
        }

        private void subtract(int value) {
            int difference = a - value - (carry ? 0 : 1);
            carry = difference >= 0;
            a = difference & 0xFF;
            setZeroNegative(a);
        }

        private void compare(int register, int value) {
            int difference = register - value;
            carry = difference >= 0;
            setZeroNegative(difference & 0xFF);
        }

        private void branchIf(boolean condition) {
            int offset = (byte) memory[pc];
            pc = (pc + 1) & 0xFFFF;
            if (condition) {
                pc = (pc + offset) & 0xFFFF;
            }
        }

        private void rts() {
            int low = pull();
            int high = pull();
            pc = (((high << 8) | low) + 1) & 0xFFFF;
        }

        private int readImmediate() {
            int value = memory[pc] & 0xFF;
            pc = (pc + 1) & 0xFFFF;
            setZeroNegative(value);
            return value;
        }

        private int readZeroPageAddress() {
            int address = memory[pc] & 0xFF;
            pc = (pc + 1) & 0xFFFF;
            return address;
        }

        private int readZeroPage() {
            int value = memory[readZeroPageAddress()] & 0xFF;
            setZeroNegative(value);
            return value;
        }

        private int readAbsolute() {
            int address = readWord(memory, pc);
            pc = (pc + 2) & 0xFFFF;
            return address;
        }

        private int readAbsoluteX() {
            int address = (readAbsolute() + x) & 0xFFFF;
            int value = memory[address] & 0xFF;
            setZeroNegative(value);
            return value;
        }

        private int readIndirectYAddress() {
            int zeroPage = readZeroPageAddress();
            int base = (memory[zeroPage] & 0xFF) | ((memory[zeroPage + 1] & 0xFF) << 8);
            return (base + y) & 0xFFFF;
        }

        private int readIndirectY() {
            int value = memory[readIndirectYAddress()] & 0xFF;
            setZeroNegative(value);
            return value;
        }

        private void setZeroNegative(int value) {
            zero = (value & 0xFF) == 0;
        }

        private void push(int value) {
            memory[0x100 + sp] = (byte) value;
            sp = (sp - 1) & 0xFF;
        }

        private int pull() {
            sp = (sp + 1) & 0xFF;
            return memory[0x100 + sp] & 0xFF;
        }
    }
}
