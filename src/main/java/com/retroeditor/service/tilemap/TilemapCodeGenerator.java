package com.retroeditor.service.tilemap;

import java.util.Locale;
import java.util.Objects;

public final class TilemapCodeGenerator {
    public String generate(TilemapPlatform platform, int[][] tilemap, boolean[][] collisions) {
        Objects.requireNonNull(platform, "platform");
        validate(tilemap, collisions, platform);

        StringBuilder output = new StringBuilder();
        appendCommonDefinitions(output, platform, tilemap[0].length, tilemap.length);
        appendMap(output, platform, tilemap);

        switch (platform) {
            case ZX_SPECTRUM -> appendZxData(output, tilemap);
            case CPC -> appendCpcData(output, tilemap);
            case MASTER_SYSTEM -> appendSmsData(output, tilemap);
            case GAME_GEAR -> appendGameGearData(output, tilemap);
            case ATARI -> appendAtariData(output, tilemap);
            case C64 -> appendC64Data(output, tilemap);
            case GAMEBOY -> appendGameBoyData(output);
        }

        appendCollisionMatrix(output, collisions);
        return output.toString();
    }

    private void appendCommonDefinitions(StringBuilder output, TilemapPlatform platform, int width, int height) {
        output.append("#define MAP_WIDTH  ").append(width).append('\n');
        output.append("#define MAP_HEIGHT ").append(height).append('\n');
        output.append("#define TILE_BITS ").append(platform.getBitsPerPixel()).append('\n');
        output.append("#define MAX_TILE_INDEX ").append(platform.getMaxTileIndex()).append('\n');
        output.append("#define TILEMAP_PLATFORM_").append(platform.getCodeName()).append(" 1\n\n");
    }

    private void appendMap(StringBuilder output, TilemapPlatform platform, int[][] tilemap) {
        String symbol = platform == TilemapPlatform.C64 ? "screen_matrix" : "level_tilemap";
        appendIntMatrix(output, symbol, tilemap);
    }

    private void appendGameBoyData(StringBuilder output) {
        output.append("#define GB_DMG_PALETTE 1\n\n");
    }

    private void appendZxData(StringBuilder output, int[][] tilemap) {
        output.append("#define ZX_ATTRIBUTE_FLASH 0x01\n");
        output.append("#define ZX_ATTRIBUTE_BRIGHT 0x08\n\n");
        appendZeroByteMatrix(output, "level_attributes", tilemap.length, tilemap[0].length);
    }

    private void appendCpcData(StringBuilder output, int[][] tilemap) {
        output.append("#define CPC_MODE 0\n\n");
        appendZeroByteMatrix(output, "level_palette", tilemap.length, tilemap[0].length);
    }

    private void appendSmsData(StringBuilder output, int[][] tilemap) {
        output.append("#define SMS_TILE_FLAGS 0\n\n");
        appendZeroByteMatrix(output, "level_attributes", tilemap.length, tilemap[0].length);
    }

    private void appendGameGearData(StringBuilder output, int[][] tilemap) {
        output.append("#define GG_VDP_MODE 0\n\n");
        appendZeroByteMatrix(output, "level_attributes", tilemap.length, tilemap[0].length);
    }

    private void appendAtariData(StringBuilder output, int[][] tilemap) {
        output.append("#define ATARI_SCREEN_MODE 0\n\n");
        appendZeroByteMatrix(output, "level_attributes", tilemap.length, tilemap[0].length);
    }

    private void appendC64Data(StringBuilder output, int[][] tilemap) {
        output.append("#define C64_SCREEN_WIDTH ").append(tilemap[0].length).append('\n');
        output.append("#define C64_SCREEN_HEIGHT ").append(tilemap.length).append("\n\n");
        appendZeroByteMatrix(output, "color_ram", tilemap.length, tilemap[0].length);
    }

    private void appendIntMatrix(StringBuilder output, String symbol, int[][] values) {
        output.append("const unsigned char ").append(symbol).append("[MAP_HEIGHT][MAP_WIDTH] = {\n");
        for (int row = 0; row < values.length; row++) {
            output.append("    { ");
            for (int column = 0; column < values[row].length; column++) {
                output.append(String.format(Locale.ROOT, "%3d", values[row][column]));
                if (column < values[row].length - 1) {
                    output.append(", ");
                }
            }
            output.append(" }");
            if (row < values.length - 1) {
                output.append(',');
            }
            output.append('\n');
        }
        output.append("};\n\n");
    }

    private void appendZeroByteMatrix(StringBuilder output, String symbol, int height, int width) {
        output.append("const unsigned char ").append(symbol).append("[MAP_HEIGHT][MAP_WIDTH] = {\n");
        for (int row = 0; row < height; row++) {
            output.append("    { ");
            for (int column = 0; column < width; column++) {
                output.append("0x00");
                if (column < width - 1) {
                    output.append(", ");
                }
            }
            output.append(" }");
            if (row < height - 1) {
                output.append(',');
            }
            output.append('\n');
        }
        output.append("};\n\n");
    }

    private void appendCollisionMatrix(StringBuilder output, boolean[][] collisions) {
        output.append("const unsigned char level_collisions[MAP_HEIGHT][MAP_WIDTH] = {\n");
        for (int row = 0; row < collisions.length; row++) {
            output.append("    { ");
            for (int column = 0; column < collisions[row].length; column++) {
                output.append(collisions[row][column] ? '1' : '0');
                if (column < collisions[row].length - 1) {
                    output.append(", ");
                }
            }
            output.append(" }");
            if (row < collisions.length - 1) {
                output.append(',');
            }
            output.append('\n');
        }
        output.append("};\n");
    }

    private void validate(int[][] tilemap, boolean[][] collisions, TilemapPlatform platform) {
        if (tilemap == null || tilemap.length == 0 || tilemap[0] == null || tilemap[0].length == 0) {
            throw new IllegalArgumentException("El tilemap no puede estar vacío.");
        }
        if (collisions == null || collisions.length != tilemap.length) {
            throw new IllegalArgumentException("Las capas de colisión deben tener las mismas filas que el tilemap.");
        }
        int width = tilemap[0].length;
        for (int row = 0; row < tilemap.length; row++) {
            if (tilemap[row] == null || tilemap[row].length != width) {
                throw new IllegalArgumentException("Todas las filas del tilemap deben tener el mismo ancho.");
            }
            if (collisions[row] == null || collisions[row].length != width) {
                throw new IllegalArgumentException("Las capas de colisión deben tener el mismo tamaño que el tilemap.");
            }
            for (int column = 0; column < width; column++) {
                int tile = tilemap[row][column];
                if (tile < 0 || tile > platform.getMaxTileIndex()) {
                    throw new IllegalArgumentException("El índice de tile debe estar entre 0 y " + platform.getMaxTileIndex() + ".");
                }
            }
        }
    }
}
