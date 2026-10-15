package com.retroeditor.service.atari;

import com.retroeditor.service.atari.atr.AtrImageWriter;
import com.retroeditor.service.atari.basic.AtariBasicSyntaxException;
import com.retroeditor.service.atari.basic.AtariBasicTokenizer;
import com.retroeditor.service.atari.basic.AtariBasicVariant;
import com.retroeditor.service.basic.samaruc.SamarucBasicException;
import com.retroeditor.service.basic.samaruc.SamarucBasicTranspiler;
import com.retroeditor.service.basic.preprocessor.BasicIncludeException;
import com.retroeditor.service.basic.preprocessor.BasicPreprocessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Convierte una fuente Atari BASIC en texto al programa tokenizado nativo. Puede
 * escribirse como imagen BASIC ({@code .bas}), que Altirra carga y ejecuta
 * directamente, o empaquetarse en una imagen de disco ATR DOS 2.0S con el
 * programa como fichero (para distribucion).
 */
public final class AtariBasicExportService {

    public static final String DEFAULT_ENTRY_NAME = "AUTORUN.BAS";

    /**
     * Tokeniza {@code sourceBas} y lo deposita como {@code entryName} en una
     * imagen ATR que se escribe en {@code outputAtr}.
     */
    public Path exportToAtr(Path sourceBas, Path outputAtr, String entryName, AtariBasicVariant variant)
            throws IOException, AtariBasicSyntaxException {
        if (sourceBas == null) {
            throw new IllegalArgumentException("La ruta de origen es obligatoria");
        }
        if (outputAtr == null) {
            throw new IllegalArgumentException("La ruta de destino es obligatoria");
        }
        String source = preprocess(
                Files.readString(sourceBas, StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n'),
                sourceBas);
        byte[] image = buildImage(source, entryName, variant);
        Path parent = outputAtr.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputAtr, image);
        return outputAtr;
    }

    /**
     * Tokeniza {@code sourceBas} y escribe el programa tokenizado como imagen
     * BASIC ({@code .bas}) en {@code outputBas}. Ese formato es el que Altirra
     * carga y ejecuta directamente (activa BASIC por si solo).
     */
    public Path exportToBasic(Path sourceBas, Path outputBas, AtariBasicVariant variant)
            throws IOException, AtariBasicSyntaxException {
        if (sourceBas == null) {
            throw new IllegalArgumentException("La ruta de origen es obligatoria");
        }
        if (outputBas == null) {
            throw new IllegalArgumentException("La ruta de destino es obligatoria");
        }
        String source = preprocess(
                Files.readString(sourceBas, StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n'),
                sourceBas);
        byte[] tokenized = tokenize(source, variant);
        Path parent = outputBas.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputBas, tokenized);
        return outputBas;
    }

    /** Variante en memoria, util para pruebas y para previsualizaciones. */
    public byte[] exportToAtrBytes(String source, String entryName, AtariBasicVariant variant)
            throws AtariBasicSyntaxException {
        return buildImage(source == null ? "" : source, entryName, variant);
    }

    /** Programa tokenizado en memoria, sin cabecera de disco. */
    public byte[] exportToBasicBytes(String source, AtariBasicVariant variant)
            throws AtariBasicSyntaxException {
        return tokenize(source == null ? "" : source, variant);
    }

    private byte[] buildImage(String source, String entryName, AtariBasicVariant variant)
            throws AtariBasicSyntaxException {
        byte[] tokenized = tokenize(source, variant);
        AtrImageWriter writer = new AtrImageWriter();
        writer.addFile(resolveEntryName(entryName), tokenized);
        return writer.toByteArray();
    }

    private static byte[] tokenize(String source, AtariBasicVariant variant) throws AtariBasicSyntaxException {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        AtariBasicVariant effectiveVariant = variant != null ? variant : AtariBasicVariant.ATARI_BASIC;
        return AtariBasicTokenizer.tokenize(normalized, effectiveVariant);
    }

    private static String resolveEntryName(String entryName) {
        return (entryName == null || entryName.isBlank()) ? DEFAULT_ENTRY_NAME : entryName;
    }

    /**
     * Expande las directivas de inclusion ({@code REM INCLUDE "sub.bas"}) de la
     * fuente antes de tokenizar, de modo que un proyecto subdividido en varios
     * ficheros .bas se agrupe en un unico programa compilable. Cada directiva puede
     * reservar un rango de numeracion para el fichero incluido
     * ({@code REM INCLUDE "sub.bas" 6000-6999}); el preprocesador valida que las
     * lineas caigan dentro del rango y que los rangos no se solapen. Los errores de
     * inclusion (ciclos, fichero inexistente, solapamiento) se propagan como error
     * de sintaxis con la linea de la directiva.
     */
    private static String preprocess(String source, Path sourceBas)
            throws IOException, AtariBasicSyntaxException {
        String expanded;
        try {
            expanded = new BasicPreprocessor(sourceBas.getParent()).processSource(source, sourceBas);
        } catch (BasicIncludeException ex) {
            throw new AtariBasicSyntaxException(ex.getMessage(), ex.getLine(), 0);
        }
        try {
            return SamarucBasicTranspiler.transpile(expanded);
        } catch (SamarucBasicException ex) {
            throw new AtariBasicSyntaxException(ex.getMessage(), ex.getLine(), 0);
        }
    }
}
