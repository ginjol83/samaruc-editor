package com.retroeditor.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de busqueda en texto plano, independiente de UI.
 */
public class TextSearchService {

    public static class MatchRange {
        private final int start;
        private final int end;

        public MatchRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
    }

    public static class SnippetMatch {
        private final int start;
        private final int length;
        private final String snippet;

        public SnippetMatch(int start, int length, String snippet) {
            this.start = start;
            this.length = length;
            this.snippet = snippet;
        }

        public int getStart() {
            return start;
        }

        public int getLength() {
            return length;
        }

        public String getSnippet() {
            return snippet;
        }
    }

    public MatchRange findNext(String text, String term, int fromIndex, boolean caseSensitive, boolean regex) {
        if (text == null || term == null || term.isEmpty()) return null;

        int idx = -1;
        int end = -1;

        try {
            if (regex) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile(term, caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(text);

                if (m.find(Math.max(0, fromIndex))) {
                    idx = m.start();
                    end = m.end();
                } else if (fromIndex > 0 && m.find(0)) {
                    idx = m.start();
                    end = m.end();
                }
            } else {
                String hay = caseSensitive ? text : text.toLowerCase();
                String needle = caseSensitive ? term : term.toLowerCase();
                idx = hay.indexOf(needle, Math.max(0, fromIndex));
                if (idx < 0 && fromIndex > 0) idx = hay.indexOf(needle, 0);
                if (idx >= 0) end = idx + needle.length();
            }
        } catch (Exception ignored) {
            return null;
        }

        return idx >= 0 ? new MatchRange(idx, end) : null;
    }

    public MatchRange findPrevious(String text, String term, int fromIndex, boolean caseSensitive, boolean regex) {
        if (text == null || term == null || term.isEmpty()) return null;

        int startPos = fromIndex;
        if (startPos < 0) startPos = text.length();

        int idx = -1;
        int end = -1;

        try {
            if (regex) {
                java.util.regex.Pattern p = java.util.regex.Pattern
                    .compile(term, caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(text);
                int last = -1;
                int lastEnd = -1;

                while (m.find() && m.start() < startPos) {
                    last = m.start();
                    lastEnd = m.end();
                }

                idx = last;
                end = lastEnd;
            } else {
                String hay = caseSensitive ? text : text.toLowerCase();
                String needle = caseSensitive ? term : term.toLowerCase();
                idx = hay.lastIndexOf(needle, startPos);
                if (idx >= 0) end = idx + needle.length();
            }
        } catch (Exception ignored) {
            return null;
        }

        return idx >= 0 ? new MatchRange(idx, end) : null;
    }

    public List<SnippetMatch> findAll(String text, String term, boolean caseSensitive, boolean regex) {
        List<SnippetMatch> list = new ArrayList<>();
        if (text == null || term == null || term.isEmpty()) return list;

        try {
            if (regex) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile(term, caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(text);
                while (m.find()) {
                    int len = m.end() - m.start();
                    list.add(new SnippetMatch(m.start(), len, buildSnippet(text, m.start(), len)));
                }
            } else {
                String hay = caseSensitive ? text : text.toLowerCase();
                String needle = caseSensitive ? term : term.toLowerCase();
                int idx = 0;
                while ((idx = hay.indexOf(needle, idx)) >= 0) {
                    int len = needle.length();
                    list.add(new SnippetMatch(idx, len, buildSnippet(text, idx, len)));
                    idx += Math.max(1, len);
                }
            }
        } catch (Exception ignored) {
        }

        return list;
    }

    private String buildSnippet(String content, int idx, int length) {
        int start = Math.max(0, idx - 30);
        int end = Math.min(content.length(), idx + length + 30);
        return content.substring(start, end).replaceAll("\\r?\\n", " ");
    }
}

