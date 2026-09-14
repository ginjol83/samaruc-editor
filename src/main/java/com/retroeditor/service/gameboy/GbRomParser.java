package com.retroeditor.service.gameboy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Parser de ROMs de Game Boy / Game Boy Color.
 * Lee la estructura del ROM: banks de 16KB, header y cartridge type.
 */
public class GbRomParser {

    private static final int BANK_SIZE = 16 * 1024;
    private static final int HEADER_OFFSET = 0x100;
    private static final int TITLE_OFFSET = 0x134;
    private static final int TITLE_MAX_LENGTH = 16;
    private static final int CARTRIDGE_TYPE_OFFSET = 0x147;
    private static final int ROM_SIZE_OFFSET = 0x148;

    private final byte[] romData;
    private final int bankCount;
    private final String title;
    private final int cartridgeType;
    private final int romSizeCode;

    /**
     * Carga un ROM completo en memoria.
     */
    public GbRomParser(byte[] romData) {
        if (romData == null || romData.length < BANK_SIZE) {
            throw new IllegalArgumentException("ROM data is null or too small (minimum 16KB)");
        }
        this.romData = romData.clone();
        this.bankCount = romData.length / BANK_SIZE;
        this.title = readTitle();
        this.cartridgeType = romData[CARTRIDGE_TYPE_OFFSET] & 0xFF;
        this.romSizeCode = romData[ROM_SIZE_OFFSET] & 0xFF;
    }

    /**
     * Carga un ROM desde un archivo.
     */
    public static GbRomParser fromFile(Path romPath) throws IOException {
        byte[] data = Files.readAllBytes(romPath);
        return new GbRomParser(data);
    }

    /**
     * Devuelve los bytes de un bank completo.
     */
    public byte[] getBank(int bankNumber) {
        if (bankNumber < 0 || bankNumber >= bankCount) {
            throw new IndexOutOfBoundsException("Bank " + bankNumber + " out of range (0-" + (bankCount - 1) + ")");
        }
        int start = bankNumber * BANK_SIZE;
        return Arrays.copyOfRange(romData, start, start + BANK_SIZE);
    }

    /**
     * Lee un byte en una posición absoluta del ROM.
     */
    public int readByte(int absoluteOffset) {
        if (absoluteOffset < 0 || absoluteOffset >= romData.length) {
            return -1;
        }
        return romData[absoluteOffset] & 0xFF;
    }

    /**
     * Lee varios bytes en una posición absoluta del ROM.
     */
    public byte[] readBytes(int absoluteOffset, int length) {
        if (absoluteOffset < 0 || absoluteOffset + length > romData.length) {
            int available = Math.max(0, romData.length - absoluteOffset);
            return Arrays.copyOfRange(romData, absoluteOffset, absoluteOffset + available);
        }
        return Arrays.copyOfRange(romData, absoluteOffset, absoluteOffset + length);
    }

    /**
     * Convierte una dirección de bank+offset a una posición absoluta.
     */
    public int toAbsoluteOffset(int bank, int bankOffset) {
        return bank * BANK_SIZE + bankOffset;
    }

    private String readTitle() {
        int end = Math.min(TITLE_OFFSET + TITLE_MAX_LENGTH, romData.length);
        StringBuilder sb = new StringBuilder();
        for (int i = TITLE_OFFSET; i < end; i++) {
            int b = romData[i] & 0xFF;
            if (b == 0) break;
            if (b >= 0x20 && b < 0x7F) {
                sb.append((char) b);
            } else {
                sb.append(String.format("\\x%02X", b));
            }
        }
        return sb.toString();
    }

    public byte[] getRomData() {
        return romData.clone();
    }

    public int getBankCount() {
        return bankCount;
    }

    public int getRomSize() {
        return romData.length;
    }

    public String getTitle() {
        return title;
    }

    public int getCartridgeType() {
        return cartridgeType;
    }

    public int getRomSizeCode() {
        return romSizeCode;
    }

    public int getBankSize() {
        return BANK_SIZE;
    }

    /**
     * Descripción legible del tipo de cartucho.
     */
    public String getCartridgeTypeDescription() {
        return switch (cartridgeType) {
            case 0x00 -> "ROM ONLY";
            case 0x01 -> "MBC1";
            case 0x02 -> "MBC1+RAM";
            case 0x03 -> "MBC1+RAM+BATTERY";
            case 0x05 -> "MBC2";
            case 0x06 -> "MBC2+BATTERY";
            case 0x08 -> "ROM+RAM";
            case 0x09 -> "ROM+RAM+BATTERY";
            case 0x19 -> "MBC5";
            case 0x1A -> "MBC5+RAM";
            case 0x1B -> "MBC5+RAM+BATTERY";
            case 0x1C -> "MBC5+RUMBLE";
            case 0x1D -> "MBC5+RUMBLE+RAM";
            case 0x1E -> "MBC5+RUMBLE+RAM+BATTERY";
            case 0x20 -> "MBC6";
            case 0xFC -> "POCKET CAMERA";
            case 0xFD -> "BANDAI TAMA5";
            case 0xFE -> "HuC3";
            case 0xFF -> "HuC1+RAM+BATTERY";
            default -> "Desconocido (0x" + String.format("%02X", cartridgeType) + ")";
        };
    }
}
