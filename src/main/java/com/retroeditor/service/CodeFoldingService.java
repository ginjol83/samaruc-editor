package com.retroeditor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para detectar bloques plegables ({ } y regiones ASM/#region) en código fuente.
 */
public class CodeFoldingService {

    public static final class FoldRange {
        private final int startLine; // 1-indexed
        private final int endLine;   // 1-indexed
        private final String title;

        public FoldRange(int startLine, int endLine, String title) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.title = title;
        }

        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public String getTitle() { return title; }
    }

    private static final Pattern REGION_START_PATTERN = Pattern.compile("(?i)(?:#\\s*region|region)\\s+(.+)");
    private static final Pattern REGION_END_PATTERN = Pattern.compile("(?i)(?:#\\s*endregion|endregion)");

    public List<FoldRange> detectFoldRanges(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<FoldRange> ranges = new ArrayList<>();
        String[] lines = text.split("\\r?\\n", -1);

        List<Integer> braceStack = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '{') {
                    braceStack.add(i + 1);
                } else if (ch == '}' && !braceStack.isEmpty()) {
                    int startLine = braceStack.remove(braceStack.size() - 1);
                    int endLine = i + 1;
                    if (endLine > startLine) {
                        ranges.add(new FoldRange(startLine, endLine, "{...}"));
                    }
                }
            }
        }

        List<RegionStart> regionStack = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            Matcher startMat = REGION_START_PATTERN.matcher(line);
            if (startMat.find()) {
                regionStack.add(new RegionStart(startMat.group(1).trim(), lineNum));
            } else if (REGION_END_PATTERN.matcher(line).find() && !regionStack.isEmpty()) {
                RegionStart start = regionStack.remove(regionStack.size() - 1);
                int endLine = lineNum;
                if (endLine > start.line) {
                    ranges.add(new FoldRange(start.line, endLine, "#region " + start.name));
                }
            }
        }

        ranges.sort((a, b) -> Integer.compare(a.startLine, b.startLine));
        return ranges;
    }

    private static class RegionStart {
        final String name;
        final int line;
        RegionStart(String name, int line) {
            this.name = name;
            this.line = line;
        }
    }
}
