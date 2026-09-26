package com.retroeditor.service.converter;

import java.util.Objects;

/**
 * Capa de datos generada por el conversor (un array de bytes destined a
 * escribirse en la memoria de video o de datos de la maquina).
 *
 * @param name nombre logico de la capa (bitmap, colors, chars...)
 * @param description descripcion para el comentario del codigo C
 * @param data bytes de la capa
 */
public record RetroImageLayer(String name, String description, byte[] data) {
    public RetroImageLayer {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(data, "data");
    }
}
