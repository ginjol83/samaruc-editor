package com.retroeditor.service.gameboy;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.List;
import java.util.Random;

/**
 * Reproductor de audio sintetizado para Game Boy (preview polifónico multi-canal basado en timeline).
 */
public class GameBoyAudioPlayer {
    private static final float SAMPLE_RATE = 44100f;

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
        new Thread(() -> playToneBlocking(frequency, durationMs, volume, dutyCycle), "gb-audio-preview").start();
    }

    public void playMelody(List<NoteStep> steps) {
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

                    double duty = 0.5;
                    String dutyCycle = step.getDutyCycle();
                    if ("12.5%".equals(dutyCycle)) duty = 0.125;
                    else if ("25%".equals(dutyCycle)) duty = 0.25;
                    else if ("75%".equals(dutyCycle)) duty = 0.75;

                    double vol = (step.getVolume() / 15.0) * 16.0;

                    for (int i = 0; i < noteSamples && (startSample + i) < totalSamples; i++) {
                        int idx = startSample + i;
                        double time = i / SAMPLE_RATE;
                        double val = 0;
                        if (ch.contains("Noise")) {
                            val = (Math.random() * 2.0 - 1.0) * vol;
                        } else {
                            int freq = step.getFrequency();
                            if (freq > 0) {
                                double period = 1.0 / freq;
                                double cycleTime = time % period;
                                val = (cycleTime < period * duty) ? vol : -vol;
                            }
                        }
                        masterBuffer[idx] += val;
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
        }, "gb-multichannel-preview").start();
    }

    private void playToneBlocking(int frequency, int durationMs, int volume, String dutyCycle) {
        try {
            AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
                line.open(af);
                line.start();

                int numSamples = (int) (SAMPLE_RATE * durationMs / 1000.0);
                byte[] buffer = new byte[numSamples];
                double duty = 0.5;
                if ("12.5%".equals(dutyCycle)) duty = 0.125;
                else if ("25%".equals(dutyCycle)) duty = 0.25;
                else if ("75%".equals(dutyCycle)) duty = 0.75;

                double vol = (volume / 15.0) * 64.0;

                for (int i = 0; i < numSamples; i++) {
                    double time = i / SAMPLE_RATE;
                    double val = 0;
                    if (frequency > 0) {
                        double period = 1.0 / frequency;
                        double cycleTime = time % period;
                        val = (cycleTime < period * duty) ? vol : -vol;
                    }
                    buffer[i] = (byte) val;
                }

                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {}
    }

    public void playNoise(int durationMs, int volume) {
        new Thread(() -> playNoiseBlocking(durationMs, volume), "gb-noise-preview").start();
    }

    private void playNoiseBlocking(int durationMs, int volume) {
        try {
            AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
                line.open(af);
                line.start();

                int numSamples = (int) (SAMPLE_RATE * durationMs / 1000.0);
                byte[] buffer = new byte[numSamples];
                double vol = (volume / 15.0) * 64.0;
                Random rnd = new Random();

                for (int i = 0; i < numSamples; i++) {
                    double val = (rnd.nextDouble() * 2.0 - 1.0) * vol;
                    buffer[i] = (byte) val;
                }

                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {}
    }
}
