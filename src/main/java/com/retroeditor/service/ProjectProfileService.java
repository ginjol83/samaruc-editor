package com.retroeditor.service;

import java.util.Optional;

import com.retroeditor.model.ConfigModel;

/**
 * Traduce la plataforma detectada de un proyecto al perfil de compilacion que le
 * corresponde, y comprueba si el perfil ya activo es ese.
 *
 * <p>No todas las plataformas detectadas tienen toolchain integrada: SMS y Game
 * Gear se detectan y se emulan, pero todavia no se compilan desde el editor. Para
 * esas, {@link #profileFor} devuelve {@link Optional#empty()} en vez de devolver
 * el perfil de otra maquina, que es lo que hacia el mapeo directo del controlador
 * (un proyecto SMS se ofrecia como ZX Spectrum).
 */
public class ProjectProfileService {

    private static final String PROPERTY_COMPILER = "compilador_seleccionado";
    private static final String PROPERTY_Z88DK_PROFILE = "z88dk_profile";
    private static final String PROPERTY_CC65_TARGET = "cc65_target";

    private static final String COMPILER_GBDK = "GBDK";
    private static final String COMPILER_Z88DK = "z88dk";
    private static final String COMPILER_LEGACY_SPECTRUM = "Spectrum";
    private static final String COMPILER_CC65 = "cc65";
    private static final String Z88DK_PROFILE_SPECTRUM = "spectrum";
    private static final String Z88DK_PROFILE_CPC = "cpc";
    private static final String Z88DK_PROFILE_SMS = "sms";
    private static final String Z88DK_PROFILE_GAME_GEAR = "gg";
    private static final String Z88DK_PROFILE_MSX = "msx";
    private static final String CC65_TARGET_ATARI = "atari";
    private static final String CC65_TARGET_C64 = "c64";
    private static final String CC65_TARGET_NES = "nes";

    /**
     * Nombre de la plataforma tal y como se muestra en el dialogo, con su clave de
     * bundle y el texto de reserva usado si el bundle no la tiene.
     */
    public record Profile(String bundleKey, String fallbackName) {
    }

    /**
     * Perfil de compilacion asociado a una plataforma, o vacio si esa plataforma no
     * tiene toolchain integrada y por tanto no hay nada que aplicar.
     */
    public Optional<Profile> profileFor(ProjectPlatformDetectionService.Platform platform) {
        if (platform == null) {
            return Optional.empty();
        }
        return switch (platform) {
            case GAMEBOY -> Optional.of(new Profile("dialog.project.detected.profile.gameboy", "Game Boy (GBDK)"));
            case SPECTRUM -> Optional.of(new Profile("dialog.project.detected.profile.spectrum", "ZX Spectrum (z88dk)"));
            case CPC -> Optional.of(new Profile("dialog.project.detected.profile.cpc", "Amstrad CPC (Z88DK)"));
            case MSX -> Optional.of(new Profile("dialog.project.detected.profile.msx", "MSX (Z88DK)"));
            case SMS -> Optional.of(new Profile("dialog.project.detected.profile.sms", "Sega Master System (Z88DK)"));
            case GAME_GEAR -> Optional.of(new Profile("dialog.project.detected.profile.gameGear", "Sega Game Gear (Z88DK)"));
            case ATARI -> Optional.of(new Profile("dialog.project.detected.profile.atari", "Atari 8-bit (CC65)"));
            case C64 -> Optional.of(new Profile("dialog.project.detected.profile.c64", "Commodore 64 (CC65)"));
            case NES -> Optional.of(new Profile("dialog.project.detected.profile.nes", "Nintendo NES (CC65)"));
            case UNKNOWN -> Optional.empty();
        };
    }

    /**
     * Indica si el perfil ya activo en la configuracion es el de esa plataforma, en
     * cuyo caso no hay que preguntar nada al usuario.
     */
    public boolean isCurrentProfile(ProjectPlatformDetectionService.Platform platform, ConfigModel model) {
        if (platform == null || model == null) {
            return false;
        }
        return switch (platform) {
            case GAMEBOY -> isGameBoy(model);
            case SPECTRUM -> isSpectrum(model);
            case CPC -> isCpc(model);
            case MSX -> isMsx(model);
            case SMS -> isSms(model);
            case GAME_GEAR -> isGameGear(model);
            case ATARI -> isAtari(model);
            case C64 -> isC64(model);
            case NES -> isNes(model);
            case UNKNOWN -> false;
        };
    }

    /**
     * Aplica el perfil de la plataforma sobre la configuracion. Las plataformas sin
     * toolchain integrada se ignoran.
     */
    public void apply(ProjectPlatformDetectionService.Platform platform, ConfigModel model) {
        if (platform == null || model == null) {
            return;
        }
        switch (platform) {
            case GAMEBOY -> model.applyGameBoyProfile();
            case SPECTRUM -> model.applySpectrumProfile();
            case CPC -> model.applyCpcProfile();
            case MSX -> model.applyMsxProfile();
            case SMS -> model.applySmsProfile();
            case GAME_GEAR -> model.applyGameGearProfile();
            case ATARI -> model.applyAtariProfile();
            case C64 -> model.applyC64Profile();
            case NES -> model.applyNesProfile();
            case UNKNOWN -> { }
        }
    }

    private boolean isGameBoy(ConfigModel model) {
        return COMPILER_GBDK.equalsIgnoreCase(compiler(model));
    }

    private boolean isSpectrum(ConfigModel model) {
        if (!isZ88dk(model)) {
            return false;
        }
        return Z88DK_PROFILE_SPECTRUM.equalsIgnoreCase(z88dkProfile(model));
    }

    private boolean isCpc(ConfigModel model) {
        if (!isZ88dk(model)) {
            return false;
        }
        return Z88DK_PROFILE_CPC.equalsIgnoreCase(z88dkProfile(model));
    }

    private boolean isMsx(ConfigModel model) {
        if (!isZ88dk(model)) {
            return false;
        }
        return Z88DK_PROFILE_MSX.equalsIgnoreCase(z88dkProfile(model));
    }

    private boolean isSms(ConfigModel model) {
        if (!isZ88dk(model)) {
            return false;
        }
        return Z88DK_PROFILE_SMS.equalsIgnoreCase(z88dkProfile(model));
    }

    private boolean isGameGear(ConfigModel model) {
        if (!isZ88dk(model)) {
            return false;
        }
        return Z88DK_PROFILE_GAME_GEAR.equalsIgnoreCase(z88dkProfile(model));
    }

    private boolean isAtari(ConfigModel model) {
        return COMPILER_CC65.equalsIgnoreCase(compiler(model))
            && CC65_TARGET_ATARI.equalsIgnoreCase(cc65Target(model));
    }

    private boolean isC64(ConfigModel model) {
        return COMPILER_CC65.equalsIgnoreCase(compiler(model))
            && CC65_TARGET_C64.equalsIgnoreCase(cc65Target(model));
    }

    private boolean isNes(ConfigModel model) {
        return COMPILER_CC65.equalsIgnoreCase(compiler(model))
            && CC65_TARGET_NES.equalsIgnoreCase(cc65Target(model));
    }

    private boolean isZ88dk(ConfigModel model) {
        String compiler = compiler(model);
        return COMPILER_Z88DK.equalsIgnoreCase(compiler) || COMPILER_LEGACY_SPECTRUM.equalsIgnoreCase(compiler);
    }

    private String compiler(ConfigModel model) {
        return model.getConfigProperty(PROPERTY_COMPILER, COMPILER_GBDK);
    }

    private String z88dkProfile(ConfigModel model) {
        return model.getConfigProperty(PROPERTY_Z88DK_PROFILE, Z88DK_PROFILE_SPECTRUM);
    }

    private String cc65Target(ConfigModel model) {
        return model.getConfigProperty(PROPERTY_CC65_TARGET, CC65_TARGET_ATARI);
    }
}
