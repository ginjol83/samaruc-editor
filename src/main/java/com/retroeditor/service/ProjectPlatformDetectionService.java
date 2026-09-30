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
        CPC,
        MSX,
        SMS,
        GAME_GEAR,
        ATARI,
        C64,
        NES,
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
        int cpcScore = 0;
        int msxScore = 0;
        int smsScore = 0;
        int gameGearScore = 0;
        int atariScore = 0;
        int c64Score = 0;
        int nesScore = 0;
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

                if (content.contains("\"samaruc.target\":\"gameboy\"") || content.contains("\"samaruc.target\": \"gameboy\"")) {
                    gameBoyScore += 15;
                    evidence.add("samaruc.target=gameboy");
                }
                if (content.contains("\"samaruc.target\":\"spectrum\"") || content.contains("\"samaruc.target\": \"spectrum\"")) {
                    spectrumScore += 15;
                    evidence.add("samaruc.target=spectrum");
                }
                if (content.contains("\"samaruc.target\":\"cpc\"") || content.contains("\"samaruc.target\": \"cpc\"")) {
                    cpcScore += 15;
                    evidence.add("samaruc.target=cpc");
                }
                if (content.contains("\"samaruc.target\":\"msx\"") || content.contains("\"samaruc.target\": \"msx\"")) {
                    msxScore += 15;
                    evidence.add("samaruc.target=msx");
                }
                if (content.contains("\"samaruc.target\":\"sms\"") || content.contains("\"samaruc.target\": \"sms\"")) {
                    smsScore += 15;
                    evidence.add("samaruc.target=sms");
                }
                if (content.contains("\"samaruc.target\":\"gamegear\"") || content.contains("\"samaruc.target\": \"gamegear\"")) {
                    gameGearScore += 15;
                    evidence.add("samaruc.target=gamegear");
                }
                if (content.contains("\"samaruc.target\":\"cc65\"") || content.contains("\"samaruc.target\": \"cc65\"")) {
                    atariScore += 15;
                    evidence.add("samaruc.target=cc65");
                }
                if (content.contains("\"samaruc.target\":\"c64\"") || content.contains("\"samaruc.target\": \"c64\"")) {
                    c64Score += 15;
                    evidence.add("samaruc.target=c64");
                }
                if (content.contains("\"samaruc.target\":\"nes\"") || content.contains("\"samaruc.target\": \"nes\"")) {
                    nesScore += 15;
                    evidence.add("samaruc.target=nes");
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

                boolean cpcSpecific = content.contains("<cpc.h>")
                    || content.contains("+cpc")
                    || content.contains("cpc_");
                if (content.contains("#include <cpc.h>")) {
                    cpcScore += 6;
                    evidence.add("cpc.h");
                }
                if (content.contains("+cpc")) {
                    cpcScore += 5;
                    evidence.add("+cpc");
                }
                if (content.contains("cpc_")) {
                    cpcScore += 3;
                    evidence.add("cpc_*");
                }
                if (content.contains("#include <conio.h>") && !content.contains("dos.h")) {
                    if (content.contains("c64.h") || content.contains("vic.h") || content.contains("sid.h")
                            || content.contains("cia.h") || content.contains("cbm_kernal.h")
                            || content.contains("-t c64") || content.contains("c64.cfg")) {
                        c64Score += 6;
                        evidence.add("conio.h (cc65 C64)");
                    } else if (cpcSpecific) {
                        cpcScore += 4;
                        evidence.add("conio.h (CPC)");
                    } else if (content.contains("atari.h")
                        || content.contains("atarixl")
                        || content.contains("cl65")
                        || content.contains("-t atari")
                        || content.contains("cgetc(")) {
                        atariScore += 6;
                        evidence.add("conio.h (cc65)");
                    } else {
                        spectrumScore += 6;
                        evidence.add("conio.h");
                    }
                }
                if (!content.contains("atari.h") && !content.contains("atarixl")
                        && !content.contains("c64.h") && !content.contains("vic.h") && !content.contains("sid.h")) {
                    if (content.contains("bordercolor(")) {
                        spectrumScore += 3;
                        evidence.add("bordercolor()");
                    }
                    if (content.contains("cputs(") && !content.contains("cl65")) {
                        spectrumScore += 2;
                        evidence.add("cputs()");
                    }
                    if (content.contains("kbhit(") && !content.contains("cl65")) {
                        spectrumScore += 2;
                        evidence.add("kbhit()");
                    }
                }
                if (content.contains("+zx")) {
                    spectrumScore += 3;
                    evidence.add("+zx");
                }
                if (content.contains("z88dk") && !cpcSpecific) {
                    spectrumScore += 2;
                    evidence.add("z88dk");
                }

                if (content.contains("#include <atari.h>")) {
                    atariScore += 6;
                    evidence.add("atari.h");
                }
                if (content.contains("atarixl") || content.contains("atarixl.h") || content.contains("-t atarixl") || content.contains("atarixl-xex")) {
                    atariScore += 5;
                    evidence.add("atarixl");
                }
                if (content.contains("cl65") && !content.contains("-t c64") && !content.contains("-t nes")
                        || content.contains("-t atari") || content.contains("atari-xex") || content.contains("atari.cfg")) {
                    atariScore += 4;
                    evidence.add("cl65/-t atari");
                }
                if (content.contains("cgetc(") || content.contains("#include <atari.h>") || content.contains("#include <atarixl.h>")) {
                    atariScore += 2;
                    evidence.add("cgetc()/atari");
                }

                if (content.contains("#include <c64.h>")) {
                    c64Score += 6;
                    evidence.add("c64.h");
                }
                if (content.contains("#include <sid.h>") || content.contains("#include <vic.h>")
                        || content.contains("#include <cia.h>") || content.contains("#include <cbm_kernal.h>")) {
                    c64Score += 5;
                    evidence.add("c64 SID/VIC/CIA");
                }
                if (content.contains("-t c64") || content.contains("c64.cfg") || content.contains("c64-x") || content.contains("c64.h")) {
                    c64Score += 4;
                    evidence.add("-t c64");
                }
                if (content.contains("vicii") || content.contains("0xd021") || content.contains("0xd015")
                        || content.contains("#define vic")) {
                    c64Score += 2;
                    evidence.add("VIC-II");
                }

                boolean ggTarget = content.contains("+gg") || content.contains("-t gg")
                        || content.contains("-subtype=gamegear");
                boolean smsTarget = !ggTarget && (content.contains("+sms") || content.contains("-subtype=sms"));
                if (content.contains("#include <sms/sms.h>") || content.contains("#include <sms.h>")) {
                    if (ggTarget) {
                        gameGearScore += 6;
                        evidence.add("sms.h (+gg)");
                    } else {
                        smsScore += 6;
                        evidence.add("sms/sms.h");
                    }
                }
                if (content.contains("#include <gg/gg.h>")) {
                    gameGearScore += 6;
                    evidence.add("gg/gg.h");
                }
                if (content.contains("sms_displayon") || content.contains("sms_waitforvblank")) {
                    if (ggTarget) {
                        gameGearScore += 4;
                        evidence.add("sms_vdp (+gg)");
                    } else {
                        smsScore += 4;
                        evidence.add("sms_vdp");
                    }
                }
                if (smsTarget) {
                    smsScore += 5;
                    evidence.add("+sms");
                }
                if (ggTarget) {
                    gameGearScore += content.contains("-subtype=gamegear") ? 8 : 5;
                    evidence.add(content.contains("-subtype=gamegear") ? "-subtype=gamegear" : "+gg");
                }
                if (content.contains("rgb15(")) {
                    if (ggTarget) {
                        gameGearScore += 2;
                        evidence.add("rgb15 (+gg)");
                    } else {
                        smsScore += 2;
                        evidence.add("rgb15");
                    }
                }

                if (content.contains("#include <msx.h>") || content.contains("#include <msx/msx.h>")) {
                    msxScore += 6;
                    evidence.add("msx.h");
                }
                if (content.contains("+msx")) {
                    msxScore += 5;
                    evidence.add("+msx");
                }
                if (content.contains("msx_")) {
                    msxScore += 3;
                    evidence.add("msx_*");
                }
                if (content.contains("-subtype=rom") && !content.contains("+sms") && !content.contains("+gg")) {
                    msxScore += 2;
                    evidence.add("-subtype=rom");
                }

                boolean nesSpecific = content.contains("-t nes")
                    || content.contains("nes.cfg")
                    || content.contains("nes.h")
                    || content.contains("ppu_");
                if (content.contains("#include <nes.h>") || content.contains("#include <nes/nes.h>")) {
                    nesScore += 6;
                    evidence.add("nes.h");
                }
                if (content.contains("-t nes")) {
                    nesScore += 4;
                    evidence.add("-t nes");
                }
                if (content.contains("nes.cfg")) {
                    nesScore += 4;
                    evidence.add("nes.cfg");
                }
                if (content.contains("ppu_")) {
                    nesScore += 3;
                    evidence.add("ppu_*");
                }
                if (nesSpecific && !content.contains("atarixl") && !content.contains("-t atari")
                        && !content.contains("atari.h")) {
                    nesScore += 2;
                    evidence.add("nes");
                }
            }
        } catch (IOException ex) {
            return new DetectionResult(Platform.UNKNOWN, 0, "");
        }

        int total = gameBoyScore + spectrumScore + cpcScore + msxScore + smsScore + gameGearScore + atariScore + c64Score + nesScore;
        if (total == 0) {
            return new DetectionResult(Platform.UNKNOWN, 0, "");
        }

        int maxScore = Math.max(gameBoyScore, Math.max(spectrumScore,
                Math.max(cpcScore, Math.max(msxScore, Math.max(smsScore,
                        Math.max(gameGearScore, Math.max(atariScore, Math.max(c64Score, nesScore))))))));
        Platform platform = Platform.UNKNOWN;
        if (maxScore == gameBoyScore && gameBoyScore > spectrumScore && gameBoyScore > cpcScore
                && gameBoyScore > msxScore && gameBoyScore > smsScore && gameBoyScore > gameGearScore
                && gameBoyScore > atariScore && gameBoyScore > c64Score && gameBoyScore > nesScore) {
            platform = Platform.GAMEBOY;
        } else if (maxScore == spectrumScore && spectrumScore > gameBoyScore && spectrumScore > cpcScore
                && spectrumScore > msxScore && spectrumScore > smsScore && spectrumScore > gameGearScore
                && spectrumScore > atariScore && spectrumScore > c64Score && spectrumScore > nesScore) {
            platform = Platform.SPECTRUM;
        } else if (maxScore == cpcScore && cpcScore > gameBoyScore && cpcScore > spectrumScore
                && cpcScore > msxScore && cpcScore > smsScore && cpcScore > gameGearScore
                && cpcScore > atariScore && cpcScore > c64Score && cpcScore > nesScore) {
            platform = Platform.CPC;
        } else if (maxScore == msxScore && msxScore > gameBoyScore && msxScore > spectrumScore
                && msxScore > cpcScore && msxScore > smsScore && msxScore > gameGearScore
                && msxScore > atariScore && msxScore > c64Score && msxScore > nesScore) {
            platform = Platform.MSX;
        } else if (maxScore == smsScore && smsScore > gameBoyScore && smsScore > spectrumScore
                && smsScore > cpcScore && smsScore > msxScore && smsScore > gameGearScore
                && smsScore > atariScore && smsScore > c64Score && smsScore > nesScore) {
            platform = Platform.SMS;
        } else if (maxScore == gameGearScore && gameGearScore > gameBoyScore && gameGearScore > spectrumScore
                && gameGearScore > cpcScore && gameGearScore > msxScore && gameGearScore > smsScore
                && gameGearScore > atariScore && gameGearScore > c64Score && gameGearScore > nesScore) {
            platform = Platform.GAME_GEAR;
        } else if (maxScore == atariScore && atariScore > gameBoyScore && atariScore > spectrumScore
                && atariScore > cpcScore && atariScore > msxScore && atariScore > smsScore
                && atariScore > gameGearScore && atariScore > c64Score && atariScore > nesScore) {
            platform = Platform.ATARI;
        } else if (maxScore == c64Score && c64Score > gameBoyScore && c64Score > spectrumScore
                && c64Score > cpcScore && c64Score > msxScore && c64Score > smsScore
                && c64Score > gameGearScore && c64Score > atariScore && c64Score > nesScore) {
            platform = Platform.C64;
        } else if (maxScore == nesScore && nesScore > gameBoyScore && nesScore > spectrumScore
                && nesScore > cpcScore && nesScore > msxScore && nesScore > smsScore
                && nesScore > gameGearScore && nesScore > atariScore && nesScore > c64Score) {
            platform = Platform.NES;
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
            || name.equals("settings.json")
            || name.endsWith(".txt");
    }

    private String summarizeEvidence(List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) return "";

        List<String> unique = evidence.stream().distinct().limit(4).toList();
        return String.join(", ", unique);
    }
}

