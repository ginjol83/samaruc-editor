package com.retroeditor.service.tilemap;

import java.util.List;
import java.util.Objects;

public final class TilemapNativeDataCodec {
    private TilemapNativeDataCodec() {
    }

    public static byte[] encode(TilemapPlatform platform, int width, int height, List<Integer> pixels) {
        Objects.requireNonNull(platform, "platform");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Las dimensiones del tile deben ser positivas.");
        }
        if (pixels == null || pixels.size() != width * height) {
            throw new IllegalArgumentException("El número de píxeles no coincide con las dimensiones del tile.");
        }

        int bytesPerRow = bytesPerRow(platform, width);
        byte[] encoded = new byte[bytesPerRow * height];
        int bitsPerPixel = platform.getBitsPerPixel();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Integer pixel = pixels.get(y * width + x);
                if (pixel == null) {
                    throw new IllegalArgumentException("El tile contiene un píxel nulo.");
                }
                int color = colorIndex(pixel, bitsPerPixel);
                int pixelOffset = x;
                int bitOffset = switch (bitsPerPixel) {
                    case 1 -> 7 - (pixelOffset % 8);
                    case 2 -> 6 - (pixelOffset % 4) * 2;
                    case 4 -> 4 - (pixelOffset % 2) * 4;
                    default -> throw new IllegalArgumentException("Bits por píxel no soportados: " + bitsPerPixel);
                };
                int byteIndex = y * bytesPerRow + pixelOffset * bitsPerPixel / 8;
                encoded[byteIndex] |= (byte) ((color & ((1 << bitsPerPixel) - 1)) << bitOffset);
            }
        }
        return encoded;
    }

    public static int expectedByteCount(TilemapPlatform platform, int width, int height) {
        Objects.requireNonNull(platform, "platform");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Las dimensiones del tile deben ser positivas.");
        }
        return bytesPerRow(platform, width) * height;
    }

    private static int bytesPerRow(TilemapPlatform platform, int width) {
        return (width * platform.getBitsPerPixel() + 7) / 8;
    }

    private static int colorIndex(int argb, int bitsPerPixel) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < 128) {
            return 0;
        }
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        int luminance = (int) (red * 0.299 + green * 0.587 + blue * 0.114);
        if (bitsPerPixel == 1) {
            return luminance >= 128 ? 1 : 0;
        }
        if (luminance > 192) {
            return 0;
        }
        if (luminance > 128) {
            return 1;
        }
        if (luminance > 64) {
            return 2;
        }
        return 3;
    }
}
