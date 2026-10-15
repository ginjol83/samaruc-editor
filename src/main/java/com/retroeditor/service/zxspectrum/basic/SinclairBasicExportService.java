package com.retroeditor.service.zxspectrum.basic;

import com.retroeditor.service.basic.preprocessor.BasicIncludeException;
import com.retroeditor.service.basic.preprocessor.BasicPreprocessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Convierte una fuente Sinclair BASIC en texto a un fichero de cinta {@code .tap}
 * cargable por la ROM del ZX Spectrum (y por tanto por JSpeccy).
 *
 * <p>Antes de tokenizar se expanden las directivas de inclusion
 * ({@code REM INCLUDE "sub.bas"}) con el mismo preprocesador que usa la ruta Atari,
 * de modo que un proyecto Spectrum dividido en varios {@code .bas} se agrupa en un
 * unico programa. Samaruc BASIC ({@code REM @samaruc on}, antes {@code @modern on})
 * es especifico de Atari y no se aplica aqui.</p>
 */
public final class SinclairBasicExportService {

    public static final String DEFAULT_ENTRY_NAME = "PROGRAM";

    /** Tokeniza {@code sourceBas} y escribe el {@code .tap} en {@code outputTap}. */
    public Path exportToTap(Path sourceBas, Path outputTap, String entryName)
            throws IOException, SinclairBasicSyntaxException {
        if (sourceBas == null) {
            throw new IllegalArgumentException("La ruta de origen es obligatoria");
        }
        if (outputTap == null) {
            throw new IllegalArgumentException("La ruta de destino es obligatoria");
        }
        String source = preprocess(
                Files.readString(sourceBas, StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n'),
                sourceBas);
        byte[] tap = buildTap(source, entryName);
        Path parent = outputTap.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputTap, tap);
        return outputTap;
    }

    /** Variante en memoria, util para pruebas y previsualizaciones. */
    public byte[] exportToTapBytes(String source, String entryName) throws SinclairBasicSyntaxException {
        return buildTap(source == null ? "" : source, entryName);
    }

    /** Programa nativo tokenizado en memoria, sin cabecera de cinta. */
    public byte[] tokenizeProgram(String source) throws SinclairBasicSyntaxException {
        return SinclairBasicTokenizer.tokenize(source == null ? "" : source);
    }

    private byte[] buildTap(String source, String entryName) throws SinclairBasicSyntaxException {
        byte[] program = SinclairBasicTokenizer.tokenize(source);
        String name = (entryName == null || entryName.isBlank()) ? DEFAULT_ENTRY_NAME : entryName;
        return SinclairTapWriter.toTap(program, name, firstLineNumber(program));
    }

    private static int firstLineNumber(byte[] program) {
        if (program.length < 2) {
            return SinclairTapWriter.NO_AUTOSTART;
        }
        return ((program[0] & 0xFF) << 8) | (program[1] & 0xFF);
    }

    private static String preprocess(String source, Path sourceBas)
            throws IOException, SinclairBasicSyntaxException {
        try {
            return new BasicPreprocessor(sourceBas.getParent()).processSource(source, sourceBas);
        } catch (BasicIncludeException ex) {
            throw new SinclairBasicSyntaxException(ex.getMessage(), ex.getLine(), 0);
        }
    }
}
