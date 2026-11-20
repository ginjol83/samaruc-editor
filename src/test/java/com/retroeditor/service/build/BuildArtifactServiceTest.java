package com.retroeditor.service.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.retroeditor.model.ConfigModel;
import com.retroeditor.service.ProjectPlatformDetectionService.Platform;
import com.retroeditor.service.ProjectProfileService;

class BuildArtifactServiceTest {

    private final BuildArtifactService service = new BuildArtifactService();

    @Test
    void gameGearPrefersGgOverSmsArtifact() {
        assertEquals(List.of(".gg", ".sms"), service.smsRomExtensions("gg"));
        assertEquals(List.of(".gg", ".sms"), service.smsRomExtensions(" GG "));
    }

    @Test
    void masterSystemStillPrefersSmsArtifact() {
        assertEquals(List.of(".sms", ".gg"), service.smsRomExtensions("sms"));
        assertEquals(List.of(".sms", ".gg"), service.smsRomExtensions(null));
    }

    /**
     * Regresion: un proyecto detectado como Atari debe acabar compilando con
     * {@code -t atari}, no con el target de otra maquina CC65.
     */
    @Test
    void detectedAtariProjectCompilesWithAtariTarget() {
        ConfigModel model = new ConfigModel();
        model.setCc65Target(ConfigModel.CC65_TARGET_C64);

        new ProjectProfileService().apply(Platform.ATARI, model);

        assertEquals(List.of("-t", "atari"), service.cc65TargetArguments(model.getCc65Target()));
        assertEquals(".xex", service.cc65ArtifactExtension(service.normalizeCc65Target(model.getCc65Target())));
    }

    @Test
    void otherCc65TargetsKeepTheirFlagsAndExtensions() {
        assertEquals(List.of("-t", "c64"), service.cc65TargetArguments("c64"));
        assertEquals(".prg", service.cc65ArtifactExtension(service.normalizeCc65Target("c64")));
        assertEquals(List.of("-t", "nes"), service.cc65TargetArguments("nes"));
        assertEquals(".nes", service.cc65ArtifactExtension(service.normalizeCc65Target("nes")));
    }

    /**
     * Regresion: Caprice32 no carga el .cpc que genera zcc por defecto, asi que
     * el perfil CPC debe pedir siempre {@code -subtype=dsk}.
     */
    @Test
    void cpcBuildRequestsDskSubtypeForCaprice32() {
        assertEquals("dsk", service.z88dkDefaultSubtype("cpc"));
        assertEquals("dsk", service.z88dkDefaultSubtype(" CPC "));
    }

    @Test
    void otherZ88dkProfilesGetTheirSubtype() {
        assertEquals("gamegear", service.z88dkDefaultSubtype("gg"));
        assertEquals("rom", service.z88dkDefaultSubtype("msx"));
        assertNull(service.z88dkDefaultSubtype("spectrum"));
        assertNull(service.z88dkDefaultSubtype("sms"));
    }
}
