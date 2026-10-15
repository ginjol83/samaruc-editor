package com.retroeditor.service.atari.atr;

/**
 * Entrada del directorio DOS 2.0S tal y como aparece en una imagen ATR.
 *
 * @param index        posicion dentro del directorio (0..63)
 * @param rawName      nombre de 8 caracteres sin recortar
 * @param rawExtension extension de 3 caracteres sin recortar
 * @param sectorCount  numero de sectores ocupados
 * @param startSector  primer sector de la cadena de datos
 * @param deleted      si la entrada tiene la marca de borrado
 */
public record AtrFileEntry(
        int index,
        String rawName,
        String rawExtension,
        int sectorCount,
        int startSector,
        boolean deleted) {

    public String name() {
        return rawName.trim();
    }

    public String extension() {
        return rawExtension.trim();
    }

    /** Nombre completo normalizado, por ejemplo {@code AUTORUN.BAS}. */
    public String fullName() {
        String ext = extension();
        return ext.isEmpty() ? name() : name() + "." + ext;
    }
}
