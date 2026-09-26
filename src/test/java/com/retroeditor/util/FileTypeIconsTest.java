package com.retroeditor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.retroeditor.util.FileTypeIcons.Kind;

class FileTypeIconsTest {

    @Test
    void classifiesCommonSourceExtensions() {
        assertEquals(Kind.C, FileTypeIcons.kindForFileName("main.c"));
        assertEquals(Kind.C, FileTypeIcons.kindForFileName("MAIN.C"));
        assertEquals(Kind.HEADER, FileTypeIcons.kindForFileName("regs.h"));
        assertEquals(Kind.ASM, FileTypeIcons.kindForFileName("boot.asm"));
        assertEquals(Kind.ASM, FileTypeIcons.kindForFileName("util.s"));
        assertEquals(Kind.ASM, FileTypeIcons.kindForFileName("code.inc"));
        assertEquals(Kind.ASM, FileTypeIcons.kindForFileName("game.z80"));
    }

    @Test
    void classifiesDocumentsAndConfig() {
        assertEquals(Kind.MARKDOWN, FileTypeIcons.kindForFileName("README.md"));
        assertEquals(Kind.MARKDOWN, FileTypeIcons.kindForFileName("doc.markdown"));
        assertEquals(Kind.TEXT, FileTypeIcons.kindForFileName("notes.txt"));
        assertEquals(Kind.JSON, FileTypeIcons.kindForFileName("config.json"));
        assertEquals(Kind.JSON, FileTypeIcons.kindForFileName("app.properties"));
    }

    @Test
    void classifiesRetroAssets() {
        assertEquals(Kind.SMT, FileTypeIcons.kindForFileName("dialog.smt"));
        assertEquals(Kind.TILE, FileTypeIcons.kindForFileName("sprites.tile"));
        assertEquals(Kind.TILEMAP, FileTypeIcons.kindForFileName("level.tilemap"));
        assertEquals(Kind.ROM, FileTypeIcons.kindForFileName("game.gb"));
        assertEquals(Kind.ROM, FileTypeIcons.kindForFileName("game.gbc"));
        assertEquals(Kind.IMAGE, FileTypeIcons.kindForFileName("sprite.png"));
        assertEquals(Kind.AUDIO, FileTypeIcons.kindForFileName("song.vgm"));
    }

    @Test
    void classifiesMakefilesAndFallbacks() {
        assertEquals(Kind.MAKE, FileTypeIcons.kindForFileName("makefile"));
        assertEquals(Kind.MAKE, FileTypeIcons.kindForFileName("Makefile"));
        assertEquals(Kind.MAKE, FileTypeIcons.kindForFileName("rules.mk"));
        assertEquals(Kind.MAKE, FileTypeIcons.kindForFileName("proj.mak"));
        assertEquals(Kind.GENERIC, FileTypeIcons.kindForFileName("unknown.xyz"));
        assertEquals(Kind.GENERIC, FileTypeIcons.kindForFileName("noext"));
        assertEquals(Kind.GENERIC, FileTypeIcons.kindForFileName(null));
    }

    @Test
    void glyphsAreNonEmptyForAllKinds() {
        for (Kind kind : Kind.values()) {
            assertTrue(FileTypeIcons.glyphForKind(kind).startsWith("fas-"),
                    "falta glifo para " + kind);
        }
    }
}