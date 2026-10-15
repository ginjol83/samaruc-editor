package com.retroeditor.service.atari.basic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class AtariBasicDetokenizer {

    private static final int STMT_LET_INV = 0x36;

    private final byte[] data;
    private final List<String> varNames = new ArrayList<>();
    private int pos;
    private int limit;

    private AtariBasicDetokenizer(byte[] data) {
        this.data = data;
    }

    public static String detokenize(byte[] data) throws AtariBasicSyntaxException {
        return new AtariBasicDetokenizer(data).run();
    }

    private String run() throws AtariBasicSyntaxException {
        if (data.length < 14) {
            throw fail(0, 0, "binary program too short");
        }
        int vntp = word(2);
        int vntd = word(4);
        int vvtp = word(6);
        int stmtab = word(8);
        int stmcur = word(10);
        int starp = word(12);
        if (vntd < vntp || vvtp < vntd || stmtab < vvtp || stmcur < stmtab || starp < stmcur
                || starp - vntp + 14 > data.length) {
            throw fail(0, 0, "invalid binary program header");
        }
        parseVnt(14, vntd - vntp + 14);
        StringBuilder out = new StringBuilder();
        int off = stmtab - vntp + 14;
        int tokEnd = stmcur - vntp + 14;
        int progEnd = starp - vntp + 14;
        while (off < tokEnd) {
            if (off + 3 > tokEnd) {
                throw fail(0, off, "truncated line header");
            }
            int num = (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
            if (num == 0x8000) {
                break;
            }
            int llen = data[off + 2] & 0xFF;
            int len = llen - 3;
            if (len < 1 || off + llen > progEnd) {
                throw fail(num, off, "truncated line");
            }
            StringBuilder line = new StringBuilder();
            line.append(num);
            int i = 0;
            int p = off + 3;
            boolean first = true;
            while (i < len) {
                int ptr = data[p] & 0xFF;
                int chunkLen = ptr - 4 - i;
                if (chunkLen < 1 || p + 1 + chunkLen > off + llen) {
                    throw fail(num, p, "truncated statement");
                }
                String text = decodeChunk(num, p + 1, p + 1 + chunkLen);
                if (first) {
                    line.append(' ');
                    first = false;
                }
                line.append(text);
                int term = data[p + chunkLen] & 0xFF;
                if (term == 0x14) {
                    line.append(" : ");
                } else if (term == 0x1B) {
                    line.append(' ');
                }
                p += 1 + chunkLen;
                i += 1 + chunkLen;
            }
            out.append(line).append('\n');
            off += llen;
        }
        return out.toString();
    }

    private String decodeChunk(int lineNum, int from, int to) throws AtariBasicSyntaxException {
        int stmtByte = data[from] & 0xFF;
        int term = data[to - 1] & 0xFF;
        if (stmtByte == AtariBasicTokens.Statement.REM.byteValue()) {
            return decodeRemData(AtariBasicTokens.Statement.REM, lineNum, from, to);
        }
        if (stmtByte == AtariBasicTokens.Statement.DATA.byteValue()) {
            return decodeRemData(AtariBasicTokens.Statement.DATA, lineNum, from, to);
        }
        if (stmtByte == STMT_LET_INV) {
            pos = from + 1;
            limit = (term == 0x14 || term == 0x16) ? to - 1 : to;
            return decodeAssignment(lineNum);
        }
        AtariBasicTokens.Statement s = AtariBasicTokens.Statement.forByte(stmtByte);
        if (s == null) {
            throw fail(lineNum, from, "invalid statement token 0x" + Integer.toHexString(stmtByte));
        }
        pos = from + 1;
        limit = (term == 0x14 || term == 0x16) ? to - 1 : to;
        String text = decodeStatement(lineNum, s);
        if (pos != limit) {
            throw fail(lineNum, pos, "unexpected token 0x" + Integer.toHexString(data[pos] & 0xFF));
        }
        return text;
    }

    private String decodeRemData(AtariBasicTokens.Statement s, int lineNum, int from, int to)
            throws AtariBasicSyntaxException {
        if ((data[to - 1] & 0xFF) != 0x9B) {
            throw fail(lineNum, to - 1, "missing end-of-line in comment");
        }
        byte[] payload = new byte[to - from - 2];
        for (int i = 0; i < payload.length; i++) {
            int b = data[from + 1 + i] & 0xFF;
            if (b != 0x09 && (b < 0x20 || b > 0x7E)) {
                throw fail(lineNum, from + 1 + i, "invalid character in comment payload");
            }
            payload[i] = (byte) b;
        }
        String text = new String(payload, StandardCharsets.US_ASCII);
        return payload.length == 0 ? s.keyword() : s.keyword() + " " + text;
    }

    private String decodeStatement(int lineNum, AtariBasicTokens.Statement s) throws AtariBasicSyntaxException {
        StringBuilder out = new StringBuilder();
        switch (s) {
            case BYE:
            case CONT:
            case CLR:
            case DEG:
            case DOS:
            case END:
            case NEW:
            case POP:
            case RAD:
            case RETURN:
            case STOP:
            case CSAVE:
            case CLOAD:
                return s.keyword();
            case COLOR:
            case GOTO:
            case GO_TO:
            case GOSUB:
            case GRAPHICS:
            case TRAP:
                return s.keyword() + " " + renderExpr(lineNum);
            case ENTER:
            case LOAD:
            case SAVE:
                return s.keyword() + " " + renderExpr(lineNum);
            case NEXT:
                return s.keyword() + " " + renderPrimary(lineNum);
            case POKE:
            case PLOT:
            case DRAWTO:
            case POSITION:
                out.append(s.keyword()).append(' ');
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ");
                renderExpr(lineNum, out);
                return out.toString();
            case SETCOLOR:
                out.append(s.keyword()).append(' ');
                renderExpr(lineNum, out);
                for (int i = 0; i < 2; i++) {
                    expectToken(lineNum, 0x12, ",");
                    out.append(", ");
                    renderExpr(lineNum, out);
                }
                return out.toString();
            case SOUND:
                out.append(s.keyword()).append(' ');
                renderExpr(lineNum, out);
                for (int i = 0; i < 3; i++) {
                    expectToken(lineNum, 0x12, ",");
                    out.append(", ");
                    renderExpr(lineNum, out);
                }
                return out.toString();
            case CLOSE:
                out.append(s.keyword());
                expectToken(lineNum, 0x1C, "#");
                out.append(" #");
                renderExpr(lineNum, out);
                return out.toString();
            case STATUS:
                out.append(s.keyword());
                expectToken(lineNum, 0x1C, "#");
                out.append(" #");
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ").append(renderVarName(lineNum));
                return out.toString();
            case NOTE:
                out.append(s.keyword());
                expectToken(lineNum, 0x1C, "#");
                out.append(" #");
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ").append(renderVarName(lineNum));
                expectToken(lineNum, 0x12, ",");
                out.append(", ").append(renderVarName(lineNum));
                return out.toString();
            case LOCATE:
                out.append(s.keyword()).append(' ');
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ");
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ").append(renderVarName(lineNum));
                return out.toString();
            case POINT:
                out.append(s.keyword());
                expectToken(lineNum, 0x1C, "#");
                out.append(" #");
                renderExpr(lineNum, out);
                for (int i = 0; i < 2; i++) {
                    expectToken(lineNum, 0x12, ",");
                    out.append(", ");
                    renderExpr(lineNum, out);
                }
                return out.toString();
            case OPEN:
                out.append(s.keyword());
                expectToken(lineNum, 0x1C, "#");
                out.append(" #");
                renderExpr(lineNum, out);
                for (int i = 0; i < 3; i++) {
                    expectToken(lineNum, 0x12, ",");
                    out.append(", ");
                    renderExpr(lineNum, out);
                }
                return out.toString();
            case XIO:
                out.append(s.keyword()).append(' ');
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                expectToken(lineNum, 0x1C, "#");
                out.append(", #");
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ");
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ");
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ");
                renderExpr(lineNum, out);
                return out.toString();
            case ON:
                out.append(s.keyword()).append(' ');
                renderExpr(lineNum, out);
                int op = next(lineNum);
                if (op == 0x17) {
                    out.append(" GOTO ");
                } else if (op == 0x18) {
                    out.append(" GOSUB ");
                } else {
                    throw fail(lineNum, pos - 1, "expected GOTO or GOSUB token");
                }
                renderExpr(lineNum, out);
                while (peek() == 0x12) {
                    pos++;
                    out.append(", ");
                    renderExpr(lineNum, out);
                }
                return out.toString();
            case GET:
                out.append(s.keyword());
                expectToken(lineNum, 0x1C, "#");
                out.append(" #");
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x12, ",");
                out.append(", ").append(renderVarName(lineNum));
                while (peek() == 0x12) {
                    pos++;
                    out.append(", ").append(renderVarName(lineNum));
                }
                return out.toString();
            case PUT:
                out.append(s.keyword());
                expectToken(lineNum, 0x1C, "#");
                out.append(" #");
                renderExpr(lineNum, out);
                while (peek() == 0x12) {
                    pos++;
                    out.append(", ");
                    renderExpr(lineNum, out);
                }
                return out.toString();
            case INPUT:
                if (peek() == 0x1C) {
                    pos++;
                    out.append(s.keyword()).append(" #");
                    renderExpr(lineNum, out);
                    if (peek() == 0x12) {
                        pos++;
                        out.append(", ");
                    } else if (peek() == 0x15) {
                        pos++;
                        out.append("; ");
                    } else {
                        throw fail(lineNum, pos, "expected ',' or ';' token");
                    }
                    out.append(renderVarName(lineNum));
                    while (peek() == 0x12) {
                        pos++;
                        out.append(", ").append(renderVarName(lineNum));
                    }
                    return out.toString();
                }
                out.append(s.keyword()).append(' ').append(renderVarName(lineNum));
                while (peek() == 0x12) {
                    pos++;
                    out.append(", ").append(renderVarName(lineNum));
                }
                return out.toString();
            case READ:
                out.append(s.keyword()).append(' ').append(renderVarName(lineNum));
                while (peek() == 0x12) {
                    pos++;
                    out.append(", ").append(renderVarName(lineNum));
                }
                return out.toString();
            case RESTORE:
            case RUN:
                out.append(s.keyword());
                if (pos < limit) {
                    out.append(' ');
                    renderExpr(lineNum, out);
                }
                return out.toString();
            case LIST:
                out.append(s.keyword());
                if (pos < limit) {
                    out.append(' ');
                    renderExpr(lineNum, out);
                    if (peek() == 0x12) {
                        pos++;
                        out.append(", ");
                        renderExpr(lineNum, out);
                        if (peek() == 0x12) {
                            pos++;
                            out.append(", ");
                            renderExpr(lineNum, out);
                        }
                    }
                }
                return out.toString();
            case FOR:
                out.append(s.keyword()).append(' ');
                renderPrimary(lineNum, out);
                expectToken(lineNum, 0x2D, "=");
                out.append('=');
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x19, "TO");
                out.append(" TO ");
                renderExpr(lineNum, out);
                if (peek() == 0x1A) {
                    pos++;
                    out.append(" STEP ");
                    renderExpr(lineNum, out);
                }
                return out.toString();
            case LET:
                out.append(s.keyword()).append(' ');
                decodeAssignment(lineNum, out);
                return out.toString();
            case IF:
                out.append(s.keyword()).append(' ');
                renderExpr(lineNum, out);
                expectToken(lineNum, 0x1B, "THEN");
                out.append(" THEN");
                if (pos < limit) {
                    int b = next(lineNum);
                    if (b != 0x0E) {
                        throw fail(lineNum, pos - 1, "expected line number after THEN");
                    }
                    out.append(' ').append(renderBcd(lineNum));
                }
                return out.toString();
            case PRINT:
            case PRINT_:
                out.append(s.keyword());
                decodePrintArgs(lineNum, out);
                return out.toString();
            case LPRINT:
                out.append(s.keyword()).append(' ');
                decodePrintItems(lineNum, out);
                return out.toString();
            case COM:
            case DIM:
                out.append(s.keyword()).append(' ');
                renderPrimary(lineNum, out);
                while (peek() == 0x12) {
                    pos++;
                    out.append(", ");
                    renderPrimary(lineNum, out);
                }
                return out.toString();
            default:
                throw fail(lineNum, pos, "unsupported statement");
        }
    }

    private String decodeAssignment(int lineNum) throws AtariBasicSyntaxException {
        StringBuilder out = new StringBuilder();
        decodeAssignment(lineNum, out);
        return out.toString();
    }

    private void decodeAssignment(int lineNum, StringBuilder out) throws AtariBasicSyntaxException {
        renderPrimary(lineNum, out);
        int op = next(lineNum);
        if (op == 0x2D || op == 0x2E) {
            out.append('=');
        } else {
            throw fail(lineNum, pos - 1, "expected assignment token");
        }
        renderExpr(lineNum, out);
    }

    private void decodePrintArgs(int lineNum, StringBuilder out) throws AtariBasicSyntaxException {
        if (peek() == 0x1C) {
            pos++;
            out.append(" #");
            renderExpr(lineNum, out);
            if (peek() == 0x12) {
                pos++;
                out.append(", ");
                decodePrintItems(lineNum, out);
            } else if (peek() == 0x15) {
                pos++;
                out.append("; ");
                decodePrintItems(lineNum, out);
            }
            return;
        }
        if (pos < limit) {
            out.append(' ');
            decodePrintItems(lineNum, out);
        }
    }

    private void decodePrintItems(int lineNum, StringBuilder out) throws AtariBasicSyntaxException {
        if (isExprStart(peek())) {
            renderExpr(lineNum, out);
        }
        while (true) {
            if (peek() == 0x12) {
                pos++;
                out.append(',');
                if (isExprStart(peek())) {
                    out.append(' ');
                    renderExpr(lineNum, out);
                }
            } else if (peek() == 0x15) {
                pos++;
                out.append(';');
                if (isExprStart(peek())) {
                    out.append(' ');
                    renderExpr(lineNum, out);
                }
            } else {
                break;
            }
        }
    }

    private String renderVarName(int lineNum) throws AtariBasicSyntaxException {
        int b = next(lineNum);
        if (b < 0x80 || b > 0xFF) {
            throw fail(lineNum, pos - 1, "expected variable token");
        }
        int idx = b - 0x80;
        if (idx >= varNames.size()) {
            throw fail(lineNum, pos - 1, "invalid variable index " + idx);
        }
        return varNames.get(idx);
    }

    private String renderExpr(int lineNum) throws AtariBasicSyntaxException {
        StringBuilder out = new StringBuilder();
        renderExpr(lineNum, out);
        return out.toString();
    }

    private void renderExpr(int lineNum, StringBuilder out) throws AtariBasicSyntaxException {
        renderPrimary(lineNum, out);
        while (true) {
            int op = peek();
            if (!isInfix(op)) {
                break;
            }
            pos++;
            if (op == 0x29) {
                out.append(" OR ");
            } else if (op == 0x2A) {
                out.append(" AND ");
            } else {
                out.append(AtariBasicTokens.Token.forByte(op).text());
            }
            renderPrimary(lineNum, out);
        }
    }

    private void renderPrimary(int lineNum, StringBuilder out) throws AtariBasicSyntaxException {
        int b = next(lineNum);
        if (b == 0x0E) {
            out.append(renderBcd(lineNum));
        } else if (b == 0x0F) {
            int len = next(lineNum);
            if (len < 0 || pos + len > limit) {
                throw fail(lineNum, pos, "truncated string constant");
            }
            out.append('"');
            for (int i = 0; i < len; i++) {
                int c = data[pos] & 0xFF;
                if (c < 0x20 || c > 0x7E || c == '"') {
                    throw fail(lineNum, pos, "invalid character in string constant");
                }
                out.append((char) c);
                pos++;
            }
            out.append('"');
        } else if (b == 0x2B) {
            out.append('(');
            renderExpr(lineNum, out);
            expectToken(lineNum, 0x2C, ")");
            out.append(')');
        } else if (b == 0x28) {
            out.append("NOT ");
            renderPrimary(lineNum, out);
        } else if (b == 0x35 || b == 0x36) {
            out.append(b == 0x36 ? '-' : '+');
            renderPrimary(lineNum, out);
        } else if (b >= 0x3D && b <= 0x54) {
            AtariBasicTokens.Token fn = AtariBasicTokens.Token.forByte(b);
            out.append(fn.text());
            expectToken(lineNum, 0x3A, "(");
            out.append('(');
            renderExpr(lineNum, out);
            while (peek() == 0x3C) {
                pos++;
                out.append(", ");
                renderExpr(lineNum, out);
            }
            expectToken(lineNum, 0x2C, ")");
            out.append(')');
        } else if (b >= 0x80) {
            int idx = b - 0x80;
            if (idx >= varNames.size()) {
                throw fail(lineNum, pos - 1, "invalid variable index " + idx);
            }
            String name = varNames.get(idx);
            if (peek() == 0x37 || peek() == 0x38 || peek() == 0x39 || peek() == 0x3B) {
                pos++;
                if (name.endsWith("(")) {
                    out.append(name, 0, name.length() - 1);
                } else {
                    out.append(name);
                }
                out.append('(');
                renderExpr(lineNum, out);
                while (peek() == 0x3C) {
                    pos++;
                    out.append(", ");
                    renderExpr(lineNum, out);
                }
                expectToken(lineNum, 0x2C, ")");
                out.append(')');
            } else {
                out.append(name);
            }
        } else {
            throw fail(lineNum, pos - 1, "invalid expression token 0x" + Integer.toHexString(b));
        }
    }

    private String renderBcd(int lineNum) throws AtariBasicSyntaxException {
        if (pos + 6 > limit) {
            throw fail(lineNum, pos, "truncated numeric constant");
        }
        int exp = data[pos] & 0xFF;
        int d0 = data[pos + 1] & 0xFF;
        int d1 = data[pos + 2] & 0xFF;
        int d2 = data[pos + 3] & 0xFF;
        int d3 = data[pos + 4] & 0xFF;
        int d4 = data[pos + 5] & 0xFF;
        pos += 6;
        return AtariBcd.of(exp, d0, d1, d2, d3, d4).toBasicString();
    }

    private String renderPrimary(int lineNum) throws AtariBasicSyntaxException {
        StringBuilder out = new StringBuilder();
        renderPrimary(lineNum, out);
        return out.toString();
    }

    private boolean isExprStart(int b) {
        return b == 0x0E || b == 0x0F || b == 0x2B || b == 0x28 || b == 0x35 || b == 0x36
                || (b >= 0x3D && b <= 0x54) || (b >= 0x80 && b <= 0xFF);
    }

    private boolean isInfix(int b) {
        return (b >= 0x1D && b <= 0x22) || (b >= 0x23 && b <= 0x27) || b == 0x29 || b == 0x2A
                || (b >= 0x2F && b <= 0x34);
    }

    private int peek() {
        return pos < limit ? data[pos] & 0xFF : -1;
    }

    private int next(int lineNum) throws AtariBasicSyntaxException {
        if (pos >= limit) {
            throw fail(lineNum, pos, "unexpected end of statement");
        }
        return data[pos++] & 0xFF;
    }

    private void expectToken(int lineNum, int expected, String what) throws AtariBasicSyntaxException {
        int b = next(lineNum);
        if (b != expected) {
            throw fail(lineNum, pos - 1, "expected " + what + ", got 0x" + Integer.toHexString(b));
        }
    }

    private void parseVnt(int start, int end) throws AtariBasicSyntaxException {
        int i = start;
        while (i < end) {
            int b = data[i] & 0xFF;
            if (b == 0) {
                break;
            }
            StringBuilder name = new StringBuilder();
            while (i < end && (data[i] & 0xFF) < 0x80) {
                name.append((char) (data[i] & 0xFF));
                i++;
            }
            if (i >= end) {
                throw fail(0, i, "truncated variable name table");
            }
            int term = data[i] & 0xFF;
            i++;
            if (term == 0xA8) {
                name.append('(');
            } else if (term == 0xA4) {
                name.append('$');
            } else {
                name.append((char) (term & 0x7F));
            }
            varNames.add(name.toString());
        }
    }

    private int word(int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private AtariBasicSyntaxException fail(int line, int column, String message) {
        return new AtariBasicSyntaxException(message, line, column);
    }
}
