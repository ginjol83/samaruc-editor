package com.retroeditor.service.gameboy;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.List;

/**
 * Reproductor de audio sintetizado (preview polifónico multi-canal basado en timeline).
 * Sintetiza el carácter de cada chip: la APU de Game Boy (onda cuadrada con duty ajustable, DAC
 * no lineal y ruido LFSR de 15 bits), el AY-3-8912 de CPC/ZX Spectrum (cuadrada fija al 50 %,
 * DAC por corriente casi lineal y ruido LFSR de 17 bits), el POKEY de Atari (tono puro $A0), el
 * SN76489 de SMS/GG (cuadrada al 50 %, atenuación logarítmica y ruido LFSR de 16 bits) y el
 * SID 6581 de C64 (pulso con ancho ajustable y ruido LFSR de 23 bits).
 */
public class GameBoyAudioPlayer {
    private static final float SAMPLE_RATE = 44100f;

    /**
     * Motor de sonido usado en la síntesis del preview.
     * <p>La APU del NES (RP2A03) comparte con la de Game Boy el LFSR de ruido de 15 bits y el
     * DAC no lineal de 4 bits, pero no el barrido del canal 1 ni el control de volumen del
     * canal triangular, así que se distingue solo por su nombre.
     */
    public enum SoundEngine { GAMEBOY, APU_RP2A03, AY_3_8912, POKEY, SN76489, SID6581 }

    /**
     * Ruido del AY/YM: LFSR de 17 bits con retroalimentación XOR de las posiciones 16 y 13;
     * periodo 2^17-1. Se avanza una posición por pulso de ruido.
     */
    private static final double AY_NOISE_HZ = 1773400.0 / 16.0 / 9.0; // R6=8, PAL /16 ≈ 12,3 kHz

    public static final class NoteStep {
        private final String channel;
        private final String noteName;
        private final int frequency;
        private final int durationMs;
        private final int startTimeMs;
        private final int volume;
        private final String dutyCycle;

        public NoteStep(String channel, String noteName, int frequency, int durationMs, int startTimeMs, int volume, String dutyCycle) {
            this.channel = channel;
            this.noteName = noteName;
            this.frequency = frequency;
            this.durationMs = durationMs;
            this.startTimeMs = startTimeMs;
            this.volume = volume;
            this.dutyCycle = dutyCycle;
        }

        public String getChannel() { return channel; }
        public String getNoteName() { return noteName; }
        public int getFrequency() { return frequency; }
        public int getDurationMs() { return durationMs; }
        public int getStartTimeMs() { return startTimeMs; }
        public int getVolume() { return volume; }
        public String getDutyCycle() { return dutyCycle; }

        @Override
        public String toString() {
            return String.format("[%s] @%dms: %s (%d Hz) - %dms - Vol:%d", channel, startTimeMs, noteName, frequency, durationMs, volume);
        }
    }

    public void playTone(int frequency, int durationMs, int volume, String dutyCycle) {
        playTone(frequency, durationMs, volume, dutyCycle, SoundEngine.GAMEBOY);
    }

    public void playTone(int frequency, int durationMs, int volume, String dutyCycle, SoundEngine engine) {
        new Thread(() -> playToneBlocking(frequency, durationMs, volume, dutyCycle, engine), "tone-preview").start();
    }

    public void playMelody(List<NoteStep> steps) {
        playMelody(steps, SoundEngine.GAMEBOY);
    }

    public void playMelody(List<NoteStep> steps, SoundEngine engine) {
        if (steps == null || steps.isEmpty()) return;
        new Thread(() -> {
            try {
                int maxEndTimeMs = 1000;
                for (NoteStep step : steps) {
                    int end = step.getStartTimeMs() + step.getDurationMs();
                    if (end > maxEndTimeMs) maxEndTimeMs = end;
                }

                int totalSamples = (int) (SAMPLE_RATE * (maxEndTimeMs + 200) / 1000.0);
                double[] masterBuffer = new double[totalSamples];

                for (NoteStep step : steps) {
                    String ch = step.getChannel() != null ? step.getChannel() : "Square 1";
                    int startSample = (int) (SAMPLE_RATE * step.getStartTimeMs() / 1000.0);
                    int noteSamples = (int) (SAMPLE_RATE * step.getDurationMs() / 1000.0);
                    int freq = step.getFrequency();

                    if (ch.contains("Noise") || ch.contains("Ruido")) {
                        double[] noise = noiseSamples(noteSamples, engine, amplitude(engine, step.getVolume(), 16.0));
                        for (int i = 0; i < noteSamples && startSample + i < totalSamples; i++) {
                            masterBuffer[startSample + i] += noise[i];
                        }
                        continue;
                    }

                    if (freq <= 0) continue; // silencio (rest)

                    double duty = toneDuty(engine, step.getDutyCycle());
                    double amp = amplitude(engine, step.getVolume(), 16.0);
                    double period = 1.0 / freq;
                    for (int i = 0; i < noteSamples && startSample + i < totalSamples; i++) {
                        double time = i / SAMPLE_RATE;
                        double cycleTime = time % period;
                        masterBuffer[startSample + i] += (cycleTime < period * duty) ? amp : -amp;
                    }
                }

                byte[] byteBuffer = new byte[totalSamples];
                for (int i = 0; i < totalSamples; i++) {
                    double sample = masterBuffer[i];
                    if (sample > 127) sample = 127;
                    if (sample < -128) sample = -128;
                    byteBuffer[i] = (byte) sample;
                }

                AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
                    line.open(af);
                    line.start();
                    line.write(byteBuffer, 0, byteBuffer.length);
                    line.drain();
                }
            } catch (Exception ignored) {}
        }, "multichannel-preview").start();
    }

    private void playToneBlocking(int frequency, int durationMs, int volume, String dutyCycle, SoundEngine engine) {
        try {
            AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
                line.open(af);
                line.start();

                int numSamples = (int) (SAMPLE_RATE * durationMs / 1000.0);
                byte[] buffer = new byte[numSamples];
                double duty = toneDuty(engine, dutyCycle);
                double amp = amplitude(engine, volume, 64.0);

                for (int i = 0; i < numSamples; i++) {
                    double time = i / SAMPLE_RATE;
                    double val = 0;
                    if (frequency > 0) {
                        double period = 1.0 / frequency;
                        double cycleTime = time % period;
                        val = (cycleTime < period * duty) ? amp : -amp;
                    }
                    buffer[i] = (byte) val;
                }

                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {}
    }

    public void playNoise(int durationMs, int volume) {
        playNoise(durationMs, volume, SoundEngine.GAMEBOY);
    }

    public void playNoise(int durationMs, int volume, SoundEngine engine) {
        new Thread(() -> playNoiseBlocking(durationMs, volume, engine), "noise-preview").start();
    }

    private void playNoiseBlocking(int durationMs, int volume, SoundEngine engine) {
        try {
            AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
                line.open(af);
                line.start();

                int numSamples = (int) (SAMPLE_RATE * durationMs / 1000.0);
                double[] noise = noiseSamples(numSamples, engine, amplitude(engine, volume, 64.0));
                byte[] buffer = new byte[numSamples];
                for (int i = 0; i < numSamples; i++) {
                    buffer[i] = (byte) noise[i];
                }

                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Onda cuadrada: el AY, el SN76489 y el POKEY (tono puro $A0) fijan el duty al 50 %; la APU
     * de GB, la del NES (RP2A03) y el pulso del SID (ancho de pulso) lo ajustan al valor del
     * tracker. El canal triangular del NES no es una cuadrada, el generador lo trata aparte.
     */
    private static double toneDuty(SoundEngine engine, String dutyCycle) {
        if (engine == SoundEngine.AY_3_8912 || engine == SoundEngine.SN76489 || engine == SoundEngine.POKEY) {
            return 0.5;
        }
        double duty = 0.5;
        if ("12.5%".equals(dutyCycle)) duty = 0.125;
        else if ("25%".equals(dutyCycle)) duty = 0.25;
        else if ("75%".equals(dutyCycle)) duty = 0.75;
        return duty;
    }

    /**
     * Amplitud por volumen: DAC lineales del AY, POKEY y SID (v/15); DAC no lineal de las APU
     * de GB y NES (raíz, para que las notas bajas no decaigan tanto) y atenuación logarítmica
     * del SN76489 (~2 dB por paso, 30 dB entre volumen 15 y 0).
     */
    private static double amplitude(SoundEngine engine, int volume, double max) {
        double v = (volume & 0x0F) / 15.0;
        double rel;
        switch (engine) {
            case GAMEBOY:
            case APU_RP2A03:
                rel = Math.sqrt(v);
                break;
            case SN76489: rel = Math.pow(10.0, -(15.0 - v * 15.0) / 10.0); break;
            default: rel = v;
        }
        return max * rel;
    }

    /**
     * Sintetiza la textura de ruido con el LFSR propio de cada chip, todos de distinto tamaño y
     * con distinta textura "metálica": GB 15 bits, SN76489 16 bits, AY/POKEY 17 bits y SID 23 bits.
     * <ul>
     *   <li>GB: LFSR de 15 bits (XOR de bits 0 y 1, realimentado al bit 14) de la APU.</li>
     *   <li>NES (RP2A03): el mismo LFSR de 15 bits que la APU de GB; el bit 5 de $400C elige
     *       entre el modo corto (93 pasos) y el largo (32767 pasos).</li>
     *   <li>SN76489: LFSR de 16 bits en modo "ruido blanco" (XOR de bits 0 y 1, realimentado al bit 15).</li>
     *   <li>AY-3-8912: LFSR de 17 bits (XOR de bits 0 y 3, realimentado al bit 16), a ~12,3 kHz con R6=8.</li>
     *   <li>POKEY: aproximación al poly counter de 17 bits (LFSR maximal); el tracker Atari solo usa tono puro.</li>
     *   <li>SID 6581: LFSR de 23 bits (XOR de bits 0 y 1, realimentado al bit 22), el generador aleatorio del SID.</li>
     * </ul>
     */
    private static double[] noiseSamples(int count, SoundEngine engine, double amp) {
        double[] out = new double[count];
        switch (engine) {
            case APU_RP2A03: {
                // La APU del NES reutiliza el LFSR de 15 bits de la de Game Boy
                // (XOR de bits 0 y 1 realimentado al bit 14). El tracker solo
                // expone el modo largo, así que el bit 5 de $400C se deja a 0.
                int lfsr = 0x7FFF;
                for (int i = 0; i < count; i++) {
                    int bit = lfsr & 1;
                    lfsr >>= 1;
                    if ((bit ^ (lfsr & 1)) != 0) {
                        lfsr |= 0x4000;
                    }
                    out[i] = (lfsr & 1) == 0 ? amp : -amp;
                }
                break;
            }
            case SN76489: {
                int lfsr = 0x8000; // 16 bits
                for (int i = 0; i < count; i++) {
                    int feedback = (lfsr & 1) ^ ((lfsr >> 1) & 1);
                    lfsr = (lfsr >> 1) | (feedback << 15);
                    out[i] = (lfsr & 1) == 0 ? amp : -amp;
                }
                break;
            }
            case AY_3_8912: {
                int lfsr = 0x17DE3; // semilla no nula, 17 bits
                double phase = 0.0;
                for (int i = 0; i < count; i++) {
                    phase += AY_NOISE_HZ / SAMPLE_RATE;
                    while (phase >= 1.0) {
                        phase -= 1.0;
                        int feedback = ((lfsr & 1) ^ ((lfsr >> 3) & 1)) & 1;
                        lfsr = (lfsr >> 1) | (feedback << 16);
                    }
                    out[i] = (lfsr & 1) == 0 ? amp : -amp;
                }
                break;
            }
            case POKEY: {
                int lfsr = 0x1FFFF; // 17 bits
                for (int i = 0; i < count; i++) {
                    int feedback = ((lfsr & 1) ^ ((lfsr >> 3) & 1)) & 1;
                    lfsr = (lfsr >> 1) | (feedback << 16);
                    out[i] = (lfsr & 1) == 0 ? amp : -amp;
                }
                break;
            }
            case SID6581: {
                int lfsr = 0x7FFFFF; // 23 bits, semilla no nula
                for (int i = 0; i < count; i++) {
                    int feedback = (lfsr & 1) ^ ((lfsr >> 1) & 1);
                    lfsr = (lfsr >> 1) | (feedback << 22);
                    out[i] = (lfsr & 1) == 0 ? amp : -amp;
                }
                break;
            }
            default: {
                int lfsr = 0x7FFF; // 15 bits (Game Boy)
                for (int i = 0; i < count; i++) {
                    int bit = lfsr & 1;
                    lfsr >>= 1;
                    if ((bit ^ (lfsr & 1)) != 0) {
                        lfsr |= 0x4000;
                    }
                    out[i] = (lfsr & 1) == 0 ? amp : -amp;
                }
            }
        }
        return out;
    }
}