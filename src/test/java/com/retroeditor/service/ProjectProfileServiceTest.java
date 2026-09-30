package com.retroeditor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.service.ProjectPlatformDetectionService.Platform;

class ProjectProfileServiceTest {

    private final ProjectProfileService service = new ProjectProfileService();

    @Test
    void mapsEveryCompiledPlatformToItsOwnProfile() {
        assertEquals("dialog.project.detected.profile.gameboy", profile(Platform.GAMEBOY).bundleKey());
        assertEquals("dialog.project.detected.profile.spectrum", profile(Platform.SPECTRUM).bundleKey());
        assertEquals("dialog.project.detected.profile.cpc", profile(Platform.CPC).bundleKey());
        assertEquals("dialog.project.detected.profile.msx", profile(Platform.MSX).bundleKey());
        assertEquals("dialog.project.detected.profile.sms", profile(Platform.SMS).bundleKey());
        assertEquals("dialog.project.detected.profile.gameGear", profile(Platform.GAME_GEAR).bundleKey());
        assertEquals("dialog.project.detected.profile.atari", profile(Platform.ATARI).bundleKey());
        assertEquals("dialog.project.detected.profile.c64", profile(Platform.C64).bundleKey());
        assertEquals("dialog.project.detected.profile.nes", profile(Platform.NES).bundleKey());
    }

    @Test
    void offersNoProfileForPlatformsWithoutAnIntegratedToolchain() {
        assertTrue(service.profileFor(Platform.UNKNOWN).isEmpty());
        assertTrue(service.profileFor(null).isEmpty());
    }

    /**
     * Regresion del fallo original: un proyecto SMS caia en el perfil de ZX Spectrum
     * porque el mapeo directo no tenia caso propio para SMS.
     */
    @Test
    void smsIsNeverReportedAsASpectrumProfile() {
        ConfigModel model = new ConfigModel();
        model.applySpectrumProfile();

        assertFalse(service.isCurrentProfile(Platform.SMS, model),
            "an SMS project must not be considered to already be on a ZX Spectrum profile");
        assertFalse(service.isCurrentProfile(Platform.GAME_GEAR, model));
        assertFalse(service.isCurrentProfile(Platform.MSX, model));
    }

    @Test
    void neverClaimsAProfileForUndetectedOrUncompiledPlatforms() {
        ConfigModel model = new ConfigModel();
        model.applyGameBoyProfile();

        assertFalse(service.isCurrentProfile(Platform.UNKNOWN, model));
        assertFalse(service.isCurrentProfile(null, model));
    }

    @Test
    void recognisesTheProfileThatIsAlreadyActive() {
        ConfigModel gameBoy = new ConfigModel();
        gameBoy.applyGameBoyProfile();
        assertTrue(service.isCurrentProfile(Platform.GAMEBOY, gameBoy));

        ConfigModel spectrum = new ConfigModel();
        spectrum.applySpectrumProfile();
        assertTrue(service.isCurrentProfile(Platform.SPECTRUM, spectrum));

        ConfigModel cpc = new ConfigModel();
        cpc.applyCpcProfile();
        assertTrue(service.isCurrentProfile(Platform.CPC, cpc));

        ConfigModel msx = new ConfigModel();
        msx.applyMsxProfile();
        assertTrue(service.isCurrentProfile(Platform.MSX, msx));

        ConfigModel sms = new ConfigModel();
        sms.applySmsProfile();
        assertTrue(service.isCurrentProfile(Platform.SMS, sms));

        ConfigModel gameGear = new ConfigModel();
        gameGear.applyGameGearProfile();
        assertTrue(service.isCurrentProfile(Platform.GAME_GEAR, gameGear));

        ConfigModel atari = new ConfigModel();
        atari.applyAtariProfile();
        assertTrue(service.isCurrentProfile(Platform.ATARI, atari));

        ConfigModel c64 = new ConfigModel();
        c64.applyC64Profile();
        assertTrue(service.isCurrentProfile(Platform.C64, c64));

        ConfigModel nes = new ConfigModel();
        nes.applyNesProfile();
        assertTrue(service.isCurrentProfile(Platform.NES, nes));
    }

    @Test
    void asksForConfirmationWhenAnotherProfileIsActive() {
        ConfigModel model = new ConfigModel();
        model.applyGameBoyProfile();

        assertFalse(service.isCurrentProfile(Platform.SPECTRUM, model));
        assertFalse(service.isCurrentProfile(Platform.C64, model));
    }

    /**
     * Atari y C64 comparten compilador, asi que solo el objetivo de cc65 los
     * distingue: un C64 no debe darse por Compatible con un perfil Atari.
     */
    @Test
    void distinguishesAtariFromC64ByTheCc65Target() {
        ConfigModel atari = new ConfigModel();
        atari.applyAtariProfile();
        assertFalse(service.isCurrentProfile(Platform.C64, atari));
        assertTrue(service.isCurrentProfile(Platform.ATARI, atari));

        ConfigModel c64 = new ConfigModel();
        c64.applyC64Profile();
        assertFalse(service.isCurrentProfile(Platform.ATARI, c64));
        assertTrue(service.isCurrentProfile(Platform.C64, c64));
    }

    @Test
    void distinguishesSpectrumFromCpcByTheZ88dkProfile() {
        ConfigModel spectrum = new ConfigModel();
        spectrum.applySpectrumProfile();
        assertFalse(service.isCurrentProfile(Platform.CPC, spectrum));

        ConfigModel cpc = new ConfigModel();
        cpc.applyCpcProfile();
        assertFalse(service.isCurrentProfile(Platform.SPECTRUM, cpc));
    }

    @Test
    void applySelectsTheMatchingProfileOnTheModel() {
        ConfigModel cpc = new ConfigModel();
        service.apply(Platform.CPC, cpc);
        assertTrue(service.isCurrentProfile(Platform.CPC, cpc));
        assertFalse(service.isCurrentProfile(Platform.SPECTRUM, cpc));

        ConfigModel sms = new ConfigModel();
        service.apply(Platform.SMS, sms);
        assertTrue(service.isCurrentProfile(Platform.SMS, sms));
        assertFalse(service.isCurrentProfile(Platform.SPECTRUM, sms));
        assertFalse(service.isCurrentProfile(Platform.GAME_GEAR, sms));

        ConfigModel nes = new ConfigModel();
        service.apply(Platform.NES, nes);
        assertTrue(service.isCurrentProfile(Platform.NES, nes));
        assertFalse(service.isCurrentProfile(Platform.ATARI, nes));
        assertFalse(service.isCurrentProfile(Platform.C64, nes));

        ConfigModel c64 = new ConfigModel();
        service.apply(Platform.C64, c64);
        assertTrue(service.isCurrentProfile(Platform.C64, c64));
        assertFalse(service.isCurrentProfile(Platform.ATARI, c64));
    }

    @Test
    void applyIsANoOpForPlatformsWithoutAnIntegratedToolchain() {
        ConfigModel untouched = new ConfigModel();
        untouched.applyGameBoyProfile();
        ConfigModel before = new ConfigModel();
        before.applyGameBoyProfile();

        service.apply(Platform.UNKNOWN, untouched);
        service.apply(null, untouched);
        service.apply(Platform.GAMEBOY, null);

        assertEquals(before.getConfigProperty("compilador_seleccionado", ""),
            untouched.getConfigProperty("compilador_seleccionado", ""));
        assertTrue(service.isCurrentProfile(Platform.GAMEBOY, untouched),
            "applying an unknown platform must not change the active profile");
    }

    private ProjectProfileService.Profile profile(Platform platform) {
        Optional<ProjectProfileService.Profile> profile = service.profileFor(platform);
        assertTrue(profile.isPresent(), platform + " must have a profile");
        assertFalse(profile.get().fallbackName().isBlank(), platform + " must have a fallback name");
        return profile.get();
    }
}
