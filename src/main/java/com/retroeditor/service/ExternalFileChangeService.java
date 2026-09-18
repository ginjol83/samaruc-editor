package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * Detecta cambios externos en archivos abiertos en el editor: cuando el archivo
 * en disco se modifica fuera de Samaruc (p. ej. por opencode u otra herramienta)
 * mientras la pestaña sigue abierta con contenido previo.
 *
 * <p>La detección se basa en la huella SHA-256 del contenido decodificado: cada
 * pestaña registra la huella de la última versión sincronizada (al abrir y al
 * guardar) y, al comparar, se decodifica el disco con el mismo orden de charsets
 * de {@link com.retroeditor.model.EditorModel}.</p>
 */
public final class ExternalFileChangeService {

    private ExternalFileChangeService() {
    }

    private static final List<Charset> TEXT_FALLBACK_CHARSETS = Arrays.asList(
        StandardCharsets.UTF_8,
        Charset.forName("windows-1252"),
        StandardCharsets.ISO_8859_1
    );

    /**
     * Lee el contenido actual del archivo en disco y lo decodifica como texto
     * (mismo fallback de charsets que {@code EditorModel}).
     *
     * @return el contenido, o {@code null} si no se puede leer/decodificar.
     */
    public static String readCurrentContent(File file) {
        byte[] bytes = readAllBytesSafe(file);
        if (bytes == null) return null;
        return decodeWithFallback(bytes);
    }

    /**
     * Huella SHA-256 (hex) del contenido textual.
     */
    public static String fingerprint(String content) {
        if (content == null) return "null";
        return fingerprint(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Huella SHA-256 (hex) de los bytes en bruto.
     */
    public static String fingerprint(byte[] bytes) {
        if (bytes == null) return "null";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0x0F, 16));
                sb.append(Character.forDigit(b & 0x0F, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            return bytes.length + ":" + java.util.Arrays.hashCode(bytes);
        }
    }

    /**
     * Huella SHA-256 (hex) del contenido actual del archivo en disco, calculada
     * sobre los bytes en bruto. Devuelve {@code null} si el archivo no existe o
     * no es legible.
     */
    public static String fingerprintOfDisk(File file) {
        byte[] bytes = readAllBytesSafe(file);
        if (bytes == null) return null;
        return fingerprint(bytes);
    }

    /**
     * True si el archivo en disco difiere de la huella registrada como última
     * versión sincronizada. Si no hay huella registrada o el archivo no es
     * legible, devuelve {@code false} (no se puede decidir y no conviene bloquear
     * la compilación).
     *
     * <p>Se comparan tanto la decodificación por fallback (UTF-8/windows-1252/
     * ISO-8859-1) como la de byte único, para cubrir también los archivos
     * forzados a abrirse «as single byte text».</p>
     */
    public static boolean hasExternalChange(File file, String registeredFingerprint) {
        if (file == null || !file.isFile() || registeredFingerprint == null || registeredFingerprint.isEmpty()) {
            return false;
        }
        byte[] bytes = readAllBytesSafe(file);
        if (bytes == null) return false;

        if (fingerprint(decodeWithFallback(bytes)).equals(registeredFingerprint)) {
            return false;
        }
        String singleByteText = new String(bytes, StandardCharsets.ISO_8859_1);
        if (fingerprint(singleByteText).equals(registeredFingerprint)) {
            return false;
        }
        return true;
    }

    private static byte[] readAllBytesSafe(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    private static String decodeWithFallback(byte[] bytes) {
        for (Charset charset : TEXT_FALLBACK_CHARSETS) {
            try {
                return decodeStrict(bytes, charset);
            } catch (CharacterCodingException ignored) {
                // Probar siguiente charset del fallback
            }
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer chars = decoder.decode(ByteBuffer.wrap(bytes));
        return chars.toString();
    }
}