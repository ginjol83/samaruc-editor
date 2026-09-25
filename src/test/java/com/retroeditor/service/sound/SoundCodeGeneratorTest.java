package com.retroeditor.service.sound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.retroeditor.service.gameboy.GameBoyAudioPlayer;

class SoundCodeGeneratorTest {

    private final SoundCodeGenerator gen = new SoundCodeGenerator();

    private static GameBoyAudioPlayer.NoteStep step(String channel, int freq, int dur, int start, int vol) {
        return new GameBoyAudioPlayer.NoteStep(channel, freq > 0 ? "C4" : "Silencio (Rest)", freq, dur, start, vol, "50%");
    }

    // ======================================================================
    // Atari (POKEY)
    // ======================================================================

    @Test
    void atariSfxEmitsPureToneAndSixtyFourKDividers() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.ATARI, "Canal 1 (Tono)", 440, 6, "50%", 300, true);

        assertTrue(code.contains("#include <peekpoke.h>"));
        assertTrue(code.contains("#include <time.h>"));
        assertTrue(code.contains("static void delay(unsigned int ms)"));
        assertTrue(code.contains("while (clock() < end) { }"));
        assertTrue(code.contains("const unsigned char Canal_1_Tono_sound_data[] = {"));
        // 63921 / 440 = 145 -> AUDF = 144 (0x90); AUDC = $A0 | 6 = 0xA6
        assertTrue(code.contains("0x90, // AUDF = 144"));
        assertTrue(code.contains("0xA6   // AUDC = $A0 | volumen 6"));
        assertTrue(code.contains("POKE(0xD200, Canal_1_Tono_sound_data[0]);    // AUDF1"));
        assertTrue(code.contains("POKE(0xD201, Canal_1_Tono_sound_data[1]);    // AUDC1"));
        assertTrue(code.contains("POKE(0xD201, 0xA0);"));
    }

    @Test
    void atariSfxMapsChannelToRegisterPair() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.ATARI, "Canal 3 (Tono)", 523, 12, "50%", 100, false);

        assertFalse(code.contains("const unsigned char "));
        assertTrue(code.contains("POKE(0xD204, Canal_3_Tono_sound_data[0]);    // AUDF3"));
        assertTrue(code.contains("POKE(0xD205, Canal_3_Tono_sound_data[1]);    // AUDC3"));
    }

    @Test
    void atariMelodyEmitsDataArraysAndPokeyPlayer() {
        List<GameBoyAudioPlayer.NoteStep> steps = List.of(
                step("Canal 1", 262, 200, 0, 12),
                step("Canal 2", 330, 200, 200, 10),
                step("Silencio (Rest)", 0, 100, 400, 0));

        String code = gen.generateMelody(SoundCodeGenerator.Platform.ATARI, "bgm", steps, true);

        assertTrue(code.contains("#include <peekpoke.h>"));
        assertTrue(code.contains("#include <time.h>"));
        assertTrue(code.contains("const unsigned char bgm_melody_ch[] = {1, 2, 1};"));
        assertTrue(code.contains("const unsigned int bgm_melody_freqs[] = {262, 330, 0};"));
        assertTrue(code.contains("const unsigned char bgm_melody_vols[] = {12, 10, 0};"));
        assertTrue(code.contains("const unsigned int bgm_melody_durs[] = {200, 200, 100};"));
        assertTrue(code.contains("const unsigned int bgm_melody_starts[] = {0, 200, 400};"));
        assertTrue(code.contains("void play_bgm_melody(void) {"));
        assertTrue(code.contains("unsigned int div = (63921U / freq) - 1U;"));
        assertTrue(code.contains("0xA0 | (bgm_melody_vols[i] & 0x0F)"));
        assertTrue(code.contains("POKE(0xD201 + m * 2, 0xA0);"));
    }

    // ======================================================================
    // SMS / Game Gear (PSG SN76489)
    // ======================================================================

    @Test
    void smsSfxToneEmitsLatchHighAndAtten() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.SMS_GAME_GEAR, "Tono 1", 440, 6, "50%", 300, true);

        assertTrue(code.contains("#include <sms/sms.h>"));
        assertTrue(code.contains("const unsigned char Tono_1_sound_data[] = {"));
        // N = 111860 / 440 = 254 -> latch = 0x80 | 14 = 0x8E, alto = 0x0F, aten = 0x90 | 9 = 0x99
        assertTrue(code.contains("0x8E, // latch Ton.1: N=254 (bajo)"));
        assertTrue(code.contains("0x99  // atenuación 9 (15 - volumen 6)"));
        assertTrue(code.contains("SMS_writeToPSG(Tono_1_sound_data[0]);"));
        assertTrue(code.contains("while (vbls--) SMS_waitForVBlank();"));
    }

    @Test
    void smsSfxMapsChannelToRegisterByte() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.SMS_GAME_GEAR, "Tono 3", 440, 6, "50%", 100, false);

        // Tono 3 => PSG canal 2: latch 0xC0|14 = 0xCE, aten 0xD0|9 = 0xD9
        assertTrue(code.contains("0xCE, // latch Ton.3: N=254 (bajo)"));
        assertTrue(code.contains("0xD9  // atenuación 9 (15 - volumen 6)"));
        assertTrue(code.contains("SMS_writeToPSG(0xDF);                             // silencio"));
    }

    @Test
    void smsNoiseSfxUsesWhiteNoiseCommand() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.SMS_GAME_GEAR, "Ruido", 0, 6, "50%", 200, false);

        assertTrue(code.contains("0xE4,          // ruido blanco, rate 0"));
        assertTrue(code.contains("0xF9         // atenuación 9"));
        assertTrue(code.contains("SMS_writeToPSG(0xFF);"));
    }

    @Test
    void smsMelodyEmitsPsgPlayer() {
        List<GameBoyAudioPlayer.NoteStep> steps = List.of(
                step("Tono 2", 330, 200, 0, 10),
                step("Tono 1", 262, 200, 200, 12),
                step("Ruido", 0, 100, 400, 12));

        String code = gen.generateMelody(SoundCodeGenerator.Platform.SMS_GAME_GEAR, "bgm", steps, true);

        assertTrue(code.contains("#include <sms/sms.h>"));
        assertTrue(code.contains("const unsigned char bgm_melody_ch[] = {1, 0, 3};"));
        assertTrue(code.contains("const unsigned int bgm_melody_freqs[] = {330, 262, 0};"));
        assertTrue(code.contains("const unsigned char bgm_melody_vols[] = {10, 12, 12};"));
        assertTrue(code.contains("const unsigned int bgm_melody_durs[] = {200, 200, 100};"));
        assertTrue(code.contains("const unsigned int bgm_melody_starts[] = {0, 200, 400};"));
        assertTrue(code.contains("void play_bgm_melody(void) {"));
        assertTrue(code.contains("unsigned int n = 111860U / f;"));
        assertTrue(code.contains("SMS_writeToPSG((unsigned char)(0x80 | (c << 5) | (n & 0x0F)));"));
        assertTrue(code.contains("SMS_writeToPSG(0xE4);"));
        assertTrue(code.contains("SMS_writeToPSG((unsigned char)(0x90 | (m << 5) | 0x0F));"));
    }

    // ======================================================================
    // Amstrad CPC (AY-3-8912 / z88dk)
    // ======================================================================

    @Test
    void cpcSfxToneEmitsAyRegisters() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.CPC, "Tono 1", 440, 6, "50%", 300, false);

        assertTrue(code.contains("#include <time.h>"));
        assertTrue(code.contains("static void ay_write(unsigned char reg, unsigned char val) {"));
        assertTrue(code.contains("#asm\n"));
        assertTrue(code.contains("ld a, %11000000"));
        // TP = 62500 / 440 = 142 (0x8E); mezclador tono canal A = 0x3E
        assertTrue(code.contains("ay_write(0, 0x8E);        // tono A, byte bajo"));
        assertTrue(code.contains("ay_write(1, 0x00);        // tono A, byte alto"));
        assertTrue(code.contains("ay_write(7, 0x3E);         // mezclador: tono canal A, resto off"));
        assertTrue(code.contains("ay_write(8, 6);            // volumen canal A = 6 (0-15)"));
        assertTrue(code.contains("delay(300);"));
        assertTrue(code.contains("ay_write(8, 0);            // silencio"));
    }

    @Test
    void cpcSfxMapsChannelToRegisterPair() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.CPC, "Tono 2", 440, 6, "50%", 100, true);

        // Tono 2 => canal B: registros 2/3, mezclador 0x3D, volumen R9
        assertTrue(code.contains("ay_write(2, 0x8E);"));
        assertTrue(code.contains("ay_write(3, 0x00);"));
        assertTrue(code.contains("ay_write(7, 0x3D);"));
        assertTrue(code.contains("ay_write(9, 6);"));
        assertTrue(code.contains("ay_write(9, 0);"));
    }

    @Test
    void cpcNoiseSfxRoutesViaChannelA() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.CPC, "Ruido", 0, 6, "50%", 200, false);

        assertTrue(code.contains("ay_write(6, 8);               // periodo de ruido (0-31)"));
        assertTrue(code.contains("ay_write(7, 0x37);            // mezclador: ruido en canal A"));
        assertTrue(code.contains("ay_write(8, 6);            // volumen canal A = 6 (0-15)"));
        assertTrue(code.contains("ay_write(8, 0);"));
    }

    @Test
    void cpcMelodyEmitsAyPlayer() {
        List<GameBoyAudioPlayer.NoteStep> steps = List.of(
                step("Tono 2", 330, 200, 0, 10),
                step("Tono 1", 262, 200, 200, 12),
                step("Ruido", 0, 100, 400, 12));

        String code = gen.generateMelody(SoundCodeGenerator.Platform.CPC, "bgm", steps, true);

        assertTrue(code.contains("#include <time.h>"));
        assertTrue(code.contains("const unsigned char bgm_melody_ch[] = {1, 0, 3};"));
        assertTrue(code.contains("const unsigned int bgm_melody_freqs[] = {330, 262, 0};"));
        assertTrue(code.contains("const unsigned char bgm_melody_vols[] = {10, 12, 12};"));
        assertTrue(code.contains("const unsigned int bgm_melody_durs[] = {200, 200, 100};"));
        assertTrue(code.contains("const unsigned int bgm_melody_starts[] = {0, 200, 400};"));
        assertTrue(code.contains("void play_bgm_melody(void) {"));
        assertTrue(code.contains("unsigned int tp = 62500U / f;"));
        assertTrue(code.contains("ay_write(7, (unsigned char)(0x3F & ~(1U << c)));"));
        assertTrue(code.contains("ay_write(6, 8);                    // periodo de ruido"));
        assertTrue(code.contains("ay_write(8, vol);                 // volumen canal A"));
        assertTrue(code.contains("ay_write((unsigned char)(8 + m), 0);"));
    }

    // ======================================================================
    // ZX Spectrum 128 (AY-3-8912 / z88dk)
    // ======================================================================

    @Test
    void zxSfxToneUsesFffdBffdPorts() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.ZX_SPECTRUM, "Tono 1", 440, 6, "50%", 300, false);

        assertTrue(code.contains("#include <time.h>"));
        assertTrue(code.contains("static void ay_write(unsigned char reg, unsigned char val) {"));
        assertTrue(code.contains("ld bc, &fffd"));
        assertTrue(code.contains("ld bc, &bffd"));
        // TP = 110838 / 440 = 252 (0xFC); mezclador tono canal A = 0x3E
        assertTrue(code.contains("ay_write(0, 0xFC);        // tono A, byte bajo"));
        assertTrue(code.contains("ay_write(1, 0x00);        // tono A, byte alto"));
        assertTrue(code.contains("ay_write(7, 0x3E);         // mezclador: tono canal A, resto off"));
        assertTrue(code.contains("ay_write(8, 6);            // volumen canal A = 6 (0-15)"));
        assertTrue(code.contains("delay(300);"));
        assertTrue(code.contains("ay_write(8, 0);            // silencio"));
    }

    @Test
    void zxNoiseSfxRoutesViaChannelA() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.ZX_SPECTRUM, "Ruido", 0, 6, "50%", 200, false);

        assertTrue(code.contains("ld bc, &fffd"));
        assertTrue(code.contains("ay_write(6, 8);               // periodo de ruido (0-31)"));
        assertTrue(code.contains("ay_write(7, 0x37);            // mezclador: ruido en canal A"));
        assertTrue(code.contains("ay_write(8, 6);"));
    }

    @Test
    void zxMelodyEmitsAyPlayerWith110838() {
        List<GameBoyAudioPlayer.NoteStep> steps = List.of(
                step("Tono 2", 330, 200, 0, 10),
                step("Tono 1", 262, 200, 200, 12),
                step("Ruido", 0, 100, 400, 12));

        String code = gen.generateMelody(SoundCodeGenerator.Platform.ZX_SPECTRUM, "bgm", steps, true);

        assertTrue(code.contains("#include <time.h>"));
        assertTrue(code.contains("ld bc, &fffd"));
        assertTrue(code.contains("const unsigned char bgm_melody_ch[] = {1, 0, 3};"));
        assertTrue(code.contains("const unsigned int bgm_melody_freqs[] = {330, 262, 0};"));
        assertTrue(code.contains("const unsigned int bgm_melody_starts[] = {0, 200, 400};"));
        assertTrue(code.contains("void play_bgm_melody(void) {"));
        assertTrue(code.contains("unsigned int tp = 110838U / f;"));
        assertTrue(code.contains("ay_write(6, 8);                    // periodo de ruido"));
        assertTrue(code.contains("ay_write((unsigned char)(8 + m), 0);"));
    }

    // ======================================================================
    // Commodore 64 (SID 6581 / cc65)
    // ======================================================================

    @Test
    void c64SfxToneEmitsSidRegisters() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.C64, "Tono 1", 440, 6, "50%", 300, false);

        assertTrue(code.contains("#include <peekpoke.h>"));
        assertTrue(code.contains("#include <cbm.h>"));
        assertTrue(code.contains("while (frames--) waitvsync();"));
        // FREQ = 440 * 524288 / 30789 = 7493 (0x1D45); pulso 50% -> pw 2048 (0x800); sustain = 6
        assertTrue(code.contains("POKE(0xD400, 0x45);"));
        assertTrue(code.contains("POKE(0xD401, 0x1D);"));
        assertTrue(code.contains("POKE(0xD402, 0x00);"));
        assertTrue(code.contains("POKE(0xD403, 0x08);"));
        assertTrue(code.contains("POKE(0xD404, 0x41);"));
        assertTrue(code.contains("POKE(0xD406, 0x60);"));
        assertTrue(code.contains("POKE(0xD418, 0x0F);"));
        assertTrue(code.contains("delay(300);"));
        assertTrue(code.contains("POKE(0xD404, 0x00);"));
        assertTrue(code.contains("POKE(0xD418, 0x00);"));
    }

    @Test
    void c64SfxMapsChannelToVoiceAndUsesDuty() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.C64, "Tono 3", 440, 12, "25%", 100, false);

        // Tono 3 => voz 3 ($D40E); duty 25% -> pw 1024 (0x400), byte alto = 0x04
        assertTrue(code.contains("POKE(0xD40E, 0x45);"));
        assertTrue(code.contains("POKE(0xD40F, 0x1D);"));
        assertTrue(code.contains("POKE(0xD410, 0x00);"));
        assertTrue(code.contains("POKE(0xD411, 0x04);"));
        assertTrue(code.contains("POKE(0xD412, 0x41);"));
        assertTrue(code.contains("POKE(0xD414, 0xC0);"));
    }

    @Test
    void c64NoiseSfxRoutesViaVoiceThree() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.C64, "Ruido", 0, 6, "50%", 200, false);

        assertTrue(code.contains("POKE(0xD40E, 0x00);"));
        assertTrue(code.contains("POKE(0xD40F, 0x00);"));
        assertTrue(code.contains("POKE(0xD412, 0x81);"));
        assertTrue(code.contains("POKE(0xD414, 0x60);"));
    }

    @Test
    void c64MelodyEmitsSidPlayer() {
        List<GameBoyAudioPlayer.NoteStep> steps = List.of(
                step("Tono 2", 330, 200, 0, 10),
                step("Tono 1", 262, 200, 200, 12),
                step("Ruido", 0, 100, 400, 12));

        String code = gen.generateMelody(SoundCodeGenerator.Platform.C64, "bgm", steps, true);

        assertTrue(code.contains("#include <cbm.h>"));
        assertTrue(code.contains("const unsigned char bgm_melody_ch[] = {1, 0, 3};"));
        assertTrue(code.contains("const unsigned int bgm_melody_freqs[] = {330, 262, 0};"));
        assertTrue(code.contains("const unsigned int bgm_melody_starts[] = {0, 200, 400};"));
        assertTrue(code.contains("void play_bgm_melody(void) {"));
        assertTrue(code.contains("((unsigned long)f * 524288UL + 15394UL) / 30789UL : 0;"));
        assertTrue(code.contains("unsigned int addr = 0xD400U + v * 7U;"));
        assertTrue(code.contains("bgm_melody_ch[i] == 3) ? 0x81 : 0x41"));
        assertTrue(code.contains("bgm_melody_vols[i] & 0x0F) << 4"));
        assertTrue(code.contains("POKE(0xD404 + m * 7, 0x00);"));
    }

    // ======================================================================
    // Game Boy (delegación, comportamiento original)
    // ======================================================================

    @Test
    void gameBoySfxStillEmitsApuRegisterWrites() {
        String code = gen.generateSfx(SoundCodeGenerator.Platform.GAMEBOY, "Square 1 (Sweep)", 440, 10, "50%", 300, true);

        assertTrue(code.contains("// Generated by Samaruc - Game Boy Audio Editor"));
        assertTrue(code.contains("NR52_REG = 0x80;"));
        assertTrue(code.contains("NR11_REG = 0x80;"));
    }

    @Test
    void gameBoyMelodyStillEmitsGbFreqTable() {
        List<GameBoyAudioPlayer.NoteStep> steps = List.of(
                step("Square 1", 262, 200, 0, 12),
                step("Square 2", 330, 200, 200, 10));

        String code = gen.generateMelody(SoundCodeGenerator.Platform.GAMEBOY, "spr", steps, true);

        assertTrue(code.contains("// Generated by Samaruc - Game Boy Timeline Melody Composer"));
        assertTrue(code.contains("unsigned int gb_freq = (f > 0) ? (2048 - (131072 / f)) : 0;"));
        assertTrue(code.contains("spr_melody_starts[] = {0, 200};"));
    }
}