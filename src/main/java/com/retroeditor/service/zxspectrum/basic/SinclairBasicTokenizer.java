package com.retroeditor.service.zxspectrum.basic;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Convierte un listado Sinclair BASIC en texto al programa tokenizado nativo del
 * ZX Spectrum (el mismo formato que almacena la ROM en memoria y que se carga
 * desde cinta/{@code .tap}).
 *
 * <p>El algoritmo es una adaptacion del tokenizador de referencia
 * {@code zmakebas} de Russell Marks. Los puntos clave del formato son:</p>
 * <ul>
 *   <li>Cada linea empieza con el numero de linea en 2 bytes big-endian, seguido
 *       de la longitud de la linea en 2 bytes little-endian (que incluye la
 *       cabecera de 4 bytes y el terminador {@code 0x0D}), el cuerpo tokenizado y
 *       el {@code 0x0D} final.</li>
 *   <li>Las palabras clave se sustituyen por su token ({@code 0xA5}-{@code 0xFF})
 *       case-insensitive, respetando limites de palabra.</li>
 *   <li>Los numeros se guardan como su texto ASCII seguido de {@code 0x0E} y su
 *       representacion de 5 bytes (entero corto o coma flotante).</li>
 *   <li>Los espacios fuera de cadenas y de {@code REM} no se almacenan; lo que
 *       sigue a {@code REM} se copia literalmente.</li>
 * </ul>
 *
 * <p>Limitaciones del MVP: requiere numeros de linea estrictamente crecientes,
 * no soporta el modo etiquetas ni las secuencias de escape de {@code zmakebas}
 * ({@code \a}, {@code \{n}}, graficos de bloque) ni la marca de {@code DEF FN}. Las
 * palabras clave deben ir separadas de los identificadores por un caracter no
 * alfabetico.</p>
 */
public final class SinclairBasicTokenizer {

    private static final int MAX_LINE_NUMBER = 9999;
    private static final int EOL = 0x0D;
    private static final int NUMBER_MARKER = 0x0E;

    private SinclairBasicTokenizer() {
    }

    /** Tokeniza {@code source} y devuelve el programa nativo (sin cabecera de cinta). */
    public static byte[] tokenize(String source) throws SinclairBasicSyntaxException {
        if (source == null) {
            return new byte[0];
        }
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);

        ByteArrayOutputStream program = new ByteArrayOutputStream();
        int lastLine = -1;

        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            int textLine = index + 1;
            if (raw.strip().isEmpty() || raw.charAt(0) == '#') {
                continue;
            }

            int cursor = 0;
            while (cursor < raw.length() && raw.charAt(cursor) == ' ') {
                cursor++;
            }
            if (cursor >= raw.length() || !isAsciiDigit(raw.charAt(cursor))) {
                throw new SinclairBasicSyntaxException("missing line number", textLine, cursor + 1);
            }
            int numberStart = cursor;
            while (cursor < raw.length() && isAsciiDigit(raw.charAt(cursor))) {
                cursor++;
            }
            int lineNumber;
            try {
                lineNumber = Integer.parseInt(raw.substring(numberStart, cursor));
            } catch (NumberFormatException ex) {
                throw new SinclairBasicSyntaxException("line number out of range", textLine, numberStart + 1);
            }
            if (lineNumber <= lastLine) {
                throw new SinclairBasicSyntaxException(
                        "line number not greater than previous one", textLine, numberStart + 1);
            }
            if (lineNumber < 0 || lineNumber > MAX_LINE_NUMBER) {
                throw new SinclairBasicSyntaxException("line number out of range", textLine, numberStart + 1);
            }
            lastLine = lineNumber;

            String body = raw.substring(cursor);
            byte[] encoded = encodeLine(body, textLine);
            int lineLength = encoded.length + 1;
            if (lineLength > 0xFFFF) {
                throw new SinclairBasicSyntaxException("line too long", textLine, 1);
            }
            program.write((lineNumber >> 8) & 0xFF);
            program.write(lineNumber & 0xFF);
            program.write(lineLength & 0xFF);
            program.write((lineLength >> 8) & 0xFF);
            program.write(encoded, 0, encoded.length);
            program.write(EOL);
        }
        return program.toByteArray();
    }

    private static byte[] encodeLine(String body, int textLine) throws SinclairBasicSyntaxException {
        char[] work = body.toCharArray();
        char[] lower = body.toLowerCase(java.util.Locale.ROOT).toCharArray();
        blankQuoted(lower);

        int remAt = findRem(lower);
        int searchLimit = remAt >= 0 ? remAt : lower.length;
        if (remAt >= 0) {
            work[remAt] = (char) SinclairBasicTokens.REM;
            work[remAt + 1] = 1;
            work[remAt + 2] = 1;
            if (remAt + 3 < work.length && work[remAt + 3] == ' ') {
                work[remAt + 3] = 1;
            }
        }

        applyTokens(work, lower, searchLimit);
        return emit(work, textLine);
    }

    private static void blankQuoted(char[] lower) {
        boolean inQuotes = false;
        for (int i = 0; i < lower.length; i++) {
            char c = lower[i];
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes) {
                lower[i] = ' ';
            }
        }
    }

    private static int findRem(char[] lower) {
        for (int i = 0; i + 2 < lower.length; i++) {
            if (lower[i] == 'r' && lower[i + 1] == 'e' && lower[i + 2] == 'm'
                    && (i == 0 || !isAsciiAlpha(lower[i - 1]))
                    && (i + 3 >= lower.length || !isAsciiAlpha(lower[i + 3]))) {
                return i;
            }
        }
        return -1;
    }

    private static void applyTokens(char[] work, char[] lower, int searchLimit) {
        List<SinclairBasicTokens.Keyword> keywords = SinclairBasicTokens.orderedKeywords();
        for (SinclairBasicTokens.Keyword keyword : keywords) {
            String text = keyword.text();
            int tokLen = text.length();
            int at = 0;
            while ((at = indexOf(lower, text, at, searchLimit)) >= 0) {
                if (boundaryOk(lower, text, at, searchLimit)) {
                    int end = at + tokLen;
                    for (int k = 1; k < tokLen; k++) {
                        work[at + k] = 1;
                        lower[at + k] = 1;
                    }
                    while (end < work.length && work[end] == ' ') {
                        work[end] = 1;
                        end++;
                    }
                    if (keyword.value() == SinclairBasicTokens.BIN) {
                        work[at] = 1;
                        lower[at] = 1;
                        work[end - 1] = (char) SinclairBasicTokens.BIN;
                        lower[end - 1] = (char) SinclairBasicTokens.BIN;
                    } else {
                        work[at] = (char) keyword.value();
                        lower[at] = (char) keyword.value();
                    }
                }
                at += tokLen;
            }
        }
    }

    private static boolean boundaryOk(char[] lower, String text, int at, int searchLimit) {
        if (text.charAt(0) == '<' || (text.length() > 1 && text.charAt(1) == '=')) {
            return true;
        }
        boolean leftBoundary = at == 0 || !isAsciiAlpha(lower[at - 1]);
        int after = at + text.length();
        boolean rightBoundary = after >= searchLimit || !isAsciiAlpha(lower[after]);
        return leftBoundary && rightBoundary;
    }

    private static int indexOf(char[] haystack, String needle, int from, int limit) {
        int n = needle.length();
        for (int i = from; i + n <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < n; j++) {
                if (haystack[i + j] != needle.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] emit(char[] work, int textLine) throws SinclairBasicSyntaxException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean inQuotes = false;
        boolean inRem = false;
        int i = 0;
        while (i < work.length) {
            char c = work[i];
            if (c == '"') {
                inQuotes = !inQuotes;
            }
            if (c == 1 || c == '\t') {
                i++;
                continue;
            }
            if (c == ' ' && !inQuotes && !inRem) {
                i++;
                continue;
            }
            if (c == SinclairBasicTokens.REM) {
                inRem = true;
                out.write(c);
                i++;
                continue;
            }

            boolean prevAlpha = i > 0 && isAsciiAlpha(work[i - 1]);
            char next1 = i + 1 < work.length ? work[i + 1] : 0;
            char next2 = i + 2 < work.length ? work[i + 2] : 0;
            boolean numberStart = !inRem && !inQuotes && !prevAlpha
                    && (isAsciiDigit(c)
                        || ((c == '-' || c == '+' || c == '.') && isAsciiDigit(next1))
                        || ((c == '-' || c == '+') && next1 == '.' && isAsciiDigit(next2)));

            if (numberStart) {
                int end;
                double value;
                if (i > 0 && work[i - 1] == SinclairBasicTokens.BIN) {
                    BinLex bin = grokBinary(work, i, textLine);
                    end = bin.end();
                    value = bin.value();
                } else {
                    NumberLex lex = scanNumber(work, i);
                    end = lex.end();
                    value = lex.value();
                }
                for (int k = i; k < end; k++) {
                    out.write((byte) work[k]);
                }
                out.write(NUMBER_MARKER);
                out.writeBytes(zxFloat(value, textLine));
                i = end;
                continue;
            }

            out.write((byte) c);
            i++;
        }
        return out.toByteArray();
    }

    private static NumberLex scanNumber(char[] source, int start) throws SinclairBasicSyntaxException {
        int i = start;
        int n = source.length;
        if (i < n && (source[i] == '+' || source[i] == '-')) {
            i++;
        }
        while (i < n && isAsciiDigit(source[i])) {
            i++;
        }
        if (i < n && source[i] == '.') {
            i++;
            while (i < n && isAsciiDigit(source[i])) {
                i++;
            }
        }
        if (i < n && (source[i] == 'e' || source[i] == 'E')) {
            int j = i + 1;
            if (j < n && (source[j] == '+' || source[j] == '-')) {
                j++;
            }
            if (j < n && isAsciiDigit(source[j])) {
                i = j;
                while (i < n && isAsciiDigit(source[i])) {
                    i++;
                }
            }
        }
        String text = new String(source, start, i - start);
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new SinclairBasicSyntaxException("malformed number: " + text, -1, start + 1);
        }
        return new NumberLex(i, value);
    }

    private static BinLex grokBinary(char[] source, int start, int textLine) throws SinclairBasicSyntaxException {
        int i = start;
        while (i < source.length && (source[i] == ' ' || source[i] == 1)) {
            i++;
        }
        if (i >= source.length || (source[i] != '0' && source[i] != '1')) {
            throw new SinclairBasicSyntaxException("invalid BIN number", textLine, start + 1);
        }
        if (i + 1 < source.length && (source[i + 1] == 'x' || source[i + 1] == 'X')) {
            int j = i + 2;
            long value = 0;
            int digits = 0;
            while (j < source.length && hexDigit(source[j]) >= 0) {
                value = value * 16 + hexDigit(source[j]);
                j++;
                digits++;
            }
            if (digits == 0) {
                throw new SinclairBasicSyntaxException("bad BIN 0x number", textLine, i + 1);
            }
            return new BinLex(j, value);
        }
        long value = 0;
        int j = i;
        while (j < source.length && (source[j] == '0' || source[j] == '1')) {
            value = value * 2 + (source[j] - '0');
            j++;
        }
        return new BinLex(j, value);
    }

    /**
     * Codifica un numero en el formato de 5 bytes del Spectrum. Los enteros entre
     * -65535 y 65535 usan la forma corta ({@code 00 <signo> <low> <high> 00}); el
     * resto, el formato de coma flotante con exponente sesgado a {@code 0x80}.
     */
    static byte[] zxFloat(double number, int textLine) throws SinclairBasicSyntaxException {
        int exponent;
        long mantissa;
        if (!Double.isFinite(number)) {
            throw new SinclairBasicSyntaxException("number out of range", textLine, 1);
        }
        if (number == Math.floor(number) && number >= -65535.0 && number <= 65535.0) {
            long tmp = (long) Math.abs(number);
            exponent = 0;
            mantissa = ((tmp % 256) << 16) | ((tmp >> 8) << 8);
        } else {
            double value = Math.abs(number);
            exponent = 0;
            while (value >= 1.0) {
                value /= 2.0;
                exponent++;
            }
            while (value < 0.5) {
                value *= 2.0;
                exponent--;
            }
            if (exponent < -128 || exponent > 127) {
                throw new SinclairBasicSyntaxException("number out of range", textLine, 1);
            }
            exponent = 128 + exponent;
            value *= 2.0;
            mantissa = 0;
            for (int f = 0; f < 32; f++) {
                mantissa = (mantissa << 1) | ((long) value & 1);
                value -= (long) value;
                value *= 2.0;
            }
            if ((long) value != 0 && mantissa != 0xFFFFFFFFL) {
                mantissa++;
            }
            mantissa &= 0x7FFFFFFFL;
        }
        return new byte[] {
            (byte) exponent,
            (byte) ((mantissa >> 24) & 0xFF),
            (byte) ((mantissa >> 16) & 0xFF),
            (byte) ((mantissa >> 8) & 0xFF),
            (byte) (mantissa & 0xFF)
        };
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private record NumberLex(int end, double value) {
    }

    private record BinLex(int end, double value) {
    }
}
