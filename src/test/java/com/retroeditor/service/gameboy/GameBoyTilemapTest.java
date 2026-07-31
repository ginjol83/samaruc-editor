package com.retroeditor.service.gameboy;

import java.awt.image.BufferedImage;

/**
 * Test simple para verificar la generación de tilemaps.
 * Genera un PNG de ejemplo y lo convierte a tilemap GBDK-2020.
 */
public class GameBoyTilemapTest {
    public static void main(String[] args) throws Exception {
        // Crear imagen de prueba 160x144 con patrón simple
        BufferedImage testImage = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
        
        // Llenar con patrón alternado (blanco/negro por tiles)
        for (int y = 0; y < 144; y++) {
            for (int x = 0; x < 160; x++) {
                int tileX = x / 8;
                int tileY = y / 8;
                int color = ((tileX + tileY) % 2 == 0) ? 0xFFFFFF : 0x000000;
                testImage.setRGB(x, y, color);
            }
        }

        // Convertir a tilemap
        PngToGameBoyTilemapConverter converter = new PngToGameBoyTilemapConverter();
        GameBoyTilemapResult result = converter.convertImageToTilemap(testImage, 0, false);

        // Generar código
        GameBoyTilemapFormatter formatter = new GameBoyTilemapFormatter();
        String headerCode = formatter.generateHeaderFile("test_tilemap");
        String sourceCode = formatter.generateSourceFile("test_tilemap", result);

        // Mostrar resultado
        System.out.println("=== HEADER FILE ===");
        System.out.println(headerCode);
        System.out.println("\n=== SOURCE FILE (primeros 100 líneas) ===");
        String[] lines = sourceCode.split("\n");
        for (int i = 0; i < Math.min(100, lines.length); i++) {
            System.out.println(lines[i]);
        }
        System.out.println("\n=== STATISTICS ===");
        System.out.println("Map size: " + result.getMapDataSize() + " bytes");
        System.out.println("Tile count: " + result.getTileCount() + " unique tiles");
        System.out.println("Tile data size: " + result.getTileDataSize() + " bytes");
        System.out.println("Total size: " + (result.getMapDataSize() + result.getTileDataSize()) + " bytes");
    }
}
