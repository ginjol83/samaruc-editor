package com.retroeditor.service.atari.xex;

import com.retroeditor.service.atari.AtariBasicExportService;
import com.retroeditor.service.atari.basic.AtariBasicSyntaxException;
import com.retroeditor.service.atari.basic.AtariBasicVariant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exporta una fuente Atari BASIC a un XEX autoarrancable. Reutiliza el
 * tokenizador y el preprocesador del exportador BASIC y delega el empaquetado en
 * {@link AtariBasicXexPackager}.
 */
public final class AtariBasicXexExportService {

    private final AtariBasicExportService basicExportService;
    private final AtariBasicXexPackager packager;

    public AtariBasicXexExportService() {
        this(new AtariBasicExportService(), new AtariBasicXexPackager());
    }

    AtariBasicXexExportService(AtariBasicExportService basicExportService, AtariBasicXexPackager packager) {
        this.basicExportService = basicExportService;
        this.packager = packager;
    }

    public Path exportToXex(Path sourceBas, Path outputXex, AtariBasicVariant variant)
            throws IOException, AtariBasicSyntaxException, XexException {
        if (sourceBas == null) {
            throw new IllegalArgumentException("La ruta de origen es obligatoria");
        }
        if (outputXex == null) {
            throw new IllegalArgumentException("La ruta de destino es obligatoria");
        }
        byte[] xex = packager.pack(basicExportService.tokenizeSource(sourceBas, variant));
        Path parent = outputXex.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputXex, xex);
        return outputXex;
    }

    /** Variante en memoria, util para pruebas y previsualizaciones. */
    public byte[] exportToXexBytes(String source, AtariBasicVariant variant)
            throws AtariBasicSyntaxException, XexException {
        return packager.pack(basicExportService.exportToBasicBytes(source, variant));
    }
}
