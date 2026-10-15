package com.retroeditor.service.basic.preprocessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expande directivas de inclusion de un fichero BASIC dentro de otro, en memoria,
 * antes de compilar/tokenizar, de modo que un proyecto pueda subdividirse en varios
 * ficheros .bas. El estandar BASIC no define import/includes, asi que se usa una
 * directiva propia con la forma:
 *
 * <pre>
 *   10 REM INCLUDE "sub.bas"
 *   10 #INCLUDE "sub.bas"
 *   10 INCLUDE "sub.bas"
 *   ' INCLUDE "sub.bas"
 * </pre>
 *
 * Las rutas se resuelven relativas al fichero que emite la directiva. Se detectan
 * ciclos y ficheros inexistentes. Ninguna linea de la directiva se conserva en la
 * salida; el contenido del fichero incluido se inserta en su lugar.
 *
 * <p>La directiva puede declarar ademas un rango de numeracion reservado para el
 * fichero incluido, con la forma {@code MIN-MAX}:</p>
 *
 * <pre>
 *   100 REM INCLUDE "rooms.bas" 6000-6999
 *   100 #INCLUDE "rooms.bas" 6000-6999
 * </pre>
 *
 * <p>Cuando el rango esta presente se comprueba que todas las lineas numeradas del
 * fichero incluido caen dentro de el y que los rangos de distintos ficheros no se
 * solapan. Ademas, dos ficheros distintos no pueden definir el mismo numero de
 * linea. Asi se puede subdividir un programa en varios .bas sin que el tokenizador
 * falle despues por "line number already in use".</p>
 */
public final class BasicPreprocessor {

    private static final String DIRECTIVE_PREFIX = "^(?:\\d+\\s*)?(?:REM\\s+|['#]\\s*)?";
    private static final String RANGE_SUFFIX = "(?:\\s+(\\d+)\\s*-\\s*(\\d+))?";

    private static final Pattern QUOTED_INCLUDE_PATTERN = Pattern.compile(
            DIRECTIVE_PREFIX + "(?i:INCLUDE)\\s+[\"']([^\"']+)[\"']" + RANGE_SUFFIX + "\\s*$");

    private static final Pattern BARE_INCLUDE_PATTERN = Pattern.compile(
            DIRECTIVE_PREFIX + "(?i:INCLUDE)\\s+(.+?)\\s*$");

    private static final Pattern TRAILING_RANGE_PATTERN =
            Pattern.compile("^(.*?)\\s+(\\d+)\\s*-\\s*(\\d+)$");

    private static final Pattern LEADING_LINE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d+)");

    private final Path baseDir;

    public BasicPreprocessor(Path baseDir) {
        this.baseDir = baseDir == null ? Paths.get("") : baseDir.toAbsolutePath().normalize();
    }

    /**
     * Expande las directivas de {@code source}. La resolucion de rutas relativas
     * parte de {@code sourcePath} (el propio fichero que contiene la fuente); si es
     * null se usa el directori base del preprocesador.
     */
    public String processSource(String source, Path sourcePath) throws IOException, BasicIncludeException {
        Objects.requireNonNull(source, "source");
        Path root = sourcePath != null && sourcePath.getParent() != null
                ? sourcePath.getParent().toAbsolutePath().normalize()
                : baseDir;
        Path origin = sourcePath != null ? sourcePath.toAbsolutePath().normalize() : null;
        State state = new State();
        String normalized = normalize(source);
        boolean hasTrailingNewline = normalized.endsWith("\n");
        String content = normalized;
        if (hasTrailingNewline) {
            content = content.substring(0, content.length() - 1);
        }
        StringBuilder out = new StringBuilder();
        if (content.isEmpty()) {
            return hasTrailingNewline ? "\n" : "";
        }
        String[] srcLines = content.split("\n", -1);
        for (int i = 0; i < srcLines.length; i++) {
            boolean isLast = i == srcLines.length - 1;
            StringBuilder lineOut = new StringBuilder();
            processLineContent(srcLines[i], root, origin, i + 1, null, state, lineOut);
            out.append(lineOut);
            if (!isLast) {
                out.append('\n');
            }
        }
        return hasTrailingNewline ? out + "\n" : out.toString();
    }

    private void processLineContent(String line,
                                    Path dir,
                                    Path originFile,
                                    int lineNumber,
                                    Range requiredRange,
                                    State state,
                                    StringBuilder out)
            throws IOException, BasicIncludeException {
        Include include = detectInclude(line);
        if (include == null) {
            registerNumberedLine(line, originFile, lineNumber, requiredRange, state);
            out.append(line);
            return;
        }
        Path inc = resolve(include.path(), dir);
        if (state.visiting.contains(inc)) {
            throw new BasicIncludeException(lineNumber, "Ciclo de inclusion detectado al incluir: " + include.path());
        }
        if (!Files.isRegularFile(inc)) {
            throw new BasicIncludeException(lineNumber,
                    "Archivo a incluir no encontrado: " + include.path() + " (" + inc + ")");
        }
        Range childRange = buildRange(include, inc, lineNumber);
        if (childRange != null) {
            registerRange(childRange, lineNumber, state);
        }
        state.visiting.add(inc);
        try {
            String content = new String(Files.readAllBytes(inc), StandardCharsets.UTF_8);
            String normalizedContent = normalize(content);
            boolean hasTrailingNewline = normalizedContent.endsWith("\n");
            String inner = normalizedContent;
            if (hasTrailingNewline) {
                inner = inner.substring(0, inner.length() - 1);
            }
            if (inner.isEmpty()) {
                return;
            }
            String[] lines = inner.split("\n", -1);
            Path incDir = inc.getParent() != null ? inc.getParent().toAbsolutePath().normalize() : dir;
            Path incOrigin = inc.toAbsolutePath().normalize();
            for (int i = 0; i < lines.length; i++) {
                processLineContent(lines[i], incDir, incOrigin, i + 1, childRange, state, out);
                if (i < lines.length - 1) {
                    out.append('\n');
                }
            }
        } finally {
            state.visiting.remove(inc);
        }
    }

    private Include detectInclude(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        Matcher quoted = QUOTED_INCLUDE_PATTERN.matcher(trimmed);
        if (quoted.matches()) {
            return new Include(quoted.group(1), parseIntOrNull(quoted.group(2)), parseIntOrNull(quoted.group(3)));
        }
        Matcher bare = BARE_INCLUDE_PATTERN.matcher(trimmed);
        if (bare.matches()) {
            String rest = bare.group(1).trim().replaceAll("[\"']", "");
            Matcher trailing = TRAILING_RANGE_PATTERN.matcher(rest);
            if (trailing.matches()) {
                return new Include(trailing.group(1).trim(),
                        parseIntOrNull(trailing.group(2)), parseIntOrNull(trailing.group(3)));
            }
            return new Include(rest, null, null);
        }
        return null;
    }

    /**
     * Crea y valida el rango reservado declarado en la directiva, o {@code null}
     * si la directiva no declara rango.
     */
    private Range buildRange(Include include, Path inc, int directiveLine) throws BasicIncludeException {
        if (include.min() == null || include.max() == null) {
            return null;
        }
        int min = include.min();
        int max = include.max();
        if (min < 0 || max < 0 || min > max) {
            throw new BasicIncludeException(directiveLine, "Rango reservado invalido en include "
                    + include.path() + ": " + min + "-" + max);
        }
        return new Range(min, max, inc);
    }

    private void registerRange(Range range, int directiveLine, State state) throws BasicIncludeException {
        for (Range other : state.ranges) {
            boolean overlaps = range.min() <= other.max() && other.min() <= range.max();
            if (overlaps) {
                throw new BasicIncludeException(directiveLine, "Rangos reservados solapados: "
                        + fileName(range.file()) + " " + range.min() + "-" + range.max()
                        + " y " + fileName(other.file()) + " " + other.min() + "-" + other.max());
            }
        }
        state.ranges.add(range);
    }

    private void registerNumberedLine(String line,
                                      Path originFile,
                                      int lineNumber,
                                      Range requiredRange,
                                      State state)
            throws BasicIncludeException {
        Integer number = leadingLineNumber(line);
        if (number == null) {
            return;
        }
        if (requiredRange != null && (number < requiredRange.min() || number > requiredRange.max())) {
            throw new BasicIncludeException(lineNumber, "Numero de linea " + number
                    + " fuera del rango reservado " + requiredRange.min() + "-" + requiredRange.max()
                    + " de " + fileName(requiredRange.file()));
        }
        Path previous = state.lineOrigins.putIfAbsent(number, originFile);
        if (previous != null && !Objects.equals(previous, originFile)) {
            throw new BasicIncludeException(lineNumber, "Numero de linea duplicado " + number
                    + ": definido en " + fileName(previous) + " y en " + fileName(originFile));
        }
    }

    private static Integer leadingLineNumber(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = LEADING_LINE_NUMBER_PATTERN.matcher(line);
        return matcher.find() ? parseIntOrNull(matcher.group(1)) : null;
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String fileName(Path file) {
        return file != null && file.getFileName() != null ? file.getFileName().toString() : "<principal>";
    }

    private Path resolve(String include, Path dir) {
        Path p = Paths.get(include.trim());
        if (p.isAbsolute()) {
            return p.normalize();
        }
        return dir.resolve(p).normalize();
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private record Include(String path, Integer min, Integer max) {
    }

    private record Range(int min, int max, Path file) {
    }

    private static final class State {
        private final Set<Path> visiting = new LinkedHashSet<>();
        private final List<Range> ranges = new ArrayList<>();
        private final Map<Integer, Path> lineOrigins = new HashMap<>();
    }
}
