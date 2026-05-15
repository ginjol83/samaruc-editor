package com.retroeditor.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Detecta la plataforma probable de un proyecto retro en base a su contenido fuente.
 */
public class ProjectPlatformDetectionService {

    public enum Platform {
        GAMEBOY,
        SPECTRUM,
        UNKNOWN
    }

    public static final class DetectionResult {
        private final Platform platform;
        private final int confidence;
        private final String evidence;

        public DetectionResult(Platform platform, int confidence, String evidence) {
            this.platform = platform != null ? platform : Platform.UNKNOWN;
            this.confidence = Math.max(0, Math.min(100, confidence));
            this.evidence = evidence != null ? evidence : "";
        }

        public Platform getPlatform() {
            return platform;
        }

        public int getConfidence() {
            return confidence;
        }

        public String getEvidence() {
            return evidence;
        }
    }

    public DetectionResult detectProjectPlatform(File projectDir) {
        if (projectDir == null || !projectDir.isDirectory()) {
            return new DetectionResult(Platform.UNKNOWN, 0, "");
        }

        int gameBoyScore = 0;
        int spectrumScore = 0;
        List<String> evidence = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectDir.toPath())) {
            List<Path> files = paths
                .filter(Files::isRegularFile)
                .filter(this::isCandidateTextFile)
                .limit(300)
                .toList();

            for (Path file : files) {
                String content;
                try {
                    content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                } catch (IOException ex) {
                    continue;
                }

                if (content.contains("#include <gb/gb.h>")) {
                    gameBoyScore += 6;
                    evidence.add("gb/gb.h");
                }
                if (content.contains("wait_vbl_done")) {
                    gameBoyScore += 3;
                    evidence.add("wait_vbl_done");
                }
                if (content.contains("set_sprite_")) {
                    gameBoyScore += 2;
                    evidence.add("set_sprite_*");
                }
                if (content.contains("display_on")) {
                    gameBoyScore += 1;
                    evidence.add("DISPLAY_ON");
                }

                if (content.contains("#include <conio.h>")) {
                    spectrumScore += 6;
                    evidence.add("conio.h");
                }
                if (content.contains("bordercolor(")) {
                    spectrumScore += 3;
                    evidence.add("bordercolor()");
                }
                if (content.contains("cputs(")) {
                    spectrumScore += 2;
                    evidence.add("cputs()");
                }
                if (content.contains("kbhit(")) {
                    spectrumScore += 2;
                    evidence.add("kbhit()");
                }
                if (content.contains("z88dk") || content.contains("+zx")) {
                    spectrumScore += 2;
                    evidence.add("z88dk/+zx");
                }
            }
        } catch (IOException ex) {
            return new DetectionResult(Platform.UNKNOWN, 0, "");
        }

        int total = gameBoyScore + spectrumScore;
        if (total == 0) {
            return new DetectionResult(Platform.UNKNOWN, 0, "");
        }

        int diff = Math.abs(gameBoyScore - spectrumScore);
        int confidence = (int) Math.round((diff * 100.0) / total);

        if (diff < 2) {
            return new DetectionResult(Platform.UNKNOWN, confidence, summarizeEvidence(evidence));
        }

        Platform platform = gameBoyScore > spectrumScore ? Platform.GAMEBOY : Platform.SPECTRUM;
        return new DetectionResult(platform, confidence, summarizeEvidence(evidence));
    }

    private boolean isCandidateTextFile(Path file) {
        String name = file.getFileName() != null ? file.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        return name.endsWith(".c")
            || name.endsWith(".h")
            || name.endsWith(".asm")
            || name.endsWith(".s")
            || name.equals("makefile")
            || name.endsWith(".mk")
            || name.endsWith(".txt");
    }

    private String summarizeEvidence(List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) return "";

        List<String> unique = evidence.stream().distinct().limit(4).toList();
        return String.join(", ", unique);
    }
}

