package com.retroeditor.service.atari.basic;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class AtariBasicTokenizer {

    private static final int STMT_LET_INV = 0x36;
    private static final int STMT_IF = 0x07;
    private static final int MAX_VARS = 128;

    private final String src;
    private int pos;
    private final List<String> varNames = new ArrayList<>();
    private final List<VarKind> varKinds = new ArrayList<>();

    private enum VarKind {
        FLOAT, STRING, ARRAY
    }

    private static class Node {
        Node left;
        Node right;
    }

    private static final class TokNode extends Node {
        AtariBasicTokens.Token token;

        TokNode(AtariBasicTokens.Token token, Node left, Node right) {
            this.token = token;
            this.left = left;
            this.right = right;
        }
    }

    private static final class NumNode extends Node {
        double value;

        NumNode(double value) {
            this.value = value;
        }
    }

    private static final class StrNode extends Node {
        byte[] text;

        StrNode(byte[] text) {
            this.text = text;
        }
    }

    private static final class VarNode extends Node {
        int id;

        VarNode(int id) {
            this.id = id;
        }
    }

    private static final class ParsedStatement {
        int stmtByte;
        Node args;
        byte[] payload;
        int srcPos;

        ParsedStatement(int stmtByte, Node args, byte[] payload, int srcPos) {
            this.stmtByte = stmtByte;
            this.args = args;
            this.payload = payload;
            this.srcPos = srcPos;
        }
    }

    private record ParsedLine(int number, List<ParsedStatement> statements, int srcPos) {
    }

    private AtariBasicTokenizer(String src) {
        this.src = src;
    }

    public static byte[] tokenize(String source) throws AtariBasicSyntaxException {
        return tokenize(source, AtariBasicVariant.ATARI_BASIC);
    }

    public static byte[] tokenize(String source, AtariBasicVariant variant) throws AtariBasicSyntaxException {
        return new AtariBasicTokenizer(source).run();
    }

    private byte[] run() throws AtariBasicSyntaxException {
        if (!src.isEmpty() && src.charAt(0) == '\uFEFF') {
            pos = 1;
        }
        List<ParsedLine> lines = new ArrayList<>();
        while (!atEnd()) {
            parseLine(lines);
        }
        return assemble(lines);
    }

    private void parseLine(List<ParsedLine> lines) throws AtariBasicSyntaxException {
        skipSpc();
        if (atEol()) {
            consumeEol();
            return;
        }
        char c = cur();
        if (c == '$') {
            throw fail("parser directives are not supported");
        }
        if (c == ':') {
            throw fail("statements after ':' at line start are not supported");
        }
        int save = pos;
        int num = parseLineNumber();
        skipSpc();
        if (atEol()) {
            consumeEol();
            lines.add(new ParsedLine(num, new ArrayList<>(), save));
            return;
        }
        List<ParsedStatement> stmts = parseStatementLine();
        requireEol();
        lines.add(new ParsedLine(num, stmts, save));
    }

    private List<ParsedStatement> parseStatementLine() throws AtariBasicSyntaxException {
        List<ParsedStatement> list = new ArrayList<>();
        list.addAll(parseStatement());
        while (!atEnd() && cur() == ':') {
            pos++;
            skipSpc();
            if (atEol()) {
                throw fail("expected statement after ':'");
            }
            list.addAll(parseStatement());
        }
        return list;
    }

    private List<ParsedStatement> parseStatement() throws AtariBasicSyntaxException {
        int save = pos;
        for (AtariBasicTokens.Statement s : AtariBasicTokens.Statement.values()) {
            if (tryStatement(s)) {
                skipSpc();
                return parseStatementBody(s, save);
            }
        }
        return List.of(parseImpliedLet(save));
    }

    private ParsedStatement parseImpliedLet(int save) throws AtariBasicSyntaxException {
        pos = save;
        Node target = tryAssignVarStr();
        if (target != null && matchLit('=')) {
            int saveEq = pos;
            Node value = tryStrExpr();
            if (value != null) {
                return new ParsedStatement(STMT_LET_INV, new TokNode(AtariBasicTokens.Token.S_ASGN, target, value), null, save);
            }
            pos = saveEq;
        }
        pos = save;
        target = tryAssignVarNum();
        if (target != null && matchLit('=')) {
            Node value = tryNumExpr();
            if (value != null) {
                return new ParsedStatement(STMT_LET_INV, new TokNode(AtariBasicTokens.Token.F_ASGN, target, value), null, save);
            }
        }
        pos = save;
        throw fail("expected statement");
    }

    private List<ParsedStatement> parseStatementBody(AtariBasicTokens.Statement s, int stmtPos) throws AtariBasicSyntaxException {
        byte stmtByte = (byte) s.byteValue();
        switch (s) {
            case REM:
            case DATA:
                return List.of(new ParsedStatement(stmtByte, null, parseCommentPayload(), stmtPos));
            case INPUT:
                return List.of(new ParsedStatement(stmtByte, parseInputExpr(), null, stmtPos));
            case COLOR:
                return List.of(new ParsedStatement(stmtByte, parseNumExprRequired("numeric expression"), null, stmtPos));
            case LIST:
                return List.of(new ParsedStatement(stmtByte, parseListExpr(), null, stmtPos));
            case ENTER:
                return List.of(new ParsedStatement(stmtByte, parseStrExprRequired("string expression"), null, stmtPos));
            case LET:
                return List.of(new ParsedStatement(stmtByte, parseLetExpr(), null, stmtPos));
            case IF:
                return parseIf(stmtPos);
            case FOR:
                return List.of(new ParsedStatement(stmtByte, parseForStmtExpr(), null, stmtPos));
            case NEXT:
                return List.of(new ParsedStatement(stmtByte, parsePVarNumRequired("variable"), null, stmtPos));
            case GOTO:
            case GO_TO:
            case GOSUB:
            case GRAPHICS:
                return List.of(new ParsedStatement(stmtByte, parseNumExprRequired("numeric expression"), null, stmtPos));
            case TRAP:
                return List.of(new ParsedStatement(stmtByte, parseNumExprRequired("numeric expression"), null, stmtPos));
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
                return List.of(new ParsedStatement(stmtByte, null, null, stmtPos));
            case COM:
            case DIM:
                return List.of(new ParsedStatement(stmtByte, parseDimVarList(), null, stmtPos));
            case CLOSE:
                return List.of(new ParsedStatement(stmtByte, parseIoChanRequired(), null, stmtPos));
            case OPEN:
                return List.of(new ParsedStatement(stmtByte, parseOpenExpr(), null, stmtPos));
            case LOAD:
            case SAVE:
                return List.of(new ParsedStatement(stmtByte, parseStrExprRequired("string expression"), null, stmtPos));
            case STATUS:
                return List.of(new ParsedStatement(stmtByte, parseStatusExpr(), null, stmtPos));
            case NOTE:
                return List.of(new ParsedStatement(stmtByte, parseNoteExpr(), null, stmtPos));
            case POINT:
                return List.of(new ParsedStatement(stmtByte, parseIoNumExpr2(), null, stmtPos));
            case XIO:
                return List.of(new ParsedStatement(stmtByte, parseXioExpr(), null, stmtPos));
            case ON:
                return List.of(new ParsedStatement(stmtByte, parseOnExpr(), null, stmtPos));
            case POKE:
            case DRAWTO:
            case PLOT:
            case POSITION:
                return List.of(new ParsedStatement(stmtByte, parseNumExpr2(), null, stmtPos));
            case PRINT:
            case PRINT_:
                return List.of(new ParsedStatement(stmtByte, parsePrintIoExpr(), null, stmtPos));
            case READ:
                return List.of(new ParsedStatement(stmtByte, parseVariableList(), null, stmtPos));
            case RESTORE:
                return List.of(new ParsedStatement(stmtByte, tryNumExpr(), null, stmtPos));
            case RUN:
                return List.of(new ParsedStatement(stmtByte, tryStrExpr(), null, stmtPos));
            case GET:
                return List.of(new ParsedStatement(stmtByte, parseGetExpr(), null, stmtPos));
            case PUT:
                return List.of(new ParsedStatement(stmtByte, parsePutExpr(), null, stmtPos));
            case SETCOLOR:
                return List.of(new ParsedStatement(stmtByte, parseNumExpr3(), null, stmtPos));
            case LOCATE:
                return List.of(new ParsedStatement(stmtByte, parseLocateExpr(), null, stmtPos));
            case SOUND:
                return List.of(new ParsedStatement(stmtByte, parseNumExpr4(), null, stmtPos));
            case LPRINT:
                return List.of(new ParsedStatement(stmtByte, parsePrintExpr(), null, stmtPos));
            default:
                throw fail("unsupported statement");
        }
    }

    private List<ParsedStatement> parseIf(int stmtPos) throws AtariBasicSyntaxException {
        int save = pos;
        Node cond = tryNumExpr();
        if (cond == null) {
            pos = save;
            throw fail("expected numeric expression");
        }
        if (!matchWord(AtariBasicTokens.Token.THEN)) {
            pos = save;
            throw fail("expected THEN");
        }
        int saveThen = pos;
        NumNode num = tryNumber();
        if (num != null) {
            if (!atEol()) {
                pos = saveThen;
                throw fail("expected end of line after 'IF ... THEN line-number'");
            }
            return List.of(new ParsedStatement(STMT_IF, new TokNode(AtariBasicTokens.Token.THEN, cond, num), null, stmtPos));
        }
        pos = saveThen;
        if (atEol()) {
            pos = save;
            throw fail("expected statement after THEN");
        }
        List<ParsedStatement> result = new ArrayList<>();
        result.add(new ParsedStatement(STMT_IF, new TokNode(AtariBasicTokens.Token.THEN, cond, null), null, stmtPos));
        result.addAll(parseStatementLine());
        return result;
    }

    private Node parseInputExpr() throws AtariBasicSyntaxException {
        int save = pos;
        Node chan = tryIoChan();
        if (chan != null) {
            if (matchLit(',')) {
                return comma(chan, parseVariableList());
            }
            if (matchLit(';')) {
                return new TokNode(AtariBasicTokens.Token.SEMICOLON, chan, parseVariableList());
            }
            pos = save;
        }
        return parseVariableList();
    }

    private Node parseListExpr() throws AtariBasicSyntaxException {
        int save = pos;
        Node str = tryStrExpr();
        if (str != null) {
            Node tree = str;
            if (matchLit(',')) {
                Node n1 = tryNumExpr();
                if (n1 != null) {
                    tree = comma(str, n1);
                    if (matchLit(',')) {
                        tree = comma(tree, parseNumExprRequired("numeric expression"));
                    }
                }
            }
            return tree;
        }
        pos = save;
        Node n = tryNumExpr();
        if (n != null && matchLit(',')) {
            return comma(n, parseNumExprRequired("numeric expression"));
        }
        pos = save;
        return parseNumExprRequired("numeric expression");
    }

    private Node parseLetExpr() throws AtariBasicSyntaxException {
        int save = pos;
        Node target = tryAssignVarStr();
        if (target != null && matchLit('=')) {
            Node value = parseStrExprRequired("string expression");
            return new TokNode(AtariBasicTokens.Token.S_ASGN, target, value);
        }
        pos = save;
        target = tryAssignVarNum();
        if (target == null) {
            throw fail("expected variable");
        }
        requireLit('=', "expected '='");
        Node value = parseNumExprRequired("numeric expression");
        return new TokNode(AtariBasicTokens.Token.F_ASGN, target, value);
    }

    private Node parseForStmtExpr() throws AtariBasicSyntaxException {
        Node var = parsePVarNumRequired("variable");
        requireLit('=', "expected '='");
        Node from = parseNumExprRequired("numeric expression");
        requireWord(AtariBasicTokens.Token.FOR_TO, "TO");
        Node to = parseNumExprRequired("numeric expression");
        Node tree = new TokNode(AtariBasicTokens.Token.F_ASGN, var, from);
        tree = new TokNode(AtariBasicTokens.Token.FOR_TO, tree, to);
        if (matchWord(AtariBasicTokens.Token.STEP)) {
            tree = new TokNode(AtariBasicTokens.Token.STEP, tree, parseNumExprRequired("numeric expression"));
        }
        return tree;
    }

    private Node parseOpenExpr() throws AtariBasicSyntaxException {
        Node n1 = parseIoChanRequired();
        requireLit(',', "expected ','");
        Node n2 = parseNumExprRequired("numeric expression");
        requireLit(',', "expected ','");
        Node n3 = parseNumExprRequired("numeric expression");
        requireLit(',', "expected ','");
        Node n4 = parseStrExprRequired("string expression");
        return comma(comma(comma(n1, n2), n3), n4);
    }

    private Node parseXioExpr() throws AtariBasicSyntaxException {
        Node n1 = parseNumExprRequired("numeric expression");
        requireLit(',', "expected ','");
        Node n2 = parseIoChanRequired();
        requireLit(',', "expected ','");
        Node n3 = parseNumExpr2();
        requireLit(',', "expected ','");
        Node n4 = parseStrExprRequired("string expression");
        return comma(comma(comma(n1, n2), n3), n4);
    }

    private Node parseOnExpr() throws AtariBasicSyntaxException {
        Node left = parseNumExprRequired("numeric expression");
        if (matchWord(AtariBasicTokens.Token.ON_GOTO)) {
            return new TokNode(AtariBasicTokens.Token.ON_GOTO, left, parseNumComma());
        }
        if (matchWord(AtariBasicTokens.Token.ON_GOSUB)) {
            return new TokNode(AtariBasicTokens.Token.ON_GOSUB, left, parseNumComma());
        }
        throw fail("expected GOTO or GOSUB");
    }

    private Node parseGetExpr() throws AtariBasicSyntaxException {
        Node chan = tryIoChan();
        if (chan == null) {
            throw fail("expected I/O channel (#)");
        }
        requireLit(',', "expected ','");
        return comma(chan, parseVarNumComma());
    }

    private Node parseLocateExpr() throws AtariBasicSyntaxException {
        Node l = parseNumExpr2();
        requireLit(',', "expected ','");
        return comma(l, parsePVarNumRequired("variable"));
    }

    private Node parseStatusExpr() throws AtariBasicSyntaxException {
        Node l = parseIoChanRequired();
        requireLit(',', "expected ','");
        return comma(l, parsePVarNumRequired("variable"));
    }

    private Node parseNoteExpr() throws AtariBasicSyntaxException {
        Node l = parseIoChanRequired();
        requireLit(',', "expected ','");
        Node r1 = parsePVarNumRequired("variable");
        requireLit(',', "expected ','");
        return comma(comma(l, r1), parsePVarNumRequired("variable"));
    }

    private Node parsePutExpr() throws AtariBasicSyntaxException {
        int save = pos;
        Node chan = tryIoChan();
        if (chan != null && matchLit(',')) {
            return comma(chan, parseNumComma());
        }
        pos = save;
        Node chan16 = new TokNode(AtariBasicTokens.Token.SHARP, null, new NumNode(16));
        return comma(chan16, parseNumComma());
    }

    private Node parseIoNumExpr2() throws AtariBasicSyntaxException {
        Node chan = parseIoChanRequired();
        requireLit(',', "expected ','");
        return comma(chan, parseNumExpr2());
    }

    private Node parseNumExpr2() throws AtariBasicSyntaxException {
        Node l = parseNumExprRequired("numeric expression");
        requireLit(',', "expected ','");
        return comma(l, parseNumExprRequired("numeric expression"));
    }

    private Node parseNumExpr3() throws AtariBasicSyntaxException {
        Node l = parseNumExpr2();
        requireLit(',', "expected ','");
        return comma(l, parseNumExprRequired("numeric expression"));
    }

    private Node parseNumExpr4() throws AtariBasicSyntaxException {
        Node l = parseNumExpr3();
        requireLit(',', "expected ','");
        return comma(l, parseNumExprRequired("numeric expression"));
    }

    private Node parseDimVarList() throws AtariBasicSyntaxException {
        Node l = parseDimVar();
        while (matchLit(',')) {
            l = comma(l, parseDimVar());
        }
        return l;
    }

    private Node parseDimVar() throws AtariBasicSyntaxException {
        int save = pos;
        String name = tryIdentifier();
        if (name == null) {
            throw fail("expected DIM string/array");
        }
        if (!atEnd() && cur() == '$') {
            pos++;
            skipSpc();
            if (!atEnd() && cur() == '(') {
                pos++;
                skipSpc();
                VarNode var = new VarNode(addIdent(name, VarKind.STRING));
                Node len = parseNumExprRequired("string dimension");
                requireLit(')', "expected ')'");
                return new TokNode(AtariBasicTokens.Token.DS_L_PRN, var, len);
            }
            pos = save;
            throw fail("expected DIM string/array");
        }
        if (!atEnd() && cur() == '(') {
            pos++;
            skipSpc();
            VarNode var = new VarNode(addIdent(name, VarKind.ARRAY));
            Node access = parseArrayAccess();
            return new TokNode(AtariBasicTokens.Token.D_L_PRN, var, access);
        }
        pos = save;
        throw fail("expected DIM string/array");
    }

    private Node parseArrayAccess() throws AtariBasicSyntaxException {
        Node l = parseNumExprRequired("array index");
        if (matchLit(',')) {
            l = new TokNode(AtariBasicTokens.Token.A_COMMA, l, parseNumExprRequired("array index"));
        }
        requireLit(')', "expected ')'");
        return l;
    }

    private Node parseVariableList() throws AtariBasicSyntaxException {
        Node l = parsePVarNumStrRequired();
        while (matchLit(',')) {
            l = comma(l, parsePVarNumStrRequired());
        }
        return l;
    }

    private Node parseVarNumComma() throws AtariBasicSyntaxException {
        Node l = parsePVarNumRequired("variable");
        while (matchLit(',')) {
            l = comma(l, parsePVarNumRequired("variable"));
        }
        return l;
    }

    private Node parseNumComma() throws AtariBasicSyntaxException {
        Node l = parseNumExprRequired("numeric expression");
        while (matchLit(',')) {
            l = comma(l, parseNumExprRequired("numeric expression"));
        }
        return l;
    }

    private Node parsePrintIoExpr() throws AtariBasicSyntaxException {
        int save = pos;
        Node chan = tryIoChan();
        if (chan != null) {
            if (matchLit(',')) {
                return new TokNode(AtariBasicTokens.Token.COMMA, chan, parsePrintExpr());
            }
            if (matchLit(';')) {
                return new TokNode(AtariBasicTokens.Token.SEMICOLON, chan, parsePrintExpr());
            }
            pos = save;
        }
        return parsePrintExpr();
    }

    private Node parsePrintExpr() throws AtariBasicSyntaxException {
        Node l = tryAnyExpr();
        while (true) {
            if (matchLit(',')) {
                l = new TokNode(AtariBasicTokens.Token.COMMA, l, tryAnyExpr());
            } else if (matchLit(';')) {
                l = new TokNode(AtariBasicTokens.Token.SEMICOLON, l, tryAnyExpr());
            } else {
                break;
            }
        }
        return l;
    }

    private Node tryAnyExpr() throws AtariBasicSyntaxException {
        Node n = tryNumExpr();
        if (n != null) {
            return n;
        }
        return tryStrExpr();
    }

    private Node tryNumExpr() throws AtariBasicSyntaxException {
        Node l = tryAndExpr();
        if (l == null) {
            return null;
        }
        while (matchWord(AtariBasicTokens.Token.OR)) {
            l = new TokNode(AtariBasicTokens.Token.OR, l, parseAndExprRequired());
        }
        return l;
    }

    private Node tryAndExpr() throws AtariBasicSyntaxException {
        Node l = tryCompExpr();
        if (l == null) {
            return null;
        }
        while (matchWord(AtariBasicTokens.Token.AND)) {
            l = new TokNode(AtariBasicTokens.Token.AND, l, parseCompExprRequired());
        }
        return l;
    }

    private Node tryCompExpr() throws AtariBasicSyntaxException {
        Node l = tryNotExpr();
        if (l == null) {
            return null;
        }
        while (true) {
            AtariBasicTokens.Token op = tryCompOp();
            if (op == null) {
                break;
            }
            l = new TokNode(op, l, parseNotExprRequired());
        }
        return l;
    }

    private Node tryNotExpr() throws AtariBasicSyntaxException {
        if (matchWord(AtariBasicTokens.Token.NOT)) {
            return new TokNode(AtariBasicTokens.Token.NOT, null, parseNotExprRequired());
        }
        return tryAddExpr();
    }

    private Node tryAddExpr() throws AtariBasicSyntaxException {
        Node l = tryMultExpr();
        if (l == null) {
            return null;
        }
        while (true) {
            if (matchLit('+')) {
                l = new TokNode(AtariBasicTokens.Token.PLUS, l, parseMultExprRequired());
            } else if (matchLit('-')) {
                l = new TokNode(AtariBasicTokens.Token.MINUS, l, parseMultExprRequired());
            } else {
                break;
            }
        }
        return l;
    }

    private Node tryMultExpr() throws AtariBasicSyntaxException {
        Node l = tryPowExpr();
        if (l == null) {
            return null;
        }
        while (true) {
            if (matchLit('*')) {
                l = new TokNode(AtariBasicTokens.Token.STAR, l, parsePowExprRequired());
            } else if (matchLit('/')) {
                l = new TokNode(AtariBasicTokens.Token.SLASH, l, parsePowExprRequired());
            } else {
                break;
            }
        }
        return l;
    }

    private Node tryPowExpr() throws AtariBasicSyntaxException {
        Node l = tryNegExpr();
        if (l == null) {
            return null;
        }
        while (matchLit('^')) {
            l = new TokNode(AtariBasicTokens.Token.CARET, l, parseNegExprRequired());
        }
        return l;
    }

    private Node tryNegExpr() throws AtariBasicSyntaxException {
        if (matchLit('-')) {
            return new TokNode(AtariBasicTokens.Token.UMINUS, null, parseNegExprRequired());
        }
        if (matchLit('+')) {
            return new TokNode(AtariBasicTokens.Token.UPLUS, null, parseNegExprRequired());
        }
        return tryUnitExpr();
    }

    private Node tryUnitExpr() throws AtariBasicSyntaxException {
        Node n = tryNumber();
        if (n != null) {
            return n;
        }
        n = tryStrCompExpr();
        if (n != null) {
            return n;
        }
        if (!atEnd() && cur() == '(') {
            int save = pos;
            pos++;
            skipSpc();
            Node inner = tryNumExpr();
            if (inner != null && matchLit(')')) {
                return new TokNode(AtariBasicTokens.Token.L_PRN, null, inner);
            }
            pos = save;
        }
        if (matchWord(AtariBasicTokens.Token.USR)) {
            return new TokNode(AtariBasicTokens.Token.USR, null, parseParUsrExpr());
        }
        if (matchWord(AtariBasicTokens.Token.ASC)) {
            return new TokNode(AtariBasicTokens.Token.ASC, null, parseParStrExpr());
        }
        if (matchWord(AtariBasicTokens.Token.VAL)) {
            return new TokNode(AtariBasicTokens.Token.VAL, null, parseParStrExpr());
        }
        if (matchWord(AtariBasicTokens.Token.LEN)) {
            return new TokNode(AtariBasicTokens.Token.LEN, null, parseParStrExpr());
        }
        if (matchWord(AtariBasicTokens.Token.ADR)) {
            return new TokNode(AtariBasicTokens.Token.ADR, null, parseParStrExpr());
        }
        if (matchWord(AtariBasicTokens.Token.ATN)) {
            return new TokNode(AtariBasicTokens.Token.ATN, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.COS)) {
            return new TokNode(AtariBasicTokens.Token.COS, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.PEEK)) {
            return new TokNode(AtariBasicTokens.Token.PEEK, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.SIN)) {
            return new TokNode(AtariBasicTokens.Token.SIN, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.RND)) {
            return new TokNode(AtariBasicTokens.Token.RND, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.FRE)) {
            return new TokNode(AtariBasicTokens.Token.FRE, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.EXP)) {
            return new TokNode(AtariBasicTokens.Token.EXP, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.LOG)) {
            return new TokNode(AtariBasicTokens.Token.LOG, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.CLOG)) {
            return new TokNode(AtariBasicTokens.Token.CLOG, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.SQR)) {
            return new TokNode(AtariBasicTokens.Token.SQR, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.SGN)) {
            return new TokNode(AtariBasicTokens.Token.SGN, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.ABS)) {
            return new TokNode(AtariBasicTokens.Token.ABS, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.INT)) {
            return new TokNode(AtariBasicTokens.Token.INT, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.PADDLE)) {
            return new TokNode(AtariBasicTokens.Token.PADDLE, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.STICK)) {
            return new TokNode(AtariBasicTokens.Token.STICK, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.PTRIG)) {
            return new TokNode(AtariBasicTokens.Token.PTRIG, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.STRIG)) {
            return new TokNode(AtariBasicTokens.Token.STRIG, null, parseParNumExpr());
        }
        return tryAssignVarNum();
    }

    private Node parseParNumExpr() throws AtariBasicSyntaxException {
        requireLit('(', "expected '('");
        Node inner = parseNumExprRequired("numeric expression");
        requireLit(')', "expected ')'");
        return inner;
    }

    private Node parseParStrExpr() throws AtariBasicSyntaxException {
        requireLit('(', "expected '('");
        Node inner = parseStrExprRequired("string expression");
        requireLit(')', "expected ')'");
        return inner;
    }

    private Node parseParUsrExpr() throws AtariBasicSyntaxException {
        requireLit('(', "expected '('");
        Node l = parseNumExprRequired("numeric expression");
        while (matchLit(',')) {
            l = new TokNode(AtariBasicTokens.Token.A_COMMA, l, parseNumExprRequired("numeric expression"));
        }
        requireLit(')', "expected ')'");
        return l;
    }

    private Node tryStrCompExpr() throws AtariBasicSyntaxException {
        int save = pos;
        Node l = tryStrExpr();
        if (l == null) {
            return null;
        }
        AtariBasicTokens.Token op = tryCompOp();
        if (op == null) {
            pos = save;
            return null;
        }
        Node r = parseStrExprRequired("string expression");
        return new TokNode(stringComp(op), l, r);
    }

    private Node tryStrExpr() throws AtariBasicSyntaxException {
        if (matchWord(AtariBasicTokens.Token.STRP)) {
            return new TokNode(AtariBasicTokens.Token.STRP, null, parseParNumExpr());
        }
        if (matchWord(AtariBasicTokens.Token.CHRP)) {
            return new TokNode(AtariBasicTokens.Token.CHRP, null, parseParNumExpr());
        }
        Node n = tryStringData();
        if (n != null) {
            return n;
        }
        return tryAssignVarStr();
    }

    private Node tryAssignVarNum() throws AtariBasicSyntaxException {
        int save = pos;
        Node arr = tryVarArray();
        if (arr != null) {
            Node access = tryArrayAccess();
            if (access != null) {
                return new TokNode(AtariBasicTokens.Token.A_L_PRN, arr, access);
            }
            pos = save;
            return null;
        }
        return tryVarNum();
    }

    private Node tryArrayAccess() throws AtariBasicSyntaxException {
        int save = pos;
        Node l = tryNumExpr();
        if (l == null) {
            return null;
        }
        if (matchLit(',')) {
            Node r = tryNumExpr();
            if (r == null) {
                pos = save;
                return null;
            }
            l = new TokNode(AtariBasicTokens.Token.A_COMMA, l, r);
        }
        if (!matchLit(')')) {
            pos = save;
            return null;
        }
        return l;
    }

    private Node tryAssignVarStr() throws AtariBasicSyntaxException {
        Node str = tryVarStr();
        if (str == null) {
            return null;
        }
        if (!atEnd() && cur() == '(') {
            int save = pos;
            pos++;
            skipSpc();
            Node access = tryArrayAccess();
            if (access != null) {
                return new TokNode(AtariBasicTokens.Token.S_L_PRN, str, access);
            }
            pos = save;
        }
        return str;
    }

    private Node tryIoChan() throws AtariBasicSyntaxException {
        int save = pos;
        if (!matchLit('#')) {
            return null;
        }
        Node expr = tryNumExpr();
        if (expr == null) {
            pos = save;
            return null;
        }
        return new TokNode(AtariBasicTokens.Token.SHARP, null, expr);
    }

    private Node parseIoChanRequired() throws AtariBasicSyntaxException {
        Node n = tryIoChan();
        if (n == null) {
            throw fail("expected I/O channel (#)");
        }
        return n;
    }

    private Node tryVarNum() throws AtariBasicSyntaxException {
        int save = pos;
        String name = tryIdentifier();
        if (name == null) {
            return null;
        }
        if (!atEnd() && (cur() == '$' || cur() == '(')) {
            pos = save;
            return null;
        }
        skipSpc();
        return new VarNode(addIdent(name, VarKind.FLOAT));
    }

    private Node tryVarStr() throws AtariBasicSyntaxException {
        int save = pos;
        String name = tryIdentifier();
        if (name == null) {
            return null;
        }
        if (atEnd() || cur() != '$') {
            pos = save;
            return null;
        }
        pos++;
        skipSpc();
        return new VarNode(addIdent(name, VarKind.STRING));
    }

    private Node tryVarArray() throws AtariBasicSyntaxException {
        int save = pos;
        String name = tryIdentifier();
        if (name == null) {
            return null;
        }
        if (atEnd() || cur() != '(') {
            pos = save;
            return null;
        }
        pos++;
        skipSpc();
        return new VarNode(addIdent(name, VarKind.ARRAY));
    }

    private Node parsePVarNumRequired(String what) throws AtariBasicSyntaxException {
        Node n = tryVarNum();
        if (n == null) {
            throw fail("expected " + what);
        }
        return n;
    }

    private Node parsePVarNumStrRequired() throws AtariBasicSyntaxException {
        Node n = tryVarNum();
        if (n != null) {
            return n;
        }
        n = tryVarStr();
        if (n == null) {
            throw fail("expected numeric or string variable");
        }
        return n;
    }

    private Node parseNumExprRequired(String what) throws AtariBasicSyntaxException {
        Node n = tryNumExpr();
        if (n == null) {
            throw fail("expected " + what);
        }
        return n;
    }

    private Node parseStrExprRequired(String what) throws AtariBasicSyntaxException {
        Node n = tryStrExpr();
        if (n == null) {
            throw fail("expected " + what);
        }
        return n;
    }

    private Node parseAndExprRequired() throws AtariBasicSyntaxException {
        Node n = tryAndExpr();
        if (n == null) {
            throw fail("expected numeric expression");
        }
        return n;
    }

    private Node parseCompExprRequired() throws AtariBasicSyntaxException {
        Node n = tryCompExpr();
        if (n == null) {
            throw fail("expected numeric expression");
        }
        return n;
    }

    private Node parseNotExprRequired() throws AtariBasicSyntaxException {
        Node n = tryNotExpr();
        if (n == null) {
            throw fail("expected numeric expression");
        }
        return n;
    }

    private Node parseMultExprRequired() throws AtariBasicSyntaxException {
        Node n = tryMultExpr();
        if (n == null) {
            throw fail("expected numeric expression");
        }
        return n;
    }

    private Node parsePowExprRequired() throws AtariBasicSyntaxException {
        Node n = tryPowExpr();
        if (n == null) {
            throw fail("expected numeric expression");
        }
        return n;
    }

    private Node parseNegExprRequired() throws AtariBasicSyntaxException {
        Node n = tryNegExpr();
        if (n == null) {
            throw fail("expected numeric expression");
        }
        return n;
    }

    private AtariBasicTokens.Token tryCompOp() {
        if (matchLitString("<=")) {
            return AtariBasicTokens.Token.N_LEQ;
        }
        if (matchLitString("<>")) {
            return AtariBasicTokens.Token.N_NEQ;
        }
        if (matchLitString(">=")) {
            return AtariBasicTokens.Token.N_GEQ;
        }
        if (matchLit('<')) {
            return AtariBasicTokens.Token.N_LE;
        }
        if (matchLit('>')) {
            return AtariBasicTokens.Token.N_GE;
        }
        if (matchLit('=')) {
            return AtariBasicTokens.Token.N_EQ;
        }
        return null;
    }

    private AtariBasicTokens.Token stringComp(AtariBasicTokens.Token op) {
        return switch (op) {
            case N_LEQ -> AtariBasicTokens.Token.S_LEQ;
            case N_NEQ -> AtariBasicTokens.Token.S_NEQ;
            case N_GEQ -> AtariBasicTokens.Token.S_GEQ;
            case N_LE -> AtariBasicTokens.Token.S_LE;
            case N_GE -> AtariBasicTokens.Token.S_GE;
            default -> AtariBasicTokens.Token.S_EQ;
        };
    }

    private byte[] parseCommentPayload() throws AtariBasicSyntaxException {
        int start = pos;
        while (!atEnd() && !isEolChar(cur())) {
            pos++;
        }
        String text = src.substring(start, pos);
        byte[] bytes = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\t' && (c < 0x20 || c > 0x7E)) {
                pos = start + i;
                throw fail("invalid character in comment or DATA payload");
            }
            bytes[i] = (byte) c;
        }
        return bytes;
    }

    private StrNode tryStringData() throws AtariBasicSyntaxException {
        if (atEnd() || cur() != '"') {
            return null;
        }
        int save = pos;
        pos++;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            if (atEnd()) {
                pos = save;
                return null;
            }
            char c = cur();
            if (c == '"') {
                pos++;
                break;
            }
            if (c < 0x20 || c > 0x7E) {
                throw fail("invalid character in string constant");
            }
            bytes.write(c);
            pos++;
        }
        skipSpc();
        return new StrNode(bytes.toByteArray());
    }

    private NumNode tryNumber() {
        int save = pos;
        boolean any = false;
        if (!atEnd() && isDigit(cur())) {
            while (!atEnd() && isDigit(cur())) {
                pos++;
            }
            any = true;
            if (!atEnd() && cur() == '.') {
                pos++;
                while (!atEnd() && isDigit(cur())) {
                    pos++;
                }
            }
        } else if (!atEnd() && cur() == '.' && pos + 1 < src.length() && isDigit(src.charAt(pos + 1))) {
            pos++;
            while (!atEnd() && isDigit(cur())) {
                pos++;
            }
            any = true;
        }
        if (!any) {
            pos = save;
            return null;
        }
        if (!atEnd() && (cur() == 'e' || cur() == 'E')) {
            int saveExp = pos;
            pos++;
            if (!atEnd() && (cur() == '+' || cur() == '-')) {
                pos++;
            }
            if (!atEnd() && isDigit(cur())) {
                pos++;
                if (!atEnd() && isDigit(cur())) {
                    pos++;
                }
            } else {
                pos = saveExp;
            }
        }
        String text = src.substring(save, pos);
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            pos = save;
            return null;
        }
        if (!Double.isFinite(value)) {
            pos = save;
            return null;
        }
        skipSpc();
        return new NumNode(value);
    }

    private int parseLineNumber() throws AtariBasicSyntaxException {
        int save = pos;
        if (!atEnd() && cur() == '-') {
            pos++;
        }
        boolean any = false;
        if (!atEnd() && isDigit(cur())) {
            while (!atEnd() && isDigit(cur())) {
                pos++;
            }
            any = true;
            if (!atEnd() && cur() == '.') {
                int saveDot = pos;
                pos++;
                if (!atEnd() && isDigit(cur())) {
                    while (!atEnd() && isDigit(cur())) {
                        pos++;
                    }
                } else {
                    pos = saveDot;
                }
            }
        } else if (!atEnd() && cur() == '.' && pos + 1 < src.length() && isDigit(src.charAt(pos + 1))) {
            pos++;
            while (!atEnd() && isDigit(cur())) {
                pos++;
            }
            any = true;
        }
        if (!any) {
            pos = save;
            throw fail("expected line number");
        }
        if (!atEnd() && (cur() == 'e' || cur() == 'E')) {
            int saveExp = pos;
            pos++;
            if (!atEnd() && (cur() == '+' || cur() == '-')) {
                pos++;
            }
            if (!atEnd() && isDigit(cur())) {
                pos++;
                if (!atEnd() && isDigit(cur())) {
                    pos++;
                }
            } else {
                pos = saveExp;
            }
        }
        double value;
        try {
            value = Double.parseDouble(src.substring(save, pos));
        } catch (NumberFormatException e) {
            pos = save;
            throw fail("invalid line number");
        }
        if (value < 0 || value > 65535) {
            throw fail("line number out of range");
        }
        skipSpc();
        return (int) (value + 0.5);
    }

    private boolean tryStatement(AtariBasicTokens.Statement s) {
        int save = pos;
        if (s == AtariBasicTokens.Statement.PRINT_) {
            if (!atEnd() && cur() == '?') {
                pos++;
                return true;
            }
            return false;
        }
        String keyword = s.keyword();
        for (int i = 0; i < keyword.length(); i++) {
            if (i >= s.min() && !atEnd() && cur() == '.') {
                pos++;
                return true;
            }
            if (atEnd() || !matchIgnoreCase(keyword.charAt(i))) {
                pos = save;
                return false;
            }
        }
        return true;
    }

    private boolean tryToken(AtariBasicTokens.Token t) {
        String text = t.text();
        int save = pos;
        for (int i = 0; i < text.length(); i++) {
            if (atEnd() || !matchIgnoreCase(text.charAt(i))) {
                pos = save;
                return false;
            }
        }
        return true;
    }

    private boolean matchWord(AtariBasicTokens.Token t) {
        if (tryToken(t)) {
            skipSpc();
            return true;
        }
        return false;
    }

    private boolean matchIgnoreCase(char c) {
        if (atEnd()) {
            return false;
        }
        char b = cur();
        if (fold(c) == fold(b)) {
            pos++;
            return true;
        }
        return false;
    }

    private static char fold(char c) {
        return (c >= 'a' && c <= 'z') ? (char) (c - 'a' + 'A') : c;
    }

    private boolean matchLit(char c) {
        if (!atEnd() && cur() == c) {
            pos++;
            skipSpc();
            return true;
        }
        return false;
    }

    private boolean matchLitString(String s) {
        int save = pos;
        for (int i = 0; i < s.length(); i++) {
            if (atEnd() || cur() != s.charAt(i)) {
                pos = save;
                return false;
            }
            pos++;
        }
        skipSpc();
        return true;
    }

    private void requireLit(char c, String what) throws AtariBasicSyntaxException {
        if (!matchLit(c)) {
            throw fail(what);
        }
    }

    private void requireWord(AtariBasicTokens.Token t, String what) throws AtariBasicSyntaxException {
        if (!matchWord(t)) {
            throw fail("expected " + what);
        }
    }

    private void requireEol() throws AtariBasicSyntaxException {
        if (!atEol()) {
            throw fail("expected end of line");
        }
        consumeEol();
    }

    private void consumeEol() {
        if (atEnd()) {
            return;
        }
        char c = cur();
        pos++;
        if (c == '\r' && !atEnd() && cur() == '\n') {
            pos++;
        }
    }

    private String tryIdentifier() {
        if (atEnd()) {
            return null;
        }
        char c = cur();
        if (!(isLetter(c) || c == '_')) {
            return null;
        }
        int save = pos;
        while (!atEnd() && (isLetter(cur()) || isDigit(cur()) || cur() == '_')) {
            pos++;
        }
        return src.substring(save, pos);
    }

    private void skipSpc() {
        while (!atEnd() && (cur() == ' ' || cur() == '\t')) {
            pos++;
        }
    }

    private boolean atEnd() {
        return pos >= src.length();
    }

    private char cur() {
        return src.charAt(pos);
    }

    private boolean atEol() {
        return atEnd() || isEolChar(cur());
    }

    private static boolean isEolChar(char c) {
        return c == '\n' || c == '\r' || c == '\u009B';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private int addIdent(String name, VarKind kind) throws AtariBasicSyntaxException {
        for (int i = 0; i < varNames.size(); i++) {
            if (varKinds.get(i) == kind && namesEqual(varNames.get(i), name)) {
                return i;
            }
        }
        if (varNames.size() >= MAX_VARS) {
            throw fail("too many variables, Atari BASIC supports up to 128");
        }
        varNames.add(name);
        varKinds.add(kind);
        return varNames.size() - 1;
    }

    private static boolean namesEqual(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (fold(a.charAt(i)) != fold(b.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static Node comma(Node left, Node right) {
        return new TokNode(AtariBasicTokens.Token.COMMA, left, right);
    }

    private byte[] assemble(List<ParsedLine> lines) throws AtariBasicSyntaxException {
        ByteArrayOutputStream toks = new ByteArrayOutputStream();
        int curLine = 0;
        boolean lineValid = false;
        boolean hadContent = false;
        for (ParsedLine line : lines) {
            if ((hadContent || lineValid) && line.number() <= curLine) {
                throw failAt("line number " + line.number() + " already in use", line.srcPos());
            }
            if (line.number() < 0 || line.number() >= 32768) {
                throw failAt("line number " + line.number() + " invalid", line.srcPos());
            }
            ByteArrayOutputStream tokLine = new ByteArrayOutputStream();
            boolean endColon = false;
            for (ParsedStatement st : line.statements()) {
                byte[] chunk = buildChunk(st);
                int maxlen = 255;
                if (st.stmtByte == AtariBasicTokens.Statement.FOR.byteValue()
                        && !(st.args instanceof TokNode t && t.token == AtariBasicTokens.Token.STEP)) {
                    maxlen = 254;
                }
                if (chunk.length + 4 > maxlen) {
                    throw failAt("statement too long", st.srcPos);
                }
                tokLine.write(chunk.length);
                tokLine.write(chunk, 0, chunk.length);
                endColon = st.payload == null;
            }
            if (line.statements().isEmpty()) {
                tokLine.write(2);
                tokLine.write(AtariBasicTokens.Statement.REM.byteValue());
                tokLine.write(0x9B);
                endColon = false;
            }
            byte[] data = tokLine.toByteArray();
            int len = data.length;
            if (len > 0xFF - 3) {
                throw failAt("line " + line.number() + " too long", line.srcPos());
            }
            if (endColon && len > 0) {
                data[len - 1] = 0x16;
            }
            toks.write(line.number() & 0xFF);
            toks.write((line.number() >> 8) & 0xFF);
            toks.write(len + 3);
            for (int i = 0; i < len; i += (data[i] & 0xFF) + 1) {
                toks.write(((data[i] & 0xFF) + i + 4) & 0xFF);
                toks.write(data, i + 1, data[i] & 0xFF);
            }
            curLine = line.number();
            lineValid = true;
            hadContent = !line.statements().isEmpty();
        }
        toks.write(0x00);
        toks.write(0x80);
        toks.write(0x06);
        toks.write(0x06);
        toks.write(0x34);
        toks.write(0x16);
        byte[] tokBytes = toks.toByteArray();
        byte[] vnt = buildVnt();
        byte[] vvt = buildVvt();
        if (varNames.size() > MAX_VARS) {
            throw failAt("too many variables", 0);
        }
        if (vnt.length + vvt.length + tokBytes.length > 0x9500) {
            throw failAt("program too big", 0);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        put16(out, 0);
        put16(out, 0x100);
        put16(out, 0x0FF + vnt.length);
        put16(out, 0x100 + vnt.length);
        put16(out, 0x100 + vnt.length + vvt.length);
        put16(out, 0x100 + vnt.length + vvt.length + tokBytes.length - 6);
        put16(out, 0x100 + vnt.length + vvt.length + tokBytes.length);
        out.write(vnt, 0, vnt.length);
        out.write(vvt, 0, vvt.length);
        out.write(tokBytes, 0, tokBytes.length);
        return out.toByteArray();
    }

    private byte[] buildChunk(ParsedStatement st) throws AtariBasicSyntaxException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (st.payload != null) {
            out.write(st.stmtByte);
            out.write(st.payload, 0, st.payload.length);
            out.write(0x9B);
            return out.toByteArray();
        }
        out.write(st.stmtByte);
        int ret = st.args != null ? rec(st.args, out) : 0;
        if (ret == 0) {
            out.write(0x14);
        }
        return out.toByteArray();
    }

    private int rec(Node n, ByteArrayOutputStream out) throws AtariBasicSyntaxException {
        int useL = 0;
        int useR = 0;
        int prec = 0;
        int ret = 0;
        if (n instanceof TokNode t) {
            AtariBasicTokens.Token tok = t.token;
            prec = tok.prec();
            int p = tok.needParens();
            useL = p > 1 ? 1 : 0;
            useR = p != 0 ? 1 : 0;
            if (t.left != null && t.left instanceof TokNode lt && prec > lt.token.prec()) {
                out.write(0x2B);
                rec(t.left, out);
                out.write(0x2C);
            } else if (t.left != null) {
                rec(t.left, out);
            }
            out.write(tok.byteValue());
            if (tok == AtariBasicTokens.Token.COLON || tok == AtariBasicTokens.Token.THEN) {
                ret = 1;
            }
        } else if (n instanceof NumNode num) {
            AtariBcd bcd = AtariBcd.fromDouble(num.value);
            out.write(0x0E);
            out.write(bcd.exp());
            for (int i = 0; i < 5; i++) {
                out.write(bcd.digit(i));
            }
        } else if (n instanceof StrNode s) {
            out.write(0x0F);
            out.write(s.text.length);
            out.write(s.text, 0, s.text.length);
        } else if (n instanceof VarNode v) {
            if (v.id > 127) {
                out.write(0);
            }
            out.write(v.id ^ 0x80);
        }
        if (n.right != null) {
            if (useR == 0 && n.right instanceof TokNode rt && prec >= rt.token.prec() && prec > 0) {
                useR = 1;
                out.write(0x2B);
            } else if (useL != 0) {
                out.write(0x3A);
            }
            ret = rec(n.right, out);
            if (useR != 0) {
                out.write(0x2C);
                ret = 0;
            }
        }
        return ret;
    }

    private byte[] buildVnt() {
        ByteArrayOutputStream vnt = new ByteArrayOutputStream();
        for (int i = 0; i < varNames.size(); i++) {
            String name = varNames.get(i);
            VarKind kind = varKinds.get(i);
            for (int c = 0; c < name.length() - 1; c++) {
                vnt.write(name.charAt(c));
            }
            char last = name.charAt(name.length() - 1);
            if (kind == VarKind.ARRAY) {
                vnt.write(last);
                vnt.write('(' | 0x80);
            } else if (kind == VarKind.STRING) {
                vnt.write(last);
                vnt.write('$' | 0x80);
            } else {
                vnt.write(last | 0x80);
            }
        }
        vnt.write(0);
        return vnt.toByteArray();
    }

    private byte[] buildVvt() {
        ByteArrayOutputStream vvt = new ByteArrayOutputStream();
        for (int i = 0; i < varNames.size(); i++) {
            VarKind kind = varKinds.get(i);
            vvt.write(kind == VarKind.STRING ? 0x80 : kind == VarKind.ARRAY ? 0x40 : 0x00);
            vvt.write(i);
            for (int d = 0; d < 6; d++) {
                vvt.write(0);
            }
        }
        return vvt.toByteArray();
    }

    private static void put16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private AtariBasicSyntaxException fail(String message) {
        return failAt(message, pos);
    }

    private AtariBasicSyntaxException failAt(String message, int srcPos) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < srcPos && i < src.length(); i++) {
            if (src.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new AtariBasicSyntaxException(message, line, col);
    }
}
