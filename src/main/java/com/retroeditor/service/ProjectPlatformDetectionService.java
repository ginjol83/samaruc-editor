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
        MSDOS,
        SMS,
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
        int msdosScore = 0;
        int smsScore = 0;
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

                if (content.contains("#include <conio.h>") && !content.contains("dos.h")) {
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

                if (content.contains("#include <dos.h>")) {
                    msdosScore += 6;
                    evidence.add("dos.h");
                }
                if (content.contains("int 21h") || content.contains("int21")) {
                    msdosScore += 4;
                    evidence.add("int 21h");
                }
                if (content.contains("org 100h")) {
                    msdosScore += 4;
                    evidence.add("org 100h");
                }
                if (content.contains("watcom") || content.contains("turboc")) {
                    msdosScore += 2;
                    evidence.add("watcom/turboc");
                }

                if (content.contains("#include <sms/sms.h>") || content.contains("#include <gg/gg.h>")) {
                    smsScore += 6;
                    evidence.add("sms/gg.h");
                }
                if (content.contains("sms_displayon") || content.contains("sms_waitforvblank")) {
                    smsScore += 4;
                    evidence.add("sms_vdp");
                }
                if (content.contains("rgb15(")) {
                    smsScore += 2;
                    evidence.add("rgb15");
                }
            }
        } catch (IOException ex) {
            return new DetectionResult(Platform.UNKNOWN, 0, "");
        }

        int total = gameBoyScore + spectrumScore + msdosScore + smsScore;
        if (total == 0) {
            return new DetectionResult(Platform.UNKNOWN, 0, "");
        }

        int maxScore = Math.max(gameBoyScore, Math.max(spectrumScore, Math.max(msdosScore, smsScore)));
        Platform platform = Platform.UNKNOWN;
        if (maxScore == gameBoyScore && gameBoyScore > spectrumScore && gameBoyScore > msdosScore && gameBoyScore > smsScore) {
            platform = Platform.GAMEBOY;
        } else if (maxScore == spectrumScore && spectrumScore > gameBoyScore && spectrumScore > msdosScore && spectrumScore > smsScore) {
            platform = Platform.SPECTRUM;
        } else if (maxScore == msdosScore && msdosScore > gameBoyScore && msdosScore > spectrumScore && msdosScore > smsScore) {
            platform = Platform.MSDOS;
        } else if (maxScore == smsScore && smsScore > gameBoyScore && smsScore > spectrumScore && smsScore > msdosScore) {
            platform = Platform.SMS;
        }

        int confidence = (int) Math.round((maxScore * 100.0) / total);
        if (platform == Platform.UNKNOWN || confidence < 40) {
            return new DetectionResult(Platform.UNKNOWN, confidence, summarizeEvidence(evidence));
        }

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

