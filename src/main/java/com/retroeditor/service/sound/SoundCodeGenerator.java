package com.retroeditor.service.sound;

import java.util.List;

import com.retroeditor.service.gameboy.GameBoyAudioPlayer;

/**
 * Generador de código de audio multiplataforma a partir de parámetros de SFX y
 * melodías con timeline: Game Boy (APU/GBDK), Atari 8-bit (POKEY/cc65),
 * Sega Master System / Game Gear (PSG SN76489 / SDCC),
 * Amstrad CPC (AY-3-8912 / z88dk), ZX Spectrum 128 (AY-3-8912 / z88dk),
 * MSX (AY-3-8910 / z88dk), NES (APU RP2A03 / cc65) y Commodore 64 (SID 6581 / cc65).
 */
public class SoundCodeGenerator {

    /** Plataformas soportadas por el editor de sonido. */
    public enum Platform { GAMEBOY, ATARI, SMS_GAME_GEAR, CPC, ZX_SPECTRUM, MSX, NES, C64 }

    /** Reloj del temporizador POKEY en modo 64 kHz (8 bits, por defecto con AUDCTL=0). */
    private static final int POKEY_CLOCK_64K = 63921;
    /** Forma de onda "pure tone" (sin polinomios): bits 5-7 = $A0. */
    private static final int POKEY_PURE_TONE = 0xA0;
    /** Dirección base de POKEY: AUDF1=$D200, AUDC1=$D201, y de ahí +2 por canal. */
    private static final int POKEY_BASE_ADDR = 0xD200;

    /**
     * Reloj del PSG SN76489 dividido por 32 (SMS/GG a 3,579545 MHz):
     * frecuencia = 3579545 / (32 * N) => N = 111860 / f.
     */
    private static final int SMS_PSG_CLOCK_DIV_32 = 111860;

    /**
     * Reloj del PSG AY-3-8912 del CPC (1 MHz) dividido por 16:
     * frecuencia = 1000000 / (16 * TP) => TP = 62500 / f.
     */
    private static final int CPC_AY_CLOCK_DIV_16 = 62500;

    public String generateSfx(Platform platform, String channel, int frequency, int volume,
                              String dutyCycle, int durationMs, boolean includeConst) {
        if (platform == Platform.ATARI) {
            return generateAtariSfx(channel, frequency, volume, durationMs, includeConst);
        }
        if (platform == Platform.SMS_GAME_GEAR) {
            return generateSmsSfx(channel, frequency, volume, durationMs, includeConst);
        }
        if (platform == Platform.CPC || platform == Platform.ZX_SPECTRUM || platform == Platform.MSX) {
            return generateAySfx(platform, channel, frequency, volume, durationMs);
        }
        if (platform == Platform.C64) {
            return generateSidSfx(channel, frequency, volume, dutyCycle, durationMs);
        }
        if (platform == Platform.NES) {
            return generateNesSfx(channel, frequency, volume, dutyCycle, durationMs, includeConst);
        }
        return generateGbSoundCode(channel, frequency, volume, dutyCycle, durationMs, includeConst);
    }

    public String generateMelody(Platform platform, String melodyName, List<GameBoyAudioPlayer.NoteStep> steps,
                                 boolean includeConst) {
        if (platform == Platform.ATARI) {
            return generateAtariMelody(melodyName, steps, includeConst);
        }
        if (platform == Platform.SMS_GAME_GEAR) {
            return generateSmsMelody(melodyName, steps, includeConst);
        }
        if (platform == Platform.CPC || platform == Platform.ZX_SPECTRUM || platform == Platform.MSX) {
            return generateAyMelody(platform, melodyName, steps, includeConst);
        }
        if (platform == Platform.C64) {
            return generateSidMelody(melodyName, steps, includeConst);
        }
        if (platform == Platform.NES) {
            return generateNesMelody(melodyName, steps, includeConst);
        }
        return generateGbMelodyCode(melodyName, steps, includeConst);
    }

    // ======================================================================
    // NES (APU RP2A03 / cc65)
    // ======================================================================

    /**
     * Reloj del RP2A03 en NTSC (1,789773 MHz). El canal de pulso divide por 16 y el triangular
     * por 32, de ahí los dos divisores de abajo. En PAL el reloj es 1,662607 MHz y los tonos
     * salen ~7 % agudos si se dejan estos valores.
     */
    private static final int NES_CPU_CLOCK_NTSC = 1789773;
    private static final int NES_PULSE_DIV_16 = 16;
    private static final int NES_TRIANGLE_DIV_32 = 32;
    /** Periodos del ruido ($400E bits 3-0): 0-15. */
    private static final int NES_NOISE_MAX_PERIOD = 15;

    /** Registro $4015: habilita los canales. Bit 0 = pulso 1, 1 = pulso 2, 2 = triangular, 3 = ruido. */
    private static int nesEnableBit(int channel) {
        return 1 << channel;
    }

    /** Duty del canal de pulso ($4000/$4004 bits 7-6) a partir del porcentaje del tracker. */
    private static int nesDuty(String dutyCycle) {
        if ("12.5%".equals(dutyCycle)) {
            return 0; // 00000001
        }
        if ("25%".equals(dutyCycle)) {
            return 1; // 10000001
        }
        if ("75%".equals(dutyCycle)) {
            return 3; // 01111110
        }
        return 2; // 10000111 = 50 %
    }

    /**
     * Timer del canal de pulso: f = reloj / (16 * (t + 1)), con t de 11 bits ($4002 y $4003 bits 2-0).
     */
    private static int nesPulseTimer(int frequency) {
        if (frequency <= 0) {
            return 0;
        }
        long t = (NES_CPU_CLOCK_NTSC + (long) NES_PULSE_DIV_16 * frequency / 2)
                / ((long) NES_PULSE_DIV_16 * frequency) - 1;
        return (int) Math.max(0, Math.min(2047, t));
    }

    /**
     * Timer del canal triangular: f = reloj / (32 * (t + 1)), también de 11 bits. El triangular
     * no tiene control de volumen (su DAC es fija a 4 bits), así que el volumen del tracker se
     * descarta para este canal.
     */
    private static int nesTriangleTimer(int frequency) {
        if (frequency <= 0) {
            return 0;
        }
        long t = (NES_CPU_CLOCK_NTSC + (long) NES_TRIANGLE_DIV_32 * frequency / 2)
                / ((long) NES_TRIANGLE_DIV_32 * frequency) - 1;
        return (int) Math.max(0, Math.min(2047, t));
    }

    /**
     * Periodo del ruido ($400E bits 3-0): la salida es reloj / (t + 1) / 16. El tracker pide una
     * frecuencia en Hz, así que se elige el periodo cuyo resultado más se acerca. Al ser el ruido
     * un LFSR de 15 bits con su propia escala, no se pretende una equivalencia exacta de tono.
     */
    private static int nesNoisePeriod(int frequency) {
        if (frequency <= 0) {
            return 0;
        }
        double target = NES_CPU_CLOCK_NTSC / 16.0 / frequency;
        int best = 0;
        double bestErr = Double.MAX_VALUE;
        for (int t = 0; t <= NES_NOISE_MAX_PERIOD; t++) {
            double err = Math.abs((t + 1) - target);
            if (err < bestErr) {
                bestErr = err;
                best = t;
            }
        }
        return best;
    }

    /**
     * Campos de la APU por canal, tal y como los nombra {@code <nes.h>} de cc65. El array se
     * indexa por el offset del registro: los cuatro del pulso 1 ($4000-$4003), los cuatro del
     * pulso 2 ($4004-$4007), el control y el timer del triangular ($4008, $400A, $400B) y los
     * del ruido ($400C, $400E, $400F).
     */
    private static final String APU_STATUS = "APU.status";
    private static final String APU_PULSE_CONTROL_0 = "APU.pulse[0].control";
    private static final String APU_PULSWEEP_0 = "APU.pulse[0].ramp";
    private static final String APU_PULSE_LOW_0 = "APU.pulse[0].period_low";
    private static final String APU_PULSE_HIGH_0 = "APU.pulse[0].len_period_high";
    private static final String APU_PULSE_CONTROL_1 = "APU.pulse[1].control";
    private static final String APU_PULSWEEP_1 = "APU.pulse[1].ramp";
    private static final String APU_PULSE_LOW_1 = "APU.pulse[1].period_low";
    private static final String APU_PULSE_HIGH_1 = "APU.pulse[1].len_period_high";
    private static final String APU_TRI_CONTROL = "APU.triangle.counter";
    private static final String APU_TRI_LOW = "APU.triangle.period_low";
    private static final String APU_TRI_HIGH = "APU.triangle.len_period_high";
    private static final String APU_NOISE_CONTROL = "APU.noise.control";
    private static final String APU_NOISE_PERIOD = "APU.noise.period";
    private static final String APU_NOISE_LEN = "APU.noise.len";

    /**
     * Emite las escrituras de los registros del canal. El triangular solo usa $4008/$400A/$400B
     * (el campo {@code unused} de $4009 no tiene control asociado) y el ruido $400C/$400E/$400F.
     */
    private void appendNesChannelWrites(StringBuilder sb, int ch, boolean noise, boolean triangle,
                                        int frequency, int volume, String dutyCycle) {
        int vol = volume & 0x0F;
        if (triangle) {
            int t = nesTriangleTimer(frequency);
            // $4008: bit 7 = control (activa el triangular), bits 6-0 = recarga del contador lineal.
            // El contador lineal a 0 y el control a 1 es lo habitual para un tono sostenido.
            sb.append(String.format("    %s = 0x80;           // $4008 control on, contador lineal 0%n", APU_TRI_CONTROL));
            sb.append(String.format("    %s = 0x%02X;           // $400A timer bajo = %d%n", APU_TRI_LOW, t & 0xFF, t & 0xFF));
            sb.append(String.format("    %s = 0x%02X;           // $400B timer alto (11 bits) = %d%n", APU_TRI_HIGH, (t >> 8) & 0x07, t));
            return;
        }
        if (noise) {
            int p = nesNoisePeriod(frequency);
            // $400C: bit 5 = modo del LFSR (0 = largo, 32767 pasos), bits 3-0 = volumen constante.
            sb.append(String.format("    %s = 0x%02X;           // $400C modo largo + volumen %d%n", APU_NOISE_CONTROL, vol, vol));
            // $400E: bit 7 = loop (0), bits 3-0 = periodo.
            sb.append(String.format("    %s = 0x%02X;           // $400E loop off + periodo %d%n", APU_NOISE_PERIOD, p, p));
            return;
        }
        // Canal de pulso 0 o 1: los registros van en bloques de 4 desde $4000 / $4004.
        int base = ch * 4;
        String control = ch == 0 ? APU_PULSE_CONTROL_0 : APU_PULSE_CONTROL_1;
        String sweep = ch == 0 ? APU_PULSWEEP_0 : APU_PULSWEEP_1;
        String low = ch == 0 ? APU_PULSE_LOW_0 : APU_PULSE_LOW_1;
        String high = ch == 0 ? APU_PULSE_HIGH_0 : APU_PULSE_HIGH_1;
        int t = nesPulseTimer(frequency);
        // $4000/$4004: bits 7-6 = duty, bit 5 = halt del contador de longitud (0 para poder pararlo),
        // bit 4 = volumen constante (1) y bits 3-0 = volumen 0-15.
        sb.append(String.format("    %s = 0x%02X;           // $%04X duty %d%% + volumen %d%n",
                control, (nesDuty(dutyCycle) << 6) | 0x10 | vol, 0x4000 + base,
                dutyPercent(dutyCycle), vol));
        // $4001/$4005: barrido desactivado (bit 7 = 0) para que el tono no se desvíe.
        sb.append(String.format("    %s = 0x00;           // $%04X barrido desactivado%n", sweep, 0x4001 + base));
        sb.append(String.format("    %s = 0x%02X;           // $%04X timer bajo = %d%n", low, t & 0xFF, 0x4002 + base, t & 0xFF));
        // $4003/$4007: bits 7-5 = longitud (0 = sin contador de longitud), bits 2-0 = timer alto.
        sb.append(String.format("    %s = 0x%02X;           // $%04X sin longitud + timer alto (11 bits) = %d%n",
                high, (t >> 8) & 0x07, 0x4003 + base, t));
    }

    private static int dutyPercent(String dutyCycle) {
        if ("12.5%".equals(dutyCycle)) return 12;
        if ("25%".equals(dutyCycle)) return 25;
        if ("75%".equals(dutyCycle)) return 75;
        return 50;
    }

    /** Silencia el canal apagando su control y su contador de longitud. */
    private void appendNesSilence(StringBuilder sb, int ch, boolean noise, boolean triangle) {
        if (triangle) {
            sb.append(String.format("    %s = 0x00;           // $4008 control off (silencio)%n", APU_TRI_CONTROL));
            sb.append(String.format("    %s = 0x00;           // $400B longitud 0%n", APU_TRI_HIGH));
            return;
        }
        if (noise) {
            sb.append(String.format("    %s = 0x00;           // $400C volumen 0 (silencio)%n", APU_NOISE_CONTROL));
            sb.append(String.format("    %s = 0x00;           // $400F longitud 0%n", APU_NOISE_LEN));
            return;
        }
        // Volumen constante a 0 ($4000/$4004 bits 3-0): el pulso se apaga sin tocar el timer.
        String control = ch == 0 ? APU_PULSE_CONTROL_0 : APU_PULSE_CONTROL_1;
        sb.append(String.format("    %s &= 0xF0;          // $%04X volumen 0 (silencio)%n", control, 0x4000 + ch * 4));
    }

    private String generateNesSfx(String channel, int frequency, int volume, String dutyCycle,
                                  int durationMs, boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        int ch = nesChannelIndex(channel);
        boolean noise = ch == 3;
        boolean triangle = ch == 2;
        String symbol = sanitizeSymbol(channel);

        sb.append("// Generated by Samaruc - NES SFX (APU RP2A03 / cc65)\n");
        sb.append("#include <nes.h>\n\n");
        appendNesDelayHelper(sb);
        sb.append("// ").append(channel).append(noise ? "" : " a " + frequency + " Hz")
          .append(", volumen ").append(volume).append(", duración ").append(durationMs).append(" ms.\n");
        if (!noise) {
            sb.append("// Timer de 11 bits: f = 1789773 / (")
              .append(triangle ? "32" : "16").append(" * (t + 1)) => t = ")
              .append(triangle ? nesTriangleTimer(frequency) : nesPulseTimer(frequency)).append('\n');
        } else {
            sb.append("// El ruido no tiene tono: $400E elige el periodo del LFSR (salida = 1789773 / (t + 1) / 16).\n");
        }
        sb.append("// $4015 habilita los canales con un 1 en su bit.\n");
        sb.append(String.format("// Canal %d (bit %d de $4015)%n", ch + 1, ch));
        if (triangle) {
            sb.append("// El canal triangular no tiene control de volumen: su DAC es fija y el tracker no lo usa.\n");
        }

        sb.append(String.format("void play_%s_effect(void) {%n", symbol));
        sb.append(String.format("    APU.status = 0x%02X;          // $4015 habilita solo este canal%n", nesEnableBit(ch)));
        appendNesChannelWrites(sb, ch, noise, triangle, frequency, volume, dutyCycle);
        sb.append(String.format("    delay(%d);%n", durationMs));
        appendNesSilence(sb, ch, noise, triangle);
        sb.append("    APU.status = 0x00;          // $4015 desactiva el canal y borra el longitud\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * delay(ms) por frames: el frame counter de la APU corre a 240 Hz en modo 4 pasos y el
     * vblank del NES es a 60,1 Hz (NTSC), así que 1 frame ≈ 16,64 ms. Es la misma aproximación
     * que usa el generador de C64 y evita depender de un reloj del sistema que no existe.
     */
    private void appendNesDelayHelper(StringBuilder sb) {
        sb.append("// delay(ms) por frames de vblank: el NES NTSC va a 60,098 Hz (16,64 ms por frame).\n");
        sb.append("static void delay(unsigned int ms) {\n");
        sb.append("    unsigned int frames = (ms * 60U + 98U) / 100U;\n");
        sb.append("    while (frames--) waitvsync();\n");
        sb.append("}\n\n");
    }

    private String generateNesMelody(String melodyName, List<GameBoyAudioPlayer.NoteStep> steps,
                                     boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        String safeName = sanitizeSymbol(melodyName);

        sb.append("// Generated by Samaruc - NES Timeline Melody Composer (APU RP2A03 / cc65)\n");
        sb.append("#include <nes.h>\n\n");
        appendNesDelayHelper(sb);

        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_ch", "canal APU 0-3",
                steps, step -> String.valueOf(nesChannelIndex(step.getChannel())));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_freqs", "frecuencia en Hz",
                steps, step -> String.valueOf(step.getFrequency()));
        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_vols", "volumen 0-15",
                steps, step -> String.valueOf(step.getVolume() & 0x0F));
        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_duty", "duty 0-3",
                steps, step -> String.valueOf(nesDuty(step.getDutyCycle())));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_durs", "duración en ms",
                steps, step -> String.valueOf(step.getDurationMs()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_starts", "inicio en ms",
                steps, step -> String.valueOf(step.getStartTimeMs()));
        sb.append("\n");

        sb.append(String.format("void play_%s_melody(void) {%n", safeName));
        // cc65 no admite declaraciones mezcladas con codigo: todo se declara al inicio.
        sb.append("    unsigned int current_time = 0;\n");
        sb.append("    unsigned int start;\n");
        sb.append("    unsigned int f;\n");
        sb.append("    unsigned int t;\n");
        sb.append("    unsigned char i;\n");
        sb.append("    unsigned char ch;\n");
        sb.append("    unsigned char vol;\n");
        sb.append("    unsigned char duty;\n");
        sb.append(String.format("    for (i = 0; i < %d; i++) {%n", steps.size()));
        sb.append(String.format("        start = %s_melody_starts[i];%n", safeName));
        sb.append("        if (start > current_time) {\n");
        sb.append("            delay(start - current_time);\n");
        sb.append("            current_time = start;\n");
        sb.append("        }\n");
        sb.append(String.format("        f = %s_melody_freqs[i];%n", safeName));
        sb.append(String.format("        ch = %s_melody_ch[i];%n", safeName));
        sb.append(String.format("        vol = %s_melody_vols[i];%n", safeName));
        sb.append(String.format("        duty = %s_melody_duty[i];%n", safeName));
        sb.append("        if (f > 0) {\n");
        sb.append("            t = (ch == 2) ? 1789773UL / (32UL * f) - 1UL\n");
        sb.append("                            : 1789773UL / (16UL * f) - 1UL;\n");
        sb.append("            APU.status = (unsigned char)(1 << ch);\n");
        sb.append("            if (ch == 2) {\n");
        sb.append("                APU.triangle.counter = 0x80;\n");
        sb.append("                APU.triangle.period_low = (unsigned char)(t & 0xFF);\n");
        sb.append("                APU.triangle.len_period_high = (unsigned char)((t >> 8) & 0x07);\n");
        sb.append("            } else if (ch == 3) {\n");
        sb.append("                APU.noise.control = vol;\n");
        sb.append("                APU.noise.period = (unsigned char)(t > 15 ? 15 : t);\n");
        sb.append("            } else {\n");
        sb.append("                APU.pulse[ch].control = (unsigned char)((duty << 6) | 0x10 | vol);\n");
        sb.append("                APU.pulse[ch].ramp = 0x00;\n");
        sb.append("                APU.pulse[ch].period_low = (unsigned char)(t & 0xFF);\n");
        sb.append("                APU.pulse[ch].len_period_high = (unsigned char)((t >> 8) & 0x07);\n");
        sb.append("            }\n");
        sb.append(String.format("            delay(%s_melody_durs[i]);%n", safeName));
        sb.append("            if (ch == 2) {\n");
        sb.append("                APU.triangle.counter = 0x00;\n");
        sb.append("            } else if (ch == 3) {\n");
        sb.append("                APU.noise.control = 0x00;\n");
        sb.append("            } else {\n");
        sb.append("                APU.pulse[ch].control &= 0xF0;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        current_time += ");
        sb.append(safeName).append("_melody_durs[i];\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Traduce el nombre del canal del editor al índice de la APU: 0 y 1 son los pulsos, 2 el
     * triangular y 3 el ruido. Acepta tanto los nombres de la UI de NES como los del Game Boy
     * por si un proyecto mezcla efectos de las dos plataformas.
     */
    private static int nesChannelIndex(String channel) {
        if (channel != null) {
            String c = channel.toLowerCase();
            if (c.contains("noise") || c.contains("ruido")) {
                return 3;
            }
            if (c.contains("triangle") || c.contains("triangular") || c.contains("wave")) {
                return 2;
            }
            if (c.contains("square 2") || c.contains("pulse 2") || c.contains("pulso 2") || c.contains("canal 2")) {
                return 1;
            }
        }
        return 0;
    }

    // ======================================================================
    // Commodore 64 (SID 6581 / cc65)
    // ======================================================================

    /** Base de registros del SID 6581: $D400-$D418; cada voz ocupa 7 registros. */
    private static final int SID_BASE_ADDR = 0xD400;
    /** Numerador reducido de 2^24 / 985248 (reloj PAL): 2^24 / 32. */
    private static final long SID_CLOCK_NUMER = 524288L;
    /** Denominador reducido de 985248 / 32. */
    private static final long SID_CLOCK_DENOM = 30789L;

    private static int sidFrequency(int hz) {
        if (hz <= 0) {
            return 0;
        }
        long v = (hz * SID_CLOCK_NUMER + SID_CLOCK_DENOM / 2) / SID_CLOCK_DENOM;
        return (int) v;
    }

    private static int sidPulseWidth(String dutyCycle) {
        if ("12.5%".equals(dutyCycle)) {
            return 512;
        }
        if ("25%".equals(dutyCycle)) {
            return 1024;
        }
        if ("75%".equals(dutyCycle)) {
            return 3072;
        }
        return 2048; // 50%
    }

    private String generateSidSfx(String channel, int frequency, int volume, String dutyCycle, int durationMs) {
        StringBuilder sb = new StringBuilder();
        int ch = psgChannelIndex(channel);
        boolean noise = ch == 3;
        int voice = noise ? 2 : ch;
        String symbol = sanitizeSymbol(channel);
        int freq = sidFrequency(frequency);
        int pw = sidPulseWidth(dutyCycle);
        int base = SID_BASE_ADDR + voice * 7;

        sb.append("// Generated by Samaruc - Commodore 64 SFX (SID 6581 / cc65)\n");
        sb.append("#include <peekpoke.h>\n");
        appendCc65DelayHelper(sb, Platform.C64);
        sb.append("// ").append(channel).append(noise ? "" : " a " + frequency + " Hz")
          .append(", volumen ").append(volume).append(", duración ").append(durationMs).append(" ms.\n");
        if (frequency > 0) {
            sb.append("// FREQ = f * 2^24 / 985248 (reloj PAL) = f * 524288 / 30789 => FREQ(")
              .append(frequency).append(" Hz) = ").append(freq).append(" (0x")
              .append(String.format("%04X", freq)).append(").\n");
        } else {
            sb.append("// Sin frecuencia fija: ruido con FREQ 0 (tasa mínima del registro de desplazamiento).\n");
        }
        sb.append("// Voz ").append(voice + 1).append(" en ").append(String.format("$%04X", base)).append("; volumen ")
          .append(volume & 0x0F).append(" = sustain del envelope (master en $D418).\n");
        sb.append("void play_").append(symbol).append("_effect(void) {\n");
        sb.append(String.format("    POKE(0x%04X, 0x%02X);             // voz %d: FREQ, byte bajo\n", base, freq & 0xFF, voice + 1));
        sb.append(String.format("    POKE(0x%04X, 0x%02X);             // voz %d: FREQ, byte alto\n", base + 1, (freq >> 8) & 0xFF, voice + 1));
        if (!noise) {
            sb.append(String.format("    POKE(0x%04X, 0x%02X);             // voz %d: ancho de pulso, byte bajo (pw = %d)\n", base + 2, pw & 0xFF, voice + 1, pw));
            sb.append(String.format("    POKE(0x%04X, 0x%02X);             // voz %d: ancho de pulso, byte alto\n", base + 3, (pw >> 8) & 0x0F, voice + 1));
        }
        sb.append(String.format("    POKE(0x%04X, 0x%02X);             // voz %d: control = %s + gate (0x01)\n",
                base + 4, noise ? 0x81 : 0x41, voice + 1, noise ? "ruido (0x80)" : "pulso (0x40)"));
        sb.append(String.format("    POKE(0x%04X, 0x00);             // voz %d: attack 0 / decay 0\n", base + 5, voice + 1));
        sb.append(String.format("    POKE(0x%04X, 0x%02X);             // voz %d: sustain %d / release 0\n",
                base + 6, (volume & 0x0F) << 4, voice + 1, volume & 0x0F));
        sb.append("    POKE(0xD418, 0x0F);                             // volumen maestro 15\n");
        sb.append("    delay(").append(durationMs).append(");\n");
        sb.append(String.format("    POKE(0x%04X, 0x00);             // voz %d: gate off (silencio)\n", base + 4, voice + 1));
        sb.append("    POKE(0xD418, 0x00);                             // volumen maestro 0\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateSidMelody(String melodyName, List<GameBoyAudioPlayer.NoteStep> steps,
                                     boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        String safeName = sanitizeSymbol(melodyName);

        sb.append("// Generated by Samaruc - Commodore 64 Timeline Melody Composer (SID 6581 / cc65)\n");
        sb.append("#include <peekpoke.h>\n");
        appendCc65DelayHelper(sb, Platform.C64);

        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_ch", "voz SID 0-2, 3 = ruido (voz 3)",
                steps, step -> String.valueOf(psgChannelIndex(step.getChannel())));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_freqs", "frecuencia en Hz",
                steps, step -> String.valueOf(step.getFrequency()));
        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_vols", "volumen 0-15 (sustain)",
                steps, step -> String.valueOf(step.getVolume()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_durs", "duración en ms",
                steps, step -> String.valueOf(step.getDurationMs()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_starts", "inicio en ms",
                steps, step -> String.valueOf(step.getStartTimeMs()));
        sb.append("\n");

        sb.append("void play_").append(safeName).append("_melody(void) {\n");
        sb.append("    unsigned int current_time = 0;\n");
        sb.append("    unsigned char i;\n");
        sb.append("    for (i = 0; i < ").append(steps.size()).append("; i++) {\n");
        sb.append("        unsigned int start = ").append(safeName).append("_melody_starts[i];\n");
        sb.append("        if (start > current_time) {\n");
        sb.append("            delay(start - current_time);\n");
        sb.append("            current_time = start;\n");
        sb.append("        }\n");
        sb.append("        unsigned int f = ").append(safeName).append("_melody_freqs[i];\n");
        sb.append("        unsigned int v = ").append(safeName).append("_melody_ch[i];\n");
        sb.append("        if (v == 3) v = 2;                        // ruido -> voz 3 (onda ruido)\n");
        sb.append("        unsigned int freq = (f > 0) ? ((unsigned long)f * 524288UL + 15394UL) / 30789UL : 0;\n");
        sb.append("        unsigned int addr = 0xD400U + v * 7U;\n");
        sb.append("        POKE(addr, (unsigned char)(freq & 0xFF));             // FREQ bajo\n");
        sb.append("        POKE(addr + 1, (unsigned char)((freq >> 8) & 0xFF));  // FREQ alto\n");
        sb.append("        POKE(addr + 2, 0x00);                                 // ancho de pulso (bajo, 50%)\n");
        sb.append("        POKE(addr + 3, 0x08);                                 // ancho de pulso (alto, 0x800)\n");
        sb.append("        POKE(addr + 4, (unsigned char)((").append(safeName).append("_melody_ch[i] == 3) ? 0x81 : 0x41));  // control: ruido/pulso + gate\n");
        sb.append("        POKE(addr + 5, 0x00);                                 // attack 0 / decay 0\n");
        sb.append("        POKE(addr + 6, (unsigned char)((")
          .append(safeName).append("_melody_vols[i] & 0x0F) << 4));             // sustain = volumen\n");
        sb.append("        POKE(0xD418, 0x0F);                                   // volumen maestro 15\n");
        sb.append("        delay(").append(safeName).append("_melody_durs[i]);\n");
        sb.append("        current_time += ").append(safeName).append("_melody_durs[i];\n");
        sb.append("    }\n");
        sb.append("    unsigned char m;\n");
        sb.append("    for (m = 0; m < 3; m++) {\n");
        sb.append("        POKE(0xD404 + m * 7, 0x00);                         // gate off (silencio)\n");
        sb.append("    }\n");
        sb.append("    POKE(0xD418, 0x00);                                     // volumen maestro 0\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Espera en ms para cc65: ni conio.h ni time.h lo traen incorporado.
     * C64 usa {@code waitvsync()} (CBM, ~50 Hz PAL / 20 ms); Atari usa {@code clock()}/RTCLOK
     * (tics por segundo según {@code CLOCKS_PER_SEC}, 60 NTSC / 50 PAL).
     */
    private void appendCc65DelayHelper(StringBuilder sb, Platform platform) {
        if (platform == Platform.C64) {
            sb.append("#include <cbm.h>\n\n");
            sb.append("// delay(ms) aproximado por vblank: PAL ~50 Hz (20 ms/frame); en NTSC ajustar a /17.\n");
            sb.append("static void delay(unsigned int ms) {\n");
            sb.append("    unsigned int frames = (ms + 19) / 20;\n");
            sb.append("    while (frames--) waitvsync();\n");
            sb.append("}\n\n");
        } else {
            sb.append("#include <time.h>\n\n");
            sb.append("// delay(ms) aproximado: clock()/RTCLOK en tics (CLOCKS_PER_SEC = 60 NTSC / 50 PAL).\n");
            sb.append("static void delay(unsigned int ms) {\n");
            sb.append("    clock_t end = clock() + (clock_t)(((unsigned long)ms * (unsigned long)CLOCKS_PER_SEC) / 1000UL);\n");
            sb.append("    while (clock() < end) { }\n");
            sb.append("}\n\n");
        }
    }

    // ======================================================================
    // Atari 8-bit (POKEY / cc65)
    // ======================================================================

    private String generateAtariSfx(String channel, int frequency, int volume, int durationMs, boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        int ch = atariChannelNumber(channel);
        int addrFreq = POKEY_BASE_ADDR + (ch - 1) * 2;
        int addrControl = addrFreq + 1;
        int divider = frequency > 0 ? Math.max(1, Math.min(255, (POKEY_CLOCK_64K / frequency) - 1)) : 1;
        int control = frequency > 0 ? (POKEY_PURE_TONE | (volume & 0x0F)) : POKEY_PURE_TONE;
        int approxHz = POKEY_CLOCK_64K / (divider + 1);
        String symbol = sanitizeSymbol(channel);

        sb.append("// Generated by Samaruc - Atari SFX (POKEY / cc65)\n");
        sb.append("#include <peekpoke.h>\n");
        appendCc65DelayHelper(sb, Platform.ATARI);
        sb.append("// ").append(channel).append(" a ").append(frequency).append(" Hz, volumen ").append(volume)
          .append(", duración ").append(durationMs).append(" ms.\n");
        sb.append("// Pure tone: AUDC = $A0 | volumen; frecuencia = 63921 / (AUDF + 1).\n");
        if (includeConst) {
            sb.append("const ");
        }
        sb.append("unsigned char ").append(symbol).append("_sound_data[] = {\n");
        sb.append(String.format("    0x%02X, // AUDF = %d -> %d Hz (63921 / %d)\n", divider, divider, approxHz, divider + 1));
        sb.append(String.format("    0x%02X   // AUDC = $A0 | volumen %d (pure tone)\n", control, volume & 0x0F));
        sb.append("};\n\n");

        sb.append("void play_").append(symbol).append("_effect(void) {\n");
        sb.append(String.format("    POKE(0x%04X, %s_sound_data[0]);    // AUDF%d\n", addrFreq, symbol, ch));
        sb.append(String.format("    POKE(0x%04X, %s_sound_data[1]);    // AUDC%d\n", addrControl, symbol, ch));
        sb.append("    delay(").append(durationMs).append(");\n");
        sb.append(String.format("    POKE(0x%04X, 0x%02X);              // silencio (volumen 0)\n", addrControl, POKEY_PURE_TONE));
        sb.append("}\n");

        return sb.toString();
    }

    private String generateAtariMelody(String melodyName, List<GameBoyAudioPlayer.NoteStep> steps,
                                       boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        String safeName = sanitizeSymbol(melodyName);

        sb.append("// Generated by Samaruc - Atari Timeline Melody Composer (POKEY / cc65)\n");
        sb.append("#include <peekpoke.h>\n");
        appendCc65DelayHelper(sb, Platform.ATARI);
        sb.append("// f = 63921 / (AUDF + 1); una nota con freq 0 silencia su canal (AUDC = $A0).\n\n");

        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_ch", "canal POKEY 1-4",
                steps, step -> String.valueOf(atariChannelNumber(step.getChannel())));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_freqs", "frecuencia en Hz",
                steps, step -> String.valueOf(step.getFrequency()));
        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_vols", "volumen 0-15",
                steps, step -> String.valueOf(step.getVolume()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_durs", "duración en ms",
                steps, step -> String.valueOf(step.getDurationMs()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_starts", "inicio en ms",
                steps, step -> String.valueOf(step.getStartTimeMs()));
        sb.append("\n");

        sb.append("void play_").append(safeName).append("_melody(void) {\n");
        sb.append("    unsigned int current_time = 0;\n");
        sb.append("    unsigned char i;\n");
        sb.append("    for (i = 0; i < ").append(steps.size()).append("; i++) {\n");
        sb.append("        unsigned int start = ").append(safeName).append("_melody_starts[i];\n");
        sb.append("        if (start > current_time) {\n");
        sb.append("            delay(start - current_time);\n");
        sb.append("            current_time = start;\n");
        sb.append("        }\n");
        sb.append("        unsigned int freq = ").append(safeName).append("_melody_freqs[i];\n");
        sb.append("        unsigned int addr = 0xD200U + (unsigned int)(")
          .append(safeName).append("_melody_ch[i] - 1) * 2U;\n");
        sb.append("        if (freq > 0) {\n");
        sb.append("            unsigned int div = (63921U / freq) - 1U;\n");
        sb.append("            POKE(addr, (unsigned char)(div & 0xFF));                       // AUDFn\n");
        sb.append("            POKE(addr + 1, (unsigned char)(0xA0 | (")
          .append(safeName).append("_melody_vols[i] & 0x0F)));   // AUDCn (pure tone)\n");
        sb.append("        } else {\n");
        sb.append("            POKE(addr + 1, 0xA0);                                       // silencio\n");
        sb.append("        }\n");
        sb.append("        unsigned int dur = ").append(safeName).append("_melody_durs[i];\n");
        sb.append("        delay(dur);\n");
        sb.append("        current_time += dur;\n");
        sb.append("    }\n");
        sb.append("    unsigned char m;\n");
        sb.append("    for (m = 0; m < 4; m++) {\n");
        sb.append("        POKE(0xD201 + m * 2, 0xA0);                                 // silencio final\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ======================================================================
    // Sega Master System / Game Gear (PSG SN76489 / SDCC - SMSlib)
    // ======================================================================

    private String generateSmsSfx(String channel, int frequency, int volume, int durationMs, boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        int ch = psgChannelIndex(channel);
        boolean noise = ch == 3;
        String symbol = sanitizeSymbol(channel);

        sb.append("// Generated by Samaruc - SMS / Game Gear SFX (PSG SN76489, SDCC)\n");
        sb.append("#include <sms/sms.h>\n\n");
        sb.append("// ").append(channel).append(noise ? "" : " a " + frequency + " Hz")
          .append(", volumen ").append(volume).append(", duración ").append(durationMs).append(" ms.\n");
        sb.append("// f = 3579545 / (32 * N) => N = 111860 / f (SMS/GG a 3,579545 MHz).\n");

        int att = 15 - (volume & 0x0F);
        if (noise) {
            if (includeConst) {
                sb.append("const ");
            }
            sb.append("unsigned char ").append(symbol).append("_sound_data[] = {\n");
            sb.append("    0xE4,          // ruido blanco, rate 0\n");
            sb.append(String.format("    0x%02X         // atenuación %d (15 - volumen)\n", 0xF0 | att, att));
            sb.append("};\n\n");

            sb.append("void play_").append(symbol).append("_effect(void) {\n");
            sb.append("    SMS_writeToPSG(").append(symbol).append("_sound_data[0]);\n");
            sb.append("    SMS_writeToPSG(").append(symbol).append("_sound_data[1]);\n");
            sb.append("    unsigned char vbls = (unsigned char)((").append(durationMs).append(" + 16) / 17); // ~16,7 ms/frame (60 Hz)\n");
            sb.append("    while (vbls--) SMS_waitForVBlank();\n");
            sb.append("    SMS_writeToPSG(0xFF);                    // silencio (atenuación 15 de ruido)\n");
            sb.append("}\n");
            return sb.toString();
        }

        int divider = frequency > 0 ? Math.max(1, Math.min(1023, SMS_PSG_CLOCK_DIV_32 / frequency)) : 1;
        int latchBase = 0x80 | (ch << 5);
        int attBase = 0x90 | (ch << 5);

        if (includeConst) {
            sb.append("const ");
        }
        sb.append("unsigned char ").append(symbol).append("_sound_data[] = {\n");
        sb.append(String.format("    0x%02X, // latch Ton.%d: N=%d (bajo)\n", latchBase | (divider & 0x0F), ch + 1, divider));
        sb.append(String.format("    0x%02X, // N=%d (alto 6 bits)\n", (divider >> 4) & 0x3F, divider));
        sb.append(String.format("    0x%02X  // atenuación %d (15 - volumen %d)\n", attBase | att, att, volume & 0x0F));
        sb.append("};\n\n");

        sb.append("void play_").append(symbol).append("_effect(void) {\n");
        sb.append("    SMS_writeToPSG(").append(symbol).append("_sound_data[0]);\n");
        sb.append("    SMS_writeToPSG(").append(symbol).append("_sound_data[1]);\n");
        sb.append("    SMS_writeToPSG(").append(symbol).append("_sound_data[2]);\n");
        sb.append("    unsigned char vbls = (unsigned char)((").append(durationMs).append(" + 16) / 17); // ~16,7 ms/frame (60 Hz)\n");
        sb.append("    while (vbls--) SMS_waitForVBlank();\n");
        sb.append(String.format("    SMS_writeToPSG(0x%02X);                             // silencio\n", attBase | 0x0F));
        sb.append("}\n");

        return sb.toString();
    }

    private String generateSmsMelody(String melodyName, List<GameBoyAudioPlayer.NoteStep> steps,
                                     boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        String safeName = sanitizeSymbol(melodyName);

        sb.append("// Generated by Samaruc - SMS / Game Gear Timeline Melody Composer (PSG SN76489, SDCC)\n");
        sb.append("#include <sms/sms.h>\n\n");
        sb.append("// wait_ms: espera aproximada usando VBlank (60 Hz NTSC, ~16,7 ms; para 50 Hz PAL usa /20).\n");
        sb.append("static void wait_ms(unsigned int ms) {\n");
        sb.append("    unsigned int vbls = (ms + 16) / 17;\n");
        sb.append("    while (vbls--) SMS_waitForVBlank();\n");
        sb.append("}\n\n");

        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_ch", "canal PSG 0-3 (3 = ruido)",
                steps, step -> String.valueOf(psgChannelIndex(step.getChannel())));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_freqs", "frecuencia en Hz",
                steps, step -> String.valueOf(step.getFrequency()));
        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_vols", "volumen 0-15",
                steps, step -> String.valueOf(step.getVolume()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_durs", "duración en ms",
                steps, step -> String.valueOf(step.getDurationMs()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_starts", "inicio en ms",
                steps, step -> String.valueOf(step.getStartTimeMs()));
        sb.append("\n");

        sb.append("void play_").append(safeName).append("_melody(void) {\n");
        sb.append("    unsigned int current_time = 0;\n");
        sb.append("    unsigned char i;\n");
        sb.append("    for (i = 0; i < ").append(steps.size()).append("; i++) {\n");
        sb.append("        unsigned int start = ").append(safeName).append("_melody_starts[i];\n");
        sb.append("        if (start > current_time) {\n");
        sb.append("            wait_ms(start - current_time);\n");
        sb.append("            current_time = start;\n");
        sb.append("        }\n");
        sb.append("        unsigned int f = ").append(safeName).append("_melody_freqs[i];\n");
        sb.append("        unsigned char c = ").append(safeName).append("_melody_ch[i];\n");
        sb.append("        if (c == 3) {\n");
        sb.append("            SMS_writeToPSG(0xE4);                    // ruido blanco, rate 0\n");
        sb.append("            SMS_writeToPSG((unsigned char)(0xF0 | (15 - (")
          .append(safeName).append("_melody_vols[i] & 0x0F))));\n");
        sb.append("        } else if (f > 0) {\n");
        sb.append("            unsigned int n = 111860U / f;\n");
        sb.append("            SMS_writeToPSG((unsigned char)(0x80 | (c << 5) | (n & 0x0F)));\n");
        sb.append("            SMS_writeToPSG((unsigned char)((n >> 4) & 0x3F));\n");
        sb.append("            SMS_writeToPSG((unsigned char)(0x90 | (c << 5) | (15 - (")
          .append(safeName).append("_melody_vols[i] & 0x0F))));\n");
        sb.append("        } else {\n");
        sb.append("            SMS_writeToPSG((unsigned char)(0x90 | (c << 5) | 0x0F));   // silencio\n");
        sb.append("        }\n");
        sb.append("        wait_ms(").append(safeName).append("_melody_durs[i]);\n");
        sb.append("        current_time += ").append(safeName).append("_melody_durs[i];\n");
        sb.append("    }\n");
        sb.append("    unsigned char m;\n");
        sb.append("    for (m = 0; m < 4; m++) {\n");
        sb.append("        SMS_writeToPSG((unsigned char)(0x90 | (m << 5) | 0x0F));      // silencio final\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static String ayChipName(Platform platform) {
        return platform == Platform.MSX ? "AY-3-8910" : "AY-3-8912";
    }

    private static String platformName(Platform platform) {
        switch (platform) {
            case ZX_SPECTRUM: return "ZX Spectrum 128";
            case MSX: return "MSX";
            default: return "Amstrad CPC";
        }
    }

    private static int ayClockDiv(Platform platform) {
        switch (platform) {
            case ZX_SPECTRUM: return ZX_AY_CLOCK_DIV_16;
            // El MSX usa el mismo reloj de 1,7734 MHz que el ZX (mitad de la CPU),
            // no el de 1 MHz del CPC: con 62500 los tonos saliran un 43 % graves.
            case MSX: return ZX_AY_CLOCK_DIV_16;
            default: return CPC_AY_CLOCK_DIV_16;
        }
    }

    private static int psgChannelIndex(String channel) {
        if (channel != null && channel.contains("Ruido")) {
            return 3;
        }
        if (channel != null) {
            for (int i = 0; i < channel.length(); i++) {
                char c = channel.charAt(i);
                if (c >= '1' && c <= '3') {
                    return c - '1';
                }
            }
        }
        return 0;
    }

    // ======================================================================
    // AY-3-8912 (Amstrad CPC y ZX Spectrum 128 / z88dk)
    // ======================================================================

    private static final int ZX_AY_CLOCK_DIV_16 = 110838;

    private String generateAySfx(Platform platform, String channel, int frequency, int volume, int durationMs) {
        StringBuilder sb = new StringBuilder();
        String platformName = platformName(platform);
        int clockDiv = ayClockDiv(platform);
        int ch = psgChannelIndex(channel);
        boolean noise = ch == 3;
        int regCh = noise ? 0 : ch;
        int amReg = 8 + regCh;
        String symbol = sanitizeSymbol(channel);

        sb.append("// Generated by Samaruc - ").append(platformName).append(" SFX (")
          .append(ayChipName(platform)).append(" / z88dk)\n");
        sb.append("#include <stdlib.h>\n\n");
        sb.append("// ").append(channel).append(noise ? "" : " a " + frequency + " Hz")
          .append(", volumen ").append(volume).append(", duración ").append(durationMs).append(" ms.\n");
        appendAyDelayFunction(sb, platform);
        appendAyWriteFunction(sb, platform, symbol);

        if (noise) {
            sb.append("// Ruido: canal A con el mezclador en modo ruido (bit 3 = 0), periodo de ruido R6 = 8.\n");
            sb.append("void play_").append(symbol).append("_effect(void) {\n");
            sb.append("    ay_write(6, 8);               // periodo de ruido (0-31)\n");
            sb.append("    ay_write(7, 0x37);            // mezclador: ruido en canal A, resto del ruido y tonos off\n");
            sb.append(String.format("    ay_write(%d, %d);            // volumen canal A = %d (0-15)\n", amReg, volume & 0x0F, volume & 0x0F));
            sb.append("    delay(").append(durationMs).append(");\n");
            sb.append(String.format("    ay_write(%d, 0);            // silencio\n", amReg));
            sb.append("}\n");
            return sb.toString();
        }

        int tp = frequency > 0 ? Math.max(1, Math.min(4095, (clockDiv + frequency / 2) / frequency)) : 1;
        int freqRegLo = ch * 2;
        int mixer = 0x3F & ~(1 << ch);
        int approxHz = clockDiv / tp;

        sb.append("// Tono ").append(ch + 1).append(" (canal ").append((char) ('A' + ch)).append(") a ")
          .append(frequency).append(" Hz: TP = ").append(tp).append(" (aprox. ").append(approxHz).append(" Hz).\n");
        sb.append("void play_").append(symbol).append("_effect(void) {\n");
        sb.append(String.format("    ay_write(%d, 0x%02X);        // tono %s, byte bajo\n",
                freqRegLo, tp & 0xFF, (char) ('A' + ch)));
        sb.append(String.format("    ay_write(%d, 0x%02X);        // tono %s, byte alto\n",
                freqRegLo + 1, (tp >> 8) & 0x0F, (char) ('A' + ch)));
        sb.append(String.format("    ay_write(7, 0x%02X);         // mezclador: tono canal %s, resto off\n", mixer, (char) ('A' + ch)));
        sb.append(String.format("    ay_write(%d, %d);            // volumen canal %s = %d (0-15)\n",
                amReg, volume & 0x0F, (char) ('A' + ch), volume & 0x0F));
        sb.append("    delay(").append(durationMs).append(");\n");
        sb.append(String.format("    ay_write(%d, 0);            // silencio\n", amReg));
        sb.append("}\n");

        return sb.toString();
    }

    private String generateAyMelody(Platform platform, String melodyName, List<GameBoyAudioPlayer.NoteStep> steps,
                                    boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        String platformName = platformName(platform);
        int clockDiv = ayClockDiv(platform);
        String safeName = sanitizeSymbol(melodyName);

        sb.append("// Generated by Samaruc - ").append(platformName)
          .append(" Timeline Melody Composer (").append(ayChipName(platform)).append(" / z88dk)\n");
        sb.append("#include <stdlib.h>\n\n");
        appendAyDelayFunction(sb, platform);
        appendAyWriteFunction(sb, platform, safeName);

        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_ch", "canal AY 0-2, 3 = ruido",
                steps, step -> String.valueOf(psgChannelIndex(step.getChannel())));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_freqs", "frecuencia en Hz",
                steps, step -> String.valueOf(step.getFrequency()));
        appendArray(sb, includeConst, "unsigned char", safeName, "_melody_vols", "volumen 0-15",
                steps, step -> String.valueOf(step.getVolume()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_durs", "duración en ms",
                steps, step -> String.valueOf(step.getDurationMs()));
        appendArray(sb, includeConst, "unsigned int", safeName, "_melody_starts", "inicio en ms",
                steps, step -> String.valueOf(step.getStartTimeMs()));
        sb.append("\n");

        sb.append("void play_").append(safeName).append("_melody(void) {\n");
        sb.append("    unsigned int current_time = 0;\n");
        sb.append("    unsigned char i;\n");
        sb.append("    for (i = 0; i < ").append(steps.size()).append("; i++) {\n");
        sb.append("        unsigned int start = ").append(safeName).append("_melody_starts[i];\n");
        sb.append("        if (start > current_time) {\n");
        sb.append("            delay(start - current_time);\n");
        sb.append("            current_time = start;\n");
        sb.append("        }\n");
        sb.append("        unsigned int f = ").append(safeName).append("_melody_freqs[i];\n");
        sb.append("        unsigned char c = ").append(safeName).append("_melody_ch[i];\n");
        sb.append("        unsigned char vol = ").append(safeName).append("_melody_vols[i] & 0x0F;\n");
        sb.append("        if (c == 3) {\n");
        sb.append("            ay_write(6, 8);                    // periodo de ruido\n");
        sb.append("            ay_write(7, 0x37);                 // mezclador: ruido en canal A\n");
        sb.append("            ay_write(8, vol);                 // volumen canal A\n");
        sb.append("        } else if (f > 0) {\n");
        sb.append("            unsigned int tp = ").append(clockDiv).append("U / f;      // periodo de tono\n");
        sb.append("            ay_write((unsigned char)(c * 2), (unsigned char)(tp & 0xFF));\n");
        sb.append("            ay_write((unsigned char)(c * 2 + 1), (unsigned char)((tp >> 8) & 0x0F));\n");
        sb.append("            ay_write(7, (unsigned char)(0x3F & ~(1U << c)));\n");
        sb.append("            ay_write((unsigned char)(8 + c), vol);\n");
        sb.append("        } else {\n");
        sb.append("            ay_write((unsigned char)(8 + c), 0);       // silencio\n");
        sb.append("        }\n");
        sb.append("        delay(").append(safeName).append("_melody_durs[i]);\n");
        sb.append("        current_time += ").append(safeName).append("_melody_durs[i];\n");
        sb.append("    }\n");
        sb.append("    unsigned char m;\n");
        sb.append("    for (m = 0; m < 3; m++) {\n");
        sb.append("        ay_write((unsigned char)(8 + m), 0);          // silencio final\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private void appendAyDelayFunction(StringBuilder sb, Platform platform) {
        int tstatesPerMs;
        String clock;
        switch (platform) {
            case CPC:
                tstatesPerMs = 4000; // Z80 a 4 MHz
                clock = "4 MHz";
                break;
            case MSX:
                tstatesPerMs = 3580; // Z80 a 3,579545 MHz
                clock = "3,579545 MHz";
                break;
            default:
                tstatesPerMs = 3500; // Z80 a 3,5 MHz
                clock = "3,5 MHz";
                break;
        }
        sb.append("// delay(ms): z88dk no trae delay() para estos targets, solo t_delay() en tstates.\n");
        sb.append("// La ").append(clock).append(" da ").append(tstatesPerMs).append(" tstates por ms; el argumento de\n");
        sb.append("// t_delay es un unsigned int de 16 bits, asi que se trocea en bloques de 60000.\n");
        sb.append("static void delay(unsigned int ms) {\n");
        sb.append("    unsigned int pending = ms;\n");
        sb.append("    while (pending > 15) {\n");
        sb.append("        t_delay(").append(tstatesPerMs * 15).append("U);\n");
        sb.append("        pending -= 15;\n");
        sb.append("    }\n");
        sb.append("    t_delay((unsigned int)(pending * ").append(tstatesPerMs).append("U));\n");
        sb.append("}\n\n");
    }

    private void appendAyWriteFunction(StringBuilder sb, Platform platform, String suffix) {
        if (platform == Platform.ZX_SPECTRUM) {
            sb.append("// AY-3-8912 del ZX 128: reloj 1,7734 MHz (mitad de la CPU); tono = 110838 / TP.\n\n");
            sb.append("// ay_write: escribe un registro del AY-3-8912 (puertos $FFFD = registro, $BFFD = dato).\n");
        } else if (platform == Platform.MSX) {
            sb.append("// AY-3-8910 del MSX: reloj 1,7734 MHz (3,579545 MHz / 2, como el ZX 128);\n");
            sb.append("// tono = 110838 / TP. Los dos puertos son $98 = registro y $99 = dato.\n\n");
            sb.append("// El registro 7 necesita dos ajustes en el MSX: los bits 6 y 7 no son parte\n");
            sb.append("// del mezclador sino del control del bus del PSG, asi que hay que enmascarar\n");
            sb.append("// el valor con %00111111 y dejar el bit 7 a uno para conservar el bus.\n");
            sb.append("// Conviene escribirlo con las interrupciones desactivadas, porque durante el\n");
            sb.append("// bus conversacional el PSG comparte las lineas con el VDP.\n");
            sb.append("// ay_write: escribe un registro del AY-3-8910 del MSX.\n");
        } else {
            sb.append("// AY-3-8912 del CPC: reloj 1 MHz; tono = 62500 / TP. Middle C = TP 478.\n\n");
            sb.append("// ay_write: escribe un registro del AY-3-8912 (secuencia PPI 8255 del CPC).\n");
        }
        sb.append("// Convención z88dk: reg en L, val en H.\n");
        sb.append("static void ay_write(unsigned char reg, unsigned char val) {\n");
        if (platform == Platform.MSX) {
            sb.append("#asm\n");
        sb.append("    ld a, l\n");
        sb.append("    cp 7\n");
        sb.append("    jr nz, msx_write_data_").append(suffix).append('\n');
        sb.append("    ld a, h\n");
        sb.append("    and %00111111\n");
        sb.append("    or  %10000000\n");
        sb.append("    ld h, a\n");
        sb.append("    ld a, l\n");
        sb.append("msx_write_data_").append(suffix).append(":\n");
        sb.append("    di\n");
        sb.append("    out (0x98), a\n");
        sb.append("    ld a, h\n");
        sb.append("    out (0x99), a\n");
        sb.append("    ei\n");
        sb.append("#endasm\n");
        sb.append("}\n\n");
        return;
        }
        sb.append("#asm\n");
        if (platform == Platform.ZX_SPECTRUM) {
            sb.append("    ld a, l                  ; REGISTER SELECT\n");
            sb.append("    ld bc, 0xFFFD\n");
            sb.append("    out (c), a\n");
            sb.append("    ld a, h                  ; WRITE VALUE\n");
            sb.append("    ld bc, 0xBFFD\n");
            sb.append("    out (c), a\n");
        } else {
            sb.append("    ld a, l                  ; REGISTER SPECIFY\n");
            sb.append("    ld bc, 0xF400\n");
            sb.append("    out (c), a\n");
            sb.append("    ld bc, 0xF600\n");
            sb.append("    ld a, %11000000\n");
            sb.append("    out (c), a\n");
            sb.append("    ld a, %00000000          ; PSG inactive\n");
            sb.append("    out (c), a\n");
            sb.append("    ld a, h                  ; WRITE DATA\n");
            sb.append("    ld bc, 0xF400\n");
            sb.append("    out (c), a\n");
            sb.append("    ld bc, 0xF600\n");
            sb.append("    ld a, %10000000\n");
            sb.append("    out (c), a\n");
            sb.append("    ld a, %00000000          ; PSG inactive\n");
            sb.append("    out (c), a\n");
        }
        sb.append("#endasm\n");
        sb.append("}\n\n");
    }

    @FunctionalInterface
    private interface StepValue {
        String of(GameBoyAudioPlayer.NoteStep step);
    }

    private void appendArray(StringBuilder sb, boolean includeConst, String type, String symbol, String suffix,
                             String comment, List<GameBoyAudioPlayer.NoteStep> steps, StepValue value) {
        if (includeConst) {
            sb.append("const ");
        }
        sb.append(type).append(" ").append(symbol).append(suffix).append("[] = {");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(value.of(steps.get(i)));
            if (i < steps.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("};  // ").append(comment).append("\n");
    }

    private static int atariChannelNumber(String channel) {
        if (channel != null) {
            for (int i = 0; i < channel.length(); i++) {
                char c = channel.charAt(i);
                if (c >= '1' && c <= '4') {
                    return c - '0';
                }
            }
        }
        return 1;
    }

    // ======================================================================
    // Game Boy (APU / GBDK) - comportamiento original, sin cambios
    // ======================================================================

    private String generateGbSoundCode(String channel, int frequency, int volume, String dutyCycle, int durationMs,
                                       boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated by Samaruc - Game Boy Audio Editor\n");
        if (includeConst) {
            sb.append("const ");
        }
        String symbol = channel.toLowerCase().replace(' ', '_').replace('1', '1').replace('2', '2');
        sb.append("unsigned char ").append(symbol).append("_sound_data[] = {\n");
        sb.append("    // Channel: ").append(channel).append("\n");
        sb.append("    // Freq: ").append(frequency).append("Hz, Vol: ").append(volume)
          .append(", Duty: ").append(dutyCycle).append(", Duration: ").append(durationMs).append("ms\n");

        int dutyBits = 0x80; // 50%
        if ("12.5%".equals(dutyCycle)) dutyBits = 0x00;
        else if ("25%".equals(dutyCycle)) dutyBits = 0x40;
        else if ("75%".equals(dutyCycle)) dutyBits = 0xC0;

        int volBits = (volume & 0x0F) << 4;

        sb.append(String.format("    0x%02X, 0x%02X, 0x%02X, 0x%02X\n", dutyBits, volBits, 0, 0));
        sb.append("};\n\n");

        sb.append("void play_").append(symbol).append("_effect(void) {\n");
        sb.append("    NR52_REG = 0x80; // Turn sound on\n");
        sb.append("    NR50_REG = 0x77; // Master volume\n");
        sb.append("    NR51_REG = 0xFF; // Enable all channels\n");
        if (!channel.contains("Noise")) {
            sb.append(String.format("    NR11_REG = 0x%02X;\n", dutyBits));
            sb.append(String.format("    NR12_REG = 0x%02X;\n", volBits | 0x07));
            sb.append("    NR13_REG = 0x83;\n");
            sb.append("    NR14_REG = 0x86;\n");
        } else {
            sb.append("    NR43_REG = 0x11;\n");
            sb.append(String.format("    NR42_REG = 0x%02X;\n", volBits));
            sb.append("    NR44_REG = 0x80;\n");
        }
        sb.append("}\n");

        return sb.toString();
    }

    private String generateGbMelodyCode(String melodyName, List<GameBoyAudioPlayer.NoteStep> steps,
                                        boolean includeConst) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated by Samaruc - Game Boy Timeline Melody Composer\n");
        String safeName = sanitizeSymbol(melodyName);

        if (includeConst) {
            sb.append("const ");
        }
        sb.append("unsigned int ").append(safeName).append("_melody_freqs[] = {");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(steps.get(i).getFrequency()).append(i < steps.size() - 1 ? ", " : "");
        }
        sb.append("};\n");

        if (includeConst) {
            sb.append("const ");
        }
        sb.append("unsigned int ").append(safeName).append("_melody_durs[] = {");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(steps.get(i).getDurationMs()).append(i < steps.size() - 1 ? ", " : "");
        }
        sb.append("};\n");

        if (includeConst) {
            sb.append("const ");
        }
        sb.append("unsigned int ").append(safeName).append("_melody_starts[] = {");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(steps.get(i).getStartTimeMs()).append(i < steps.size() - 1 ? ", " : "");
        }
        sb.append("};\n\n");

        sb.append("void play_").append(safeName).append("_melody(void) {\n");
        sb.append("    NR52_REG = 0x80; // Turn sound on\n");
        sb.append("    NR50_REG = 0x77; // Master volume\n");
        sb.append("    NR51_REG = 0xFF; // Enable all channels\n");
        sb.append("    unsigned int current_time = 0;\n");
        sb.append("    unsigned char i;\n");
        sb.append("    for (i = 0; i < ").append(steps.size()).append("; i++) {\n");
        sb.append("        unsigned int start = ").append(safeName).append("_melody_starts[i];\n");
        sb.append("        if (start > current_time) {\n");
        sb.append("            delay(start - current_time);\n");
        sb.append("            current_time = start;\n");
        sb.append("        }\n");
        sb.append("        unsigned int f = ").append(safeName).append("_melody_freqs[i];\n");
        sb.append("        unsigned int gb_freq = (f > 0) ? (2048 - (131072 / f)) : 0;\n");
        sb.append("        NR13_REG = (unsigned char)(gb_freq & 0xFF);\n");
        sb.append("        NR14_REG = (unsigned char)(0x80 | ((gb_freq >> 8) & 0x07));\n");
        sb.append("        NR11_REG = 0x80;\n");
        sb.append("        NR12_REG = 0xF0;\n");
        sb.append("        unsigned int dur = ").append(safeName).append("_melody_durs[i];\n");
        sb.append("        delay(dur);\n");
        sb.append("        current_time += dur;\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String sanitizeSymbol(String name) {
        String raw = (name != null && !name.isBlank()) ? name.trim() : "melody";
        String symbol = raw.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (symbol.isEmpty() || Character.isDigit(symbol.charAt(0))) {
            symbol = "_" + symbol;
        }
        return symbol;
    }
}
