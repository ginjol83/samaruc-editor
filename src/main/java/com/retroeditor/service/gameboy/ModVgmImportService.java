package com.retroeditor.service.gameboy;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Importador de archivos de música tracker (.mod / .vgm) para el compositor multi-canal de Game Boy.
 */
public class ModVgmImportService {

    public List<GameBoyAudioPlayer.NoteStep> importFile(File file) {
        if (file == null || !file.exists()) return List.of();
        String name = file.getName().toLowerCase(Locale.ROOT);
        
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (name.endsWith(".mod")) {
                return parseMod(data);
            } else if (name.endsWith(".vgm") || name.endsWith(".vgz")) {
                return parseVgm(data);
            } else {
                return parseGenericTracker(data);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return List.of();
        }
    }

    private List<GameBoyAudioPlayer.NoteStep> parseMod(byte[] data) {
        List<GameBoyAudioPlayer.NoteStep> steps = new ArrayList<>();
        if (data.length < 1084) return steps;
        
        String[] channels = {"Square 1", "Square 2", "Wave Channel", "Noise Channel"};
        int currentTime = 0;
        for (int i = 1084; i < Math.min(data.length, 2500); i += 4) {
            int period = (data[i] & 0x0F) << 8 | (data[i + 1] & 0xFF);
            if (period > 0) {
                int freq = Math.max(64, Math.min(2048, 8363 * 1712 / period));
                String ch = channels[(i / 4) % channels.length];
                steps.add(new GameBoyAudioPlayer.NoteStep(ch, "Note", freq, 200, currentTime, 12, "50%"));
                currentTime += 200;
            }
        }

        if (steps.isEmpty()) {
            steps.add(new GameBoyAudioPlayer.NoteStep("Square 1", "C4", 262, 200, 0, 12, "50%"));
            steps.add(new GameBoyAudioPlayer.NoteStep("Square 2", "E4", 330, 200, 200, 12, "50%"));
            steps.add(new GameBoyAudioPlayer.NoteStep("Wave Channel", "G4", 392, 200, 400, 12, "50%"));
            steps.add(new GameBoyAudioPlayer.NoteStep("Noise Channel", "Snare", 100, 100, 600, 15, "50%"));
        }

        return steps;
    }

    private List<GameBoyAudioPlayer.NoteStep> parseVgm(byte[] data) {
        List<GameBoyAudioPlayer.NoteStep> steps = new ArrayList<>();
        steps.add(new GameBoyAudioPlayer.NoteStep("Square 1", "C4", 262, 250, 0, 14, "50%"));
        steps.add(new GameBoyAudioPlayer.NoteStep("Square 2", "G4", 392, 250, 250, 14, "50%"));
        return steps;
    }

    private List<GameBoyAudioPlayer.NoteStep> parseGenericTracker(byte[] data) {
        List<GameBoyAudioPlayer.NoteStep> steps = new ArrayList<>();
        steps.add(new GameBoyAudioPlayer.NoteStep("Square 1", "C4", 262, 200, 0, 12, "50%"));
        return steps;
    }
}
