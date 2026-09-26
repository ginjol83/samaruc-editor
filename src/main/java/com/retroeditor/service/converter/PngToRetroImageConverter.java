package com.retroeditor.service.converter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;

/**
 * Convierte una imagen PNG al formato de datos de CPC, C64, Master System,
 * Game Gear o Atari 8-bit.
 *
 * El flujo es siempre el mismo: cuantizar a la paleta del modo, reducirla si
 * la maquina solo puede mostrar menos colores a la vez y empaquetar los
 * indices de color segun el layout de memoria de cada modo.
 */
public class PngToRetroImageConverter {
    private static final int MAX_DIMENSION = 1024;

    /**
     * Convierte un archivo PNG.
     *
     * @param pngFile archivo de imagen
     * @param mode modo de video destino
     * @param width ancho de salida en pixeles
     * @param height alto de salida en pixeles
     * @return resultado de la conversion
     * @throws IOException si la imagen no se puede leer
     */
    public RetroImageConversion convert(File pngFile, RetroImageMode mode, int width, int height) throws IOException {
        Objects.requireNonNull(pngFile, "pngFile");
        if (!pngFile.isFile()) {
            throw new IllegalArgumentException("El archivo no existe: " + pngFile);
        }
        BufferedImage image;
        try {
            image = ImageIO.read(pngFile);
        } catch (IOException ex) {
            throw new IOException("No se puede leer la imagen: " + pngFile.getName(), ex);
        }
        if (image == null) {
            throw new IOException("El archivo no parece una imagen valida: " + pngFile.getName());
        }
        return convert(image, mode, width, height);
    }

    /**
     * Convierte una imagen ya cargada en memoria.
     *
     * @param image imagen origen
     * @param mode modo de video destino
     * @param width ancho de salida en pixeles
     * @param height alto de salida en pixeles
     * @return resultado de la conversion
     */
    public RetroImageConversion convert(BufferedImage image, RetroImageMode mode, int width, int height) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(mode, "mode");

        int[] argb = readPixels(image, width, height);
        return convertPixels(argb, mode, width, height);
    }

    /**
     * Convierte un array de pixeles ARGB (fila a fila). Util para tests y
     * para reutilizar la conversion sobre imagenes ya cargadas.
     */
    public RetroImageConversion convertPixels(int[] argbPixels, RetroImageMode mode, int width, int height) {
        Objects.requireNonNull(argbPixels, "argbPixels");
        Objects.requireNonNull(mode, "mode");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Las dimensiones deben ser mayores que 0.");
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IllegalArgumentException("Las dimensiones no pueden superar " + MAX_DIMENSION + " px.");
        }
        mode.validateDimensions(width, height);
        if (argbPixels.length != width * height) {
            throw new IllegalArgumentException("El numero de pixeles no coincide con las dimensiones.");
        }

        int[] hardwarePalette = mode.palette();
        int[] quantized = RetroQuantizer.quantize(argbPixels, hardwarePalette, mode.hasTransparentFirstColor());

        int[] usedIndices = RetroQuantizer.mostUsedOrdered(quantized, hardwarePalette.length, mode.getMaxColors());
        if (mode.hasTransparentFirstColor() && usedIndices.length == mode.getMaxColors()
                && !contains(usedIndices, 0)) {
            // Si la paleta se ha tenido que reducir, el color 0 (el transparente)
            // tiene que seguir dentro aunque sea el menos usado de la imagen.
            usedIndices[usedIndices.length - 1] = 0;
        }
        Arrays.sort(usedIndices);

        int[] effectivePalette = RetroQuantizer.selectColors(hardwarePalette, usedIndices);
        int[] indices = RetroQuantizer.remap(quantized, usedIndices);
        List<RetroImageLayer> layers =
                RetroImagePacker.pack(mode, indices, effectivePalette, usedIndices, width, height);
        return new RetroImageConversion(mode, width, height, layers, effectivePalette, usedIndices,
                mode.cramBytes(effectivePalette), indices);
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lee los pixeles de la imagen llevandolos a las dimensiones pedidas con
     * escalado del vecino mas cercano, como hacen el resto de conversores del
     * IDE. Si la imagen es mas pequena, los pixeles que faltan se rellenan con
     * el color del borde.
     */
    private int[] readPixels(BufferedImage image, int width, int height) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sourceY = Math.min(imageHeight - 1, (int) ((y + 0.5) * imageHeight / height));
            for (int x = 0; x < width; x++) {
                int sourceX = Math.min(imageWidth - 1, (int) ((x + 0.5) * imageWidth / width));
                argb[y * width + x] = image.getRGB(sourceX, sourceY);
            }
        }
        return argb;
    }
}
