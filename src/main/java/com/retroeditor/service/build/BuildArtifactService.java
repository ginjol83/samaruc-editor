package com.retroeditor.service.build;

import java.util.List;

/**
 * Resolucion pura de targets de compilacion y extensiones de artefacto.
 *
 * <p>Vive fuera de {@code BuildController} para poder fijar con tests de
 * regresion dos casos que ya fallaron: un proyecto Game Gear debe preferir su
 * {@code .gg} aunque quede un {@code .sms} antiguo en {@code out/}, y un
 * proyecto Atari detectado debe compilar con {@code -t atari}.
 */
public class BuildArtifactService {

    public static final String CC65_TARGET_ATARI = "atari";
    public static final String CC65_TARGET_C64 = "c64";
    public static final String CC65_TARGET_NES = "nes";

    public static final String Z88DK_PROFILE_SPECTRUM = "spectrum";
    public static final String Z88DK_PROFILE_CPC = "cpc";
    public static final String Z88DK_PROFILE_SMS = "sms";
    public static final String Z88DK_PROFILE_GAME_GEAR = "gg";
    public static final String Z88DK_PROFILE_MSX = "msx";

    public String normalizeCc65Target(String target) {
        if (target != null && target.trim().equalsIgnoreCase(CC65_TARGET_C64)) {
            return CC65_TARGET_C64;
        }
        if (target != null && target.trim().equalsIgnoreCase(CC65_TARGET_NES)) {
            return CC65_TARGET_NES;
        }
        return CC65_TARGET_ATARI;
    }

    public String cc65TargetFlag(String normalizedTarget) {
        if (CC65_TARGET_NES.equals(normalizedTarget)) {
            return CC65_TARGET_NES;
        }
        if (CC65_TARGET_C64.equals(normalizedTarget)) {
            return CC65_TARGET_C64;
        }
        return CC65_TARGET_ATARI;
    }

    public String cc65ArtifactExtension(String normalizedTarget) {
        if (CC65_TARGET_NES.equals(normalizedTarget)) {
            return ".nes";
        }
        if (CC65_TARGET_C64.equals(normalizedTarget)) {
            return ".prg";
        }
        return ".xex";
    }

    public List<String> cc65ArtifactExtensions(String normalizedTarget) {
        if (CC65_TARGET_NES.equals(normalizedTarget)) {
            return List.of(".nes");
        }
        if (CC65_TARGET_C64.equals(normalizedTarget)) {
            return List.of(".prg", ".d64");
        }
        return List.of(".xex", ".com", ".atr", ".exe");
    }

    /** Argumentos {@code -t <target>} que recibe cl65 para el target normalizado. */
    public List<String> cc65TargetArguments(String target) {
        return List.of("-t", cc65TargetFlag(normalizeCc65Target(target)));
    }

    /**
     * Extensiones de ROM SMS/Game Gear en orden de preferencia. Para Game Gear
     * el {@code .gg} va primero para no lanzar un {@code .sms} de una
     * compilacion anterior.
     */
    public List<String> smsRomExtensions(String z88dkProfile) {
        if (z88dkProfile != null && Z88DK_PROFILE_GAME_GEAR.equalsIgnoreCase(z88dkProfile.trim())) {
            return List.of(".gg", ".sms");
        }
        return List.of(".sms", ".gg");
    }

    public String normalizeZ88dkProfile(String profile) {
        String cleaned = profile != null ? profile.trim() : null;
        if (Z88DK_PROFILE_CPC.equalsIgnoreCase(cleaned)) {
            return Z88DK_PROFILE_CPC;
        }
        if (Z88DK_PROFILE_SMS.equalsIgnoreCase(cleaned)) {
            return Z88DK_PROFILE_SMS;
        }
        if (Z88DK_PROFILE_GAME_GEAR.equalsIgnoreCase(cleaned)) {
            return Z88DK_PROFILE_GAME_GEAR;
        }
        if (Z88DK_PROFILE_MSX.equalsIgnoreCase(cleaned)) {
            return Z88DK_PROFILE_MSX;
        }
        return Z88DK_PROFILE_SPECTRUM;
    }

    /**
     * Subtype que zcc necesita para producir el artefacto que espera el
     * emulador. CPC debe pedir {@code dsk} porque Caprice32 no carga el
     * {@code .cpc} por defecto.
     */
    public String z88dkDefaultSubtype(String z88dkProfile) {
        String profile = normalizeZ88dkProfile(z88dkProfile);
        if (Z88DK_PROFILE_CPC.equals(profile)) {
            return "dsk";
        }
        if (Z88DK_PROFILE_MSX.equals(profile)) {
            return "rom";
        }
        if (Z88DK_PROFILE_GAME_GEAR.equals(profile)) {
            return "gamegear";
        }
        return null;
    }
}
