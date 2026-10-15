package com.retroeditor.service.atari.basic;

public final class AtariBasicTokens {

    private AtariBasicTokens() {
    }

    public enum Statement {
        REM("REM", 0),
        DATA("DATA", 1),
        INPUT("INPUT", 1),
        COLOR("COLOR", 1),
        LIST("LIST", 1),
        ENTER("ENTER", 1),
        LET("LET", 2),
        IF("IF", 2),
        FOR("FOR", 1),
        NEXT("NEXT", 1),
        GOTO("GOTO", 1),
        GO_TO("GO TO", 1),
        GOSUB("GOSUB", 3),
        TRAP("TRAP", 1),
        BYE("BYE", 1),
        CONT("CONT", 3),
        COM("COM", 3),
        CLOSE("CLOSE", 2),
        CLR("CLR", 3),
        DEG("DEG", 2),
        DIM("DIM", 2),
        END("END", 3),
        NEW("NEW", 3),
        OPEN("OPEN", 1),
        LOAD("LOAD", 2),
        SAVE("SAVE", 1),
        STATUS("STATUS", 2),
        NOTE("NOTE", 2),
        POINT("POINT", 1),
        XIO("XIO", 1),
        ON("ON", 2),
        POKE("POKE", 3),
        PRINT("PRINT", 2),
        RAD("RAD", 2),
        READ("READ", 3),
        RESTORE("RESTORE", 3),
        RETURN("RETURN", 3),
        RUN("RUN", 2),
        STOP("STOP", 3),
        POP("POP", 3),
        PRINT_("?", 0),
        GET("GET", 2),
        PUT("PUT", 2),
        GRAPHICS("GRAPHICS", 2),
        PLOT("PLOT", 2),
        POSITION("POSITION", 3),
        DOS("DOS", 2),
        DRAWTO("DRAWTO", 2),
        SETCOLOR("SETCOLOR", 2),
        LOCATE("LOCATE", 3),
        SOUND("SOUND", 2),
        LPRINT("LPRINT", 2),
        CSAVE("CSAVE", 2),
        CLOAD("CLOAD", 4);

        private final String keyword;
        private final int min;

        Statement(String keyword, int min) {
            this.keyword = keyword;
            this.min = min;
        }

        public String keyword() {
            return keyword;
        }

        public int min() {
            return min;
        }

        public int byteValue() {
            return ordinal();
        }

        public static Statement forByte(int value) {
            Statement[] all = values();
            if (value < 0 || value >= all.length) {
                return null;
            }
            return all[value];
        }
    }

    public enum Token {
        QUOTE(null, 11, 0),
        DUMMY(null, 11, 0),
        COMMA(",", 0, 0),
        DOLAR(null, 11, 0),
        COLON(null, 11, 0),
        SEMICOLON(";", 0, 0),
        EOL(null, 11, 0),
        ON_GOTO("GOTO", -1, 0),
        ON_GOSUB("GOSUB", -1, 0),
        FOR_TO("TO", -1, 0),
        STEP("STEP", -1, 0),
        THEN("THEN", -1, 0),
        SHARP("#", 1, 0),
        N_LEQ("<=", 4, 0),
        N_NEQ("<>", 4, 0),
        N_GEQ(">=", 4, 0),
        N_LE("<", 4, 0),
        N_GE(">", 4, 0),
        N_EQ("=", 4, 0),
        CARET("^", 9, 0),
        STAR("*", 7, 0),
        PLUS("+", 6, 0),
        MINUS("-", 6, 0),
        SLASH("/", 7, 0),
        NOT("NOT", 5, 0),
        OR("OR", 2, 0),
        AND("AND", 3, 0),
        L_PRN("(", 11, 1),
        R_PRN(")", 11, 1),
        F_ASGN("=", -1, 0),
        S_ASGN("=", -1, 0),
        S_LEQ("<=", 11, 0),
        S_NEQ("<>", 11, 0),
        S_GEQ(">=", 11, 0),
        S_LE("<", 11, 0),
        S_GE(">", 11, 0),
        S_EQ("=", 11, 0),
        UPLUS("+", 10, 0),
        UMINUS("-", 10, 0),
        S_L_PRN(null, 13, 1),
        A_L_PRN(null, 13, 1),
        D_L_PRN(null, 13, 1),
        FN_PRN(null, 13, 1),
        DS_L_PRN(null, 13, 1),
        A_COMMA(null, 0, 0),
        STRP("STR$", 12, 3),
        CHRP("CHR$", 12, 3),
        USR("USR", 12, 3),
        ASC("ASC", 12, 3),
        VAL("VAL", 12, 3),
        LEN("LEN", 12, 3),
        ADR("ADR", 12, 3),
        ATN("ATN", 12, 3),
        COS("COS", 12, 3),
        PEEK("PEEK", 12, 3),
        SIN("SIN", 12, 3),
        RND("RND", 12, 3),
        FRE("FRE", 12, 3),
        EXP("EXP", 12, 3),
        LOG("LOG", 12, 3),
        CLOG("CLOG", 12, 3),
        SQR("SQR", 12, 3),
        SGN("SGN", 12, 3),
        ABS("ABS", 12, 3),
        INT("INT", 12, 3),
        PADDLE("PADDLE", 12, 3),
        STICK("STICK", 12, 3),
        PTRIG("PTRIG", 12, 3),
        STRIG("STRIG", 12, 3);

        private final String text;
        private final int prec;
        private final int needParens;

        Token(String text, int prec, int needParens) {
            this.text = text;
            this.prec = prec;
            this.needParens = needParens;
        }

        public String text() {
            return text;
        }

        public int prec() {
            return prec;
        }

        public int needParens() {
            return needParens;
        }

        public int byteValue() {
            return ordinal() + 0x10;
        }

        public static Token forByte(int value) {
            Token[] all = values();
            int idx = value - 0x10;
            if (idx < 0 || idx >= all.length) {
                return null;
            }
            return all[idx];
        }
    }
}
