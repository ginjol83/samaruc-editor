package com.retroeditor.service.basic.samaruc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preprocesador de "Samaruc BASIC" (antes "BASIC extendido") para el dialecto Atari.
 * Traduce sintaxis extendida (funciones con parametros y valor de retorno,
 * procedimientos y variables locales) a BASIC clasico numerado, de modo que el
 * tokenizador de Atari y el emulador no necesiten saber nada de estas construcciones.
 *
 * <p>El modo se activa con una marca en el fuente (se acepta {@code @modern} como
 * alias por compatibilidad):</p>
 *
 * <pre>
 *   REM @samaruc on
 * </pre>
 *
 * <p>Sintaxis soportada:</p>
 *
 * <pre>
 *   100 D = DIST(0, 0, 3, 4)
 *
 *   1000 FUNCTION DIST(X1, Y1, X2, Y2)
 *   1010 LOCAL DX, DY
 *   1020 DX = X2 - X1
 *   1030 DY = Y2 - Y1
 *   1040 RETURN SQR(DX*DX + DY*DY)
 *   1050 END FUNCTION
 * </pre>
 *
 * <p>Una funcion puede devolver una cadena si su nombre termina en {@code $}
 * ({@code FUNCTION NAME$(N)}); en ese caso {@code RETURN} guarda el resultado en
 * {@code R$}. Un {@code PROCEDURE} no devuelve valor. Los {@code LOCAL} y los
 * parametros son siempre numericos en esta version.</p>
 *
 * <p>La llamada a una funcion solo puede aparecer como el lado derecho completo de
 * una asignacion o como una sentencia suelta; las llamadas anidadas dentro de una
 * expresion o como argumento deben resolverse con una variable temporal.</p>
 *
 * <p>Control estructurado (sin numero de linea de destino explicito). Cada
 * apertura y cierre deben ir en su propia linea numerada y, para reconstruir el
 * flujo, el transpilador usa la linea siguiente:</p>
 *
 * <pre>
 *   100 IF A &gt; 0 THEN
 *   110   PRINT "POSITIVO"
 *   120 ELSE
 *   130   PRINT "NO POSITIVO"
 *   140 END IF
 *
 *   200 WHILE I &lt; 10
 *   210   I = I + 1
 *   220 ENDWHILE        (tambien WEND o END WHILE)
 *
 *   300 REPEAT
 *   310   I = I - 1
 *   320 UNTIL I = 0
 *
 *   400 DO
 *   410   I = I + 1
 *   420 LOOP WHILE I &lt; 10   (o LOOP UNTIL I &gt;= 10, o LOOP a secas)
 * </pre>
 *
 * <p>Un bloque estructurado no puede cruzar el limite de una {@code FUNCTION}.</p>
 *
 * <p>Constantes simbolicas y azucar de asignacion:</p>
 *
 * <pre>
 *   CONST MAX = 10
 *   CONST SALUDO = "HOLA"
 *   100 I = 0
 *   110 I += 1          : equivale a  I = I + (1)
 *   120 I -= 1          : equivale a  I = I - (1)
 *   130 I *= 2          : equivale a  I = I * (2)
 *   140 I /= 2          : equivale a  I = I / (2)
 *   150 INC I           : equivale a  I = I + 1
 *   160 DEC I, 3        : equivale a  I = I - (3)
 * </pre>
 *
 * <p>Las constantes son globales y se sustituyen en tiempo de compilacion. Los
 * parametros y variables {@code LOCAL} de una funcion tienen prioridad sobre una
 * constante con el mismo nombre.</p>
 *
 * <p>Nombres reservados en Samaruc BASIC: {@code P()}, {@code L()}, {@code R} y
 * {@code R$}.</p>
 */
public final class SamarucBasicTranspiler {

    private static final String IDENT = "[A-Za-z_][A-Za-z0-9_]*";
    private static final String FN_NAME = IDENT + "\\$?";

    private static final Pattern PRAGMA = Pattern.compile(
            "^\\s*(?:\\d+\\s*)?(?:REM\\s+|['#]\\s*)?@(?:samaruc|modern)\\s+on\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBERED = Pattern.compile("^(\\s*)(\\d+)(.*)$");

    private static final Pattern DEF = Pattern.compile(
            "^(?i)(FUNCTION|PROCEDURE)\\s+(" + FN_NAME + ")\\s*\\(\\s*(.*?)\\s*\\)\\s*$");

    private static final Pattern END_DEF = Pattern.compile(
            "^(?i)END\\s+(FUNCTION|PROCEDURE)\\s*$");

    private static final Pattern LOCAL = Pattern.compile("^(?i)LOCAL\\s+(.+)$");

    private static final Pattern RETURN_EXPR = Pattern.compile("^(?i)RETURN\\s+(.+)$");

    private static final Pattern PARAM = Pattern.compile("^" + IDENT + "$");

    private static final Pattern ASSIGN_CALL = Pattern.compile(
            "^(" + IDENT + "\\$?(?:\\s*\\([^()]*\\))?)\\s*=\\s*(" + FN_NAME + ")\\s*\\((.*)\\)\\s*$");

    private static final Pattern CALL_STMT = Pattern.compile(
            "^(" + FN_NAME + ")\\s*\\((.*)\\)\\s*$");

    private static final Pattern IF_OPEN = Pattern.compile("^(?i)IF\\s+(.+?)\\s+THEN\\s*$");
    private static final Pattern ELSE_LINE = Pattern.compile("^(?i)ELSE\\s*$");
    private static final Pattern ENDIF_LINE = Pattern.compile("^(?i)END\\s*IF\\s*$");
    private static final Pattern WHILE_OPEN = Pattern.compile("^(?i)WHILE\\s+(.+?)\\s*$");
    private static final Pattern ENDWHILE_LINE = Pattern.compile("^(?i)(ENDWHILE|WEND|END\\s+WHILE)\\s*$");
    private static final Pattern REPEAT_LINE = Pattern.compile("^(?i)REPEAT\\s*$");
    private static final Pattern UNTIL_LINE = Pattern.compile("^(?i)UNTIL\\s+(.+?)\\s*$");
    private static final Pattern DO_LINE = Pattern.compile("^(?i)DO\\s*$");
    private static final Pattern LOOP_LINE = Pattern.compile("^(?i)LOOP(?:\\s+(WHILE|UNTIL)\\s+(.+?))?\\s*$");

    private static final Pattern CONST_DEF = Pattern.compile(
            "^(?i)CONST\\s+(" + FN_NAME + ")\\s*=\\s*(.+)$");
    private static final Pattern COMPOUND = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*\\$?)(\\s*\\([^()]*\\))?\\s*(\\+=|-=|\\*=|/=)\\s*(.+)$");
    private static final Pattern INC_DEC = Pattern.compile(
            "^(?i)(INC|DEC)\\s+([A-Za-z_][A-Za-z0-9_]*\\$?)(\\s*\\([^()]*\\))?(?:\\s*,\\s*(.+))?$");

    private SamarucBasicTranspiler() {
    }

    /**
     * Traduce {@code source}. Si no contiene la marca {@code @samaruc on} devuelve la
     * fuente sin cambios. En caso contrario elimina la marca y desazuca funciones,
     * procedimientos y locales a BASIC clasico numerado.
     */
    public static String transpile(String source) throws SamarucBasicException {
        if (source == null || source.isEmpty()) {
            return source == null ? "" : source;
        }
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        List<String> rawLines = splitLines(normalized);
        boolean enabled = false;
        for (String raw : rawLines) {
            if (PRAGMA.matcher(raw).matches()) {
                enabled = true;
                break;
            }
        }
        if (!enabled) {
            return source;
        }

        List<SrcLine> lines = new ArrayList<>(rawLines.size());
        for (String raw : rawLines) {
            lines.add(parse(raw));
        }

        Program program = collect(lines);
        resolveConstants(program);
        allocate(program);
        program.structured = matchBlocks(lines);
        List<String> body = rewrite(lines, program);
        return assemble(program, body);
    }

    // ------------------------------------------------------------------ parsing

    private static List<String> splitLines(String text) {
        String content = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        List<String> result = new ArrayList<>();
        if (content.isEmpty()) {
            return result;
        }
        for (String part : content.split("\n", -1)) {
            result.add(part);
        }
        return result;
    }

    private static SrcLine parse(String raw) {
        Matcher m = NUMBERED.matcher(raw);
        if (m.matches()) {
            return new SrcLine(Integer.parseInt(m.group(2)), m.group(1), m.group(3), raw);
        }
        return new SrcLine(-1, "", raw, raw);
    }

    private static Program collect(List<SrcLine> lines) throws SamarucBasicException {
        Program program = new Program();
        FnDef current = null;
        for (SrcLine line : lines) {
            Matcher constant = CONST_DEF.matcher(line.body.trim());
            if (constant.matches()) {
                defineConstant(program, constant.group(1), constant.group(2).trim(), line.number);
                continue;
            }
            if (line.number < 0 || PRAGMA.matcher(line.raw).matches()) {
                continue;
            }
            String t = line.body.trim();
            Matcher def = DEF.matcher(t);
            if (def.matches()) {
                if (current != null) {
                    throw new SamarucBasicException("No se puede definir una funcion dentro de otra funcion", line.number);
                }
                FnDef fn = new FnDef();
                fn.keyword = def.group(1).toUpperCase(Locale.ROOT);
                fn.procedure = fn.keyword.equals("PROCEDURE");
                fn.name = def.group(2);
                fn.stringReturn = fn.name.endsWith("$");
                if (fn.procedure && fn.stringReturn) {
                    throw new SamarucBasicException("Un PROCEDURE no puede devolver valor (el nombre no debe acabar en $)", line.number);
                }
                fn.startLine = line.number;
                parseParams(fn, def.group(3), line.number);
                if (program.byName.putIfAbsent(fn.name.toUpperCase(Locale.ROOT), fn) != null) {
                    throw new SamarucBasicException("Funcion duplicada: " + fn.name, line.number);
                }
                program.functions.add(fn);
                current = fn;
                continue;
            }
            Matcher end = END_DEF.matcher(t);
            if (end.matches()) {
                if (current == null) {
                    throw new SamarucBasicException("END " + end.group(1).toUpperCase(Locale.ROOT) + " sin su definicion", line.number);
                }
                String expected = current.procedure ? "PROCEDURE" : "FUNCTION";
                if (!end.group(1).equalsIgnoreCase(expected)) {
                    throw new SamarucBasicException("END " + end.group(1).toUpperCase(Locale.ROOT)
                            + " no coincide con " + expected + " " + current.name, line.number);
                }
                current.endLine = line.number;
                current = null;
                continue;
            }
            Matcher local = LOCAL.matcher(t);
            if (local.matches()) {
                if (current == null) {
                    throw new SamarucBasicException("LOCAL solo puede usarse dentro de una funcion", line.number);
                }
                parseLocals(current, local.group(1), line.number);
                continue;
            }
            Matcher ret = RETURN_EXPR.matcher(t);
            if (ret.matches() && current == null) {
                throw new SamarucBasicException("RETURN con valor fuera de una funcion", line.number);
            }
        }
        if (current != null) {
            throw new SamarucBasicException("Falta END " + current.keyword + " de " + current.name, current.startLine);
        }
        for (FnDef fn : program.functions) {
            if (fn.endLine < 0) {
                throw new SamarucBasicException("Falta END " + fn.keyword + " de " + fn.name, fn.startLine);
            }
            if (fn.endLine <= fn.startLine) {
                throw new SamarucBasicException("Rango de lineas invalido en la funcion " + fn.name, fn.startLine);
            }
        }
        return program;
    }

    private static void parseParams(FnDef fn, String text, int lineNumber) throws SamarucBasicException {
        if (text.isBlank()) {
            return;
        }
        for (String raw : text.split(",")) {
            String name = raw.trim();
            if (name.endsWith("$")) {
                throw new SamarucBasicException("Los parametros de cadena aun no estan soportados", lineNumber);
            }
            if (!PARAM.matcher(name).matches()) {
                throw new SamarucBasicException("Parametro no valido: '" + name + "'", lineNumber);
            }
            if (fn.slotOf(name) != null) {
                throw new SamarucBasicException("Parametro duplicado: " + name, lineNumber);
            }
            fn.params.add(name);
            fn.slots.put(name.toUpperCase(Locale.ROOT), fn.params.size() - 1);
        }
    }

    private static void parseLocals(FnDef fn, String text, int lineNumber) throws SamarucBasicException {
        for (String raw : text.split(",")) {
            String name = raw.trim();
            if (name.endsWith("$")) {
                throw new SamarucBasicException("Las variables locales de cadena aun no estan soportadas", lineNumber);
            }
            if (!PARAM.matcher(name).matches()) {
                throw new SamarucBasicException("Variable local no valida: '" + name + "'", lineNumber);
            }
            if (fn.slotOf(name) != null) {
                throw new SamarucBasicException("Variable local duplicada (o ya es un parametro): " + name, lineNumber);
            }
            fn.locals.add(name);
            fn.slots.put(name.toUpperCase(Locale.ROOT), fn.params.size() + fn.locals.size() - 1);
        }
    }

    private static void defineConstant(Program program, String name, String value, int line)
            throws SamarucBasicException {
        String upper = name.toUpperCase(Locale.ROOT);
        if (program.reservedNames.contains(upper)) {
            throw new SamarucBasicException("Nombre reservado en Samaruc BASIC: " + name, line);
        }
        if (program.constRaw.containsKey(upper)) {
            throw new SamarucBasicException("Constante duplicada: " + name, line);
        }
        if (value.isEmpty()) {
            throw new SamarucBasicException("La constante " + name + " no tiene valor", line);
        }
        program.constRaw.put(upper, value);
        program.constDisplay.put(upper, name);
        program.constLine.put(upper, line);
    }

    private static void resolveConstants(Program program) throws SamarucBasicException {
        for (String key : program.constRaw.keySet()) {
            if (program.byName.containsKey(key)) {
                throw new SamarucBasicException("La constante " + program.constDisplay.get(key)
                        + " colisiona con una funcion", program.constLine.getOrDefault(key, -1));
            }
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String key : program.constRaw.keySet()) {
            resolveConstant(key, program, resolved, new HashSet<>());
        }
        program.constValue.putAll(resolved);
    }

    private static String resolveConstant(String key, Program program,
                                          Map<String, String> resolved, Set<String> visiting)
            throws SamarucBasicException {
        String cached = resolved.get(key);
        if (cached != null) {
            return cached;
        }
        int line = program.constLine.getOrDefault(key, -1);
        if (!visiting.add(key)) {
            throw new SamarucBasicException("Ciclo entre constantes: " + program.constDisplay.get(key), line);
        }
        String expanded = expandConstValue(program.constRaw.get(key), program, resolved, visiting, line);
        visiting.remove(key);
        resolved.put(key, expanded);
        return expanded;
    }

    private static String expandConstValue(String value, Program program,
                                           Map<String, String> resolved, Set<String> visiting, int line)
            throws SamarucBasicException {
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        int n = value.length();
        while (i < n) {
            char c = value.charAt(i);
            if (c == '"') {
                int end = value.indexOf('"', i + 1);
                if (end < 0) {
                    sb.append(value, i, n);
                    break;
                }
                sb.append(value, i, end + 1);
                i = end + 1;
                continue;
            }
            if (isIdentStart(c)) {
                int start = i;
                while (i < n && isIdentPart(value.charAt(i))) {
                    i++;
                }
                String ident = value.substring(start, i);
                if (i < n && value.charAt(i) == '$') {
                    i++;
                    ident = ident + "$";
                }
                String upper = ident.toUpperCase(Locale.ROOT);
                if (program.constRaw.containsKey(upper)) {
                    sb.append(resolveConstant(upper, program, resolved, visiting));
                } else {
                    sb.append(ident);
                }
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String applyConstants(String body, Program program) {
        if (program.constValue.isEmpty()) {
            return body;
        }
        StringBuilder sb = new StringBuilder(body.length());
        int i = 0;
        int n = body.length();
        while (i < n) {
            char c = body.charAt(i);
            if (c == '"') {
                int end = body.indexOf('"', i + 1);
                if (end < 0) {
                    sb.append(body, i, n);
                    break;
                }
                sb.append(body, i, end + 1);
                i = end + 1;
                continue;
            }
            if (isIdentStart(c)) {
                int start = i;
                while (i < n && isIdentPart(body.charAt(i))) {
                    i++;
                }
                String ident = body.substring(start, i);
                if (i < n && body.charAt(i) == '$') {
                    i++;
                    ident = ident + "$";
                }
                String replacement = program.constValue.get(ident.toUpperCase(Locale.ROOT));
                sb.append(replacement != null ? replacement : ident);
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static void allocate(Program program) {
        int running = 0;
        for (FnDef fn : program.functions) {
            fn.numBase = running;
            fn.numCount = fn.params.size() + fn.locals.size();
            running += fn.numCount;
            program.maxArity = Math.max(program.maxArity, fn.params.size());
            for (int ln = fn.startLine + 1; ln < fn.endLine; ln++) {
                program.enclosing.put(ln, fn);
            }
        }
        program.totalLocals = running;
    }

    // ---------------------------------------------------------------- rewriting

    private static List<String> rewrite(List<SrcLine> lines, Program program) throws SamarucBasicException {
        List<String> out = new ArrayList<>();
        for (SrcLine line : lines) {
            if (PRAGMA.matcher(line.raw).matches()) {
                continue;
            }
            if (CONST_DEF.matcher(line.body.trim()).matches()) {
                if (line.number >= 0) {
                    out.add(line.number + " REM");
                }
                continue;
            }
            if (line.number < 0) {
                out.add(line.raw);
                continue;
            }
            program.used.add(line.number);

            FnDef def = program.startOf(line.number);
            if (def != null) {
                out.add(line.number + " " + entryCopy(def));
                continue;
            }
            FnDef ender = program.endOf(line.number);
            if (ender != null) {
                out.add(line.number + " RETURN");
                continue;
            }
            if (LOCAL.matcher(line.body.trim()).matches()) {
                out.add(line.number + " REM");
                continue;
            }

            FnDef enclosing = program.enclosing.get(line.number);
            String body = line.body;
            boolean opaque = isOpaque(line.body.trim());
            if (enclosing != null && !opaque) {
                body = substitute(body, enclosing);
            }
            if (!opaque) {
                body = applyConstants(body, program);
            }

            String trimmed = body.trim();
            Matcher ret = RETURN_EXPR.matcher(trimmed);
            if (ret.matches()) {
                if (enclosing == null) {
                    throw new SamarucBasicException("RETURN con valor fuera de una funcion", line.number);
                }
                String slot = enclosing.stringReturn ? "R$" : "R";
                out.add(line.number + " " + slot + "=" + ret.group(1).trim() + ": RETURN");
                continue;
            }

            String sugared = sugar(trimmed);
            if (sugared != null) {
                out.add(line.number + " " + sugared);
                continue;
            }

            String structured = structuredLine(trimmed, line.number, program);
            if (structured != null) {
                out.add(line.number + " " + structured);
                continue;
            }

            String rewritten = rewriteCall(trimmed, line.number, program);
            out.add((line.number + " " + rewritten).stripTrailing());
        }
        return out;
    }

    private static String entryCopy(FnDef fn) {
        if (fn.params.isEmpty()) {
            return "REM";
        }
        List<String> copies = new ArrayList<>(fn.params.size());
        for (int i = 0; i < fn.params.size(); i++) {
            copies.add("L(" + (fn.numBase + i) + ")=P(" + i + ")");
        }
        return String.join(": ", copies);
    }

    private static String substitute(String body, FnDef fn) {
        StringBuilder sb = new StringBuilder(body.length());
        int i = 0;
        int n = body.length();
        while (i < n) {
            char c = body.charAt(i);
            if (c == '"') {
                int end = body.indexOf('"', i + 1);
                if (end < 0) {
                    sb.append(body, i, n);
                    break;
                }
                sb.append(body, i, end + 1);
                i = end + 1;
                continue;
            }
            if (isIdentStart(c)) {
                int start = i;
                while (i < n && isIdentPart(body.charAt(i))) {
                    i++;
                }
                String ident = body.substring(start, i);
                if (i < n && body.charAt(i) == '$') {
                    i++;
                    sb.append(ident).append('$');
                    continue;
                }
                Integer slot = fn.slotOf(ident);
                if (slot != null) {
                    sb.append("L(").append(fn.numBase + slot).append(')');
                } else {
                    sb.append(ident);
                }
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String rewriteCall(String text, int lineNumber, Program program) throws SamarucBasicException {
        if (text.isEmpty()) {
            return text;
        }
        Matcher assign = ASSIGN_CALL.matcher(text);
        if (assign.matches()) {
            FnDef fn = program.byName.get(assign.group(2).toUpperCase(Locale.ROOT));
            if (fn != null) {
                return buildCall(assign.group(1).trim(), fn, assign.group(3), lineNumber, program);
            }
        }
        Matcher stmt = CALL_STMT.matcher(text);
        if (stmt.matches()) {
            FnDef fn = program.byName.get(stmt.group(1).toUpperCase(Locale.ROOT));
            if (fn != null) {
                return buildCall(null, fn, stmt.group(2), lineNumber, program);
            }
        }
        for (String name : program.byName.keySet()) {
            if (containsCall(text, name)) {
                throw new SamarucBasicException("La funcion " + name
                        + " solo puede usarse como asignacion o como sentencia (las llamadas anidadas"
                        + " dentro de una expresion requieren una variable temporal)", lineNumber);
            }
        }
        return text;
    }

    private static String buildCall(String target, FnDef fn, String argsText, int lineNumber, Program program)
            throws SamarucBasicException {
        if (target != null && fn.procedure) {
            throw new SamarucBasicException(fn.name + " es un PROCEDURE y no devuelve valor", lineNumber);
        }
        List<String> args = splitTopLevel(argsText);
        if (args.size() != fn.params.size()) {
            throw new SamarucBasicException("La funcion " + fn.name + " espera " + fn.params.size()
                    + " argumento(s) y se han indicado " + args.size(), lineNumber);
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            for (String name : program.byName.keySet()) {
                if (containsCall(arg, name)) {
                    throw new SamarucBasicException("Llamada anidada no soportada dentro de un argumento de "
                            + fn.name + "; usa una variable temporal", lineNumber);
                }
            }
            parts.add("P(" + i + ")=" + arg);
        }
        parts.add("GOSUB " + fn.startLine);
        if (target != null) {
            parts.add(target + "=" + (fn.stringReturn ? "R$" : "R"));
        }
        return String.join(": ", parts);
    }

    private static boolean containsCall(String text, String upperName) {
        Pattern p = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(upperName) + "\\s*\\(");
        return p.matcher(text).find();
    }

    private static List<String> splitTopLevel(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        int depth = 0;
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                result.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(text.substring(start).trim());
        return result;
    }

    private static boolean isOpaque(String trimmed) {
        if (trimmed.isEmpty()) {
            return false;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return upper.startsWith("REM") || upper.startsWith("DATA");
    }

    private static String assemble(Program program, List<String> body) throws SamarucBasicException {
        List<String> prologueBodies = new ArrayList<>();
        if (program.maxArity > 0 || program.totalLocals > 0) {
            List<String> dims = new ArrayList<>(2);
            if (program.maxArity > 0) {
                dims.add("DIM P(" + program.maxArity + ")");
            }
            if (program.totalLocals > 0) {
                dims.add("DIM L(" + program.totalLocals + ")");
            }
            prologueBodies.add(String.join(":", dims));
        }

        List<String> finalLines = new ArrayList<>();
        if (!prologueBodies.isEmpty()) {
            int minLine = program.used.stream().mapToInt(Integer::intValue).min().orElse(-1);
            int candidate = 1;
            for (String prologueBody : prologueBodies) {
                while (program.used.contains(candidate)) {
                    candidate++;
                }
                if (minLine != -1 && candidate >= minLine) {
                    throw new SamarucBasicException("No hay lineas libres por debajo de la primera linea del"
                            + " programa para el prologo de Samaruc BASIC; deja libre una linea baja (por ejemplo 1)", 0);
                }
                program.used.add(candidate);
                finalLines.add(candidate + " " + prologueBody);
                candidate++;
            }
        }
        finalLines.addAll(body);
        return String.join("\n", finalLines) + "\n";
    }

    private static String sugar(String text) {
        Matcher compound = COMPOUND.matcher(text);
        if (compound.matches()) {
            String lhs = compound.group(1) + (compound.group(2) == null ? "" : compound.group(2));
            String operator = compound.group(3).substring(0, 1);
            String rhs = compound.group(4).trim();
            return lhs + " = " + lhs + " " + operator + " " + wrapRhs(lhs, rhs);
        }
        Matcher incDec = INC_DEC.matcher(text);
        if (incDec.matches()) {
            String lhs = incDec.group(2) + (incDec.group(3) == null ? "" : incDec.group(3));
            String sign = incDec.group(1).equalsIgnoreCase("INC") ? "+" : "-";
            String amount = incDec.group(4) == null ? "1" : incDec.group(4).trim();
            return lhs + " = " + lhs + " " + sign + " " + wrapRhs(lhs, amount);
        }
        return null;
    }

    private static String wrapRhs(String lhs, String rhs) {
        if (lhs.endsWith("$") || !hasArithmeticOperator(rhs)) {
            return rhs;
        }
        return "(" + rhs + ")";
    }

    private static boolean hasArithmeticOperator(String text) {
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                int end = text.indexOf('"', i + 1);
                i = end < 0 ? n : end + 1;
                continue;
            }
            if (c == '+' || c == '-' || c == '*' || c == '/') {
                return true;
            }
            i++;
        }
        return false;
    }

    private static String structuredLine(String text, int number, Program program) {
        Structured s = program.structured;
        if (s == null || text.isEmpty()) {
            return null;
        }
        Matcher ifOpen = IF_OPEN.matcher(text);
        if (ifOpen.matches()) {
            return "IF NOT (" + ifOpen.group(1).trim() + ") THEN " + s.ifTarget.get(number);
        }
        if (ELSE_LINE.matcher(text).matches()) {
            return "GOTO " + s.elseSkip.get(number);
        }
        if (ENDIF_LINE.matcher(text).matches()) {
            return "REM";
        }
        Matcher whileOpen = WHILE_OPEN.matcher(text);
        if (whileOpen.matches()) {
            return "IF NOT (" + whileOpen.group(1).trim() + ") THEN " + s.whileTarget.get(number);
        }
        if (ENDWHILE_LINE.matcher(text).matches()) {
            return "GOTO " + s.endWhileBack.get(number);
        }
        if (REPEAT_LINE.matcher(text).matches()) {
            return "REM";
        }
        Matcher until = UNTIL_LINE.matcher(text);
        if (until.matches()) {
            return "IF NOT (" + until.group(1).trim() + ") THEN " + s.untilBack.get(number);
        }
        if (DO_LINE.matcher(text).matches()) {
            return "REM";
        }
        Matcher loop = LOOP_LINE.matcher(text);
        if (loop.matches()) {
            int back = s.loopBack.get(number);
            String kind = loop.group(1);
            if (kind == null) {
                return "GOTO " + back;
            }
            String cond = loop.group(2).trim();
            return kind.equalsIgnoreCase("WHILE")
                    ? "IF (" + cond + ") THEN " + back
                    : "IF NOT (" + cond + ") THEN " + back;
        }
        return null;
    }

    private static Structured matchBlocks(List<SrcLine> lines) throws SamarucBasicException {
        Structured s = new Structured();
        List<SrcLine> numbered = new ArrayList<>();
        for (SrcLine line : lines) {
            if (line.number >= 0 && !PRAGMA.matcher(line.raw).matches()) {
                numbered.add(line);
            }
        }
        Map<Integer, Integer> nextOf = new HashMap<>();
        for (int i = 0; i < numbered.size(); i++) {
            nextOf.put(numbered.get(i).number, i + 1 < numbered.size() ? numbered.get(i + 1).number : -1);
        }

        Deque<Block> stack = new ArrayDeque<>();
        for (SrcLine line : numbered) {
            String t = line.body.trim();
            int ln = line.number;
            if (DEF.matcher(t).matches() || END_DEF.matcher(t).matches()) {
                if (!stack.isEmpty()) {
                    throw new SamarucBasicException("Un bloque estructurado no puede cruzar el limite de una funcion", ln);
                }
                continue;
            }
            if (IF_OPEN.matcher(t).matches()) {
                stack.push(new Block(Kind.IF, ln));
                continue;
            }
            if (ELSE_LINE.matcher(t).matches()) {
                Block b = stack.peek();
                if (b == null || b.kind != Kind.IF || b.elseLine >= 0) {
                    throw new SamarucBasicException("ELSE sin IF correspondiente", ln);
                }
                b.elseLine = ln;
                continue;
            }
            if (ENDIF_LINE.matcher(t).matches()) {
                Block b = requireTop(stack, Kind.IF, "ENDIF", ln);
                int target = b.elseLine >= 0 ? nextOf.getOrDefault(b.elseLine, -1) : ln;
                if (target < 0) {
                    throw new SamarucBasicException("Falta una linea despues de ELSE", b.elseLine);
                }
                s.ifTarget.put(b.line, target);
                if (b.elseLine >= 0) {
                    int skip = nextOf.getOrDefault(ln, -1);
                    if (skip < 0) {
                        throw new SamarucBasicException("Falta una linea despues de ENDIF", ln);
                    }
                    s.elseSkip.put(b.elseLine, skip);
                }
                continue;
            }
            if (WHILE_OPEN.matcher(t).matches()) {
                stack.push(new Block(Kind.WHILE, ln));
                continue;
            }
            if (ENDWHILE_LINE.matcher(t).matches()) {
                Block b = requireTop(stack, Kind.WHILE, "ENDWHILE", ln);
                int target = nextOf.getOrDefault(ln, -1);
                if (target < 0) {
                    throw new SamarucBasicException("Falta una linea despues de ENDWHILE", ln);
                }
                s.whileTarget.put(b.line, target);
                s.endWhileBack.put(ln, b.line);
                continue;
            }
            if (REPEAT_LINE.matcher(t).matches()) {
                stack.push(new Block(Kind.REPEAT, ln));
                continue;
            }
            if (UNTIL_LINE.matcher(t).matches()) {
                Block b = requireTop(stack, Kind.REPEAT, "UNTIL", ln);
                s.untilBack.put(ln, b.line);
                continue;
            }
            if (DO_LINE.matcher(t).matches()) {
                stack.push(new Block(Kind.DO, ln));
                continue;
            }
            if (LOOP_LINE.matcher(t).matches()) {
                Block b = requireTop(stack, Kind.DO, "LOOP", ln);
                s.loopBack.put(ln, b.line);
                continue;
            }
        }
        if (!stack.isEmpty()) {
            Block b = stack.peek();
            throw new SamarucBasicException("Bloque estructurado sin cerrar", b.line);
        }
        return s;
    }

    private static Block requireTop(Deque<Block> stack, Kind kind, String what, int line)
            throws SamarucBasicException {
        Block b = stack.peek();
        if (b == null || b.kind != kind) {
            throw new SamarucBasicException(what + " sin su bloque de apertura", line);
        }
        stack.pop();
        return b;
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }

    // -------------------------------------------------------------------- model

    private record SrcLine(int number, String indent, String body, String raw) {
    }

    private enum Kind {
        IF, WHILE, REPEAT, DO
    }

    private static final class Block {
        final Kind kind;
        final int line;
        int elseLine = -1;

        Block(Kind kind, int line) {
            this.kind = kind;
            this.line = line;
        }
    }

    private static final class Structured {
        final Map<Integer, Integer> ifTarget = new HashMap<>();
        final Map<Integer, Integer> elseSkip = new HashMap<>();
        final Map<Integer, Integer> whileTarget = new HashMap<>();
        final Map<Integer, Integer> endWhileBack = new HashMap<>();
        final Map<Integer, Integer> untilBack = new HashMap<>();
        final Map<Integer, Integer> loopBack = new HashMap<>();
    }

    private static final class FnDef {
        String keyword;
        String name;
        boolean procedure;
        boolean stringReturn;
        int startLine = -1;
        int endLine = -1;
        int numBase;
        int numCount;
        final List<String> params = new ArrayList<>();
        final List<String> locals = new ArrayList<>();
        final Map<String, Integer> slots = new HashMap<>();

        Integer slotOf(String name) {
            return slots.get(name.toUpperCase(Locale.ROOT));
        }
    }

    private static final class Program {
        final List<FnDef> functions = new ArrayList<>();
        final Map<String, FnDef> byName = new LinkedHashMap<>();
        final Map<Integer, FnDef> enclosing = new HashMap<>();
        final Set<Integer> used = new HashSet<>();
        final Set<String> reservedNames = Set.of("P", "L", "R", "R$");
        final Map<String, String> constRaw = new LinkedHashMap<>();
        final Map<String, String> constDisplay = new LinkedHashMap<>();
        final Map<String, Integer> constLine = new LinkedHashMap<>();
        final Map<String, String> constValue = new LinkedHashMap<>();
        int maxArity;
        int totalLocals;
        Structured structured;

        FnDef startOf(int line) {
            for (FnDef fn : functions) {
                if (fn.startLine == line) {
                    return fn;
                }
            }
            return null;
        }

        FnDef endOf(int line) {
            for (FnDef fn : functions) {
                if (fn.endLine == line) {
                    return fn;
                }
            }
            return null;
        }
    }
}
