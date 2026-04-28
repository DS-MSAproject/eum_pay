package com.eum.rag.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MarkdownStructuringService {

    private static final Pattern ORDERED_TITLE = Pattern.compile("^(?:\\d+[.)]\\s+.+|\\d+\\.\\d+(?:\\.\\d+){0,2}\\s+.+)$");
    private static final Pattern KOREAN_ARTICLE_TITLE = Pattern.compile("^제\\d+조\\s*\\(.+\\)$");
    private static final Pattern SECTION_MARKER = Pattern.compile("^={3,}\\s*.+\\s*={3,}$");

    public String toMarkdown(String cleanedText) {
        if (cleanedText == null || cleanedText.isBlank()) {
            return "";
        }

        String[] lines = cleanedText.split("\\R");
        StringBuilder markdown = new StringBuilder();
        List<String> bufferedParagraph = new ArrayList<>();

        for (String line : lines) {
            String normalized = line.trim();
            if (normalized.isEmpty()) {
                flushParagraph(markdown, bufferedParagraph);
                continue;
            }

            if (isHeader(normalized)) {
                flushParagraph(markdown, bufferedParagraph);
                markdown.append("## ").append(normalized).append("\n\n");
                continue;
            }

            bufferedParagraph.add(normalized);
        }

        flushParagraph(markdown, bufferedParagraph);
        return markdown.toString().trim();
    }

    private boolean isHeader(String line) {
        if (line.length() > 80) {
            return false;
        }

        if (SECTION_MARKER.matcher(line).matches()) {
            return true;
        }

        if (ORDERED_TITLE.matcher(line).matches()) {
            return true;
        }

        if (KOREAN_ARTICLE_TITLE.matcher(line).matches()) {
            return true;
        }

        String lowered = line.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("q.") || lowered.startsWith("안내 ")) {
            return true;
        }

        if (line.endsWith(":") && isSafeColonHeader(line, lowered)) {
            return true;
        }

        return false;
    }

    private boolean isSafeColonHeader(String line, String lowered) {
        if (line.length() > 40) {
            return false;
        }
        if (line.contains("http://") || line.contains("https://")) {
            return false;
        }
        if (lowered.startsWith("summary:")
                || lowered.startsWith("source:")
                || lowered.startsWith("price:")
                || lowered.startsWith("rating:")
                || lowered.startsWith("description:")
                || lowered.startsWith("generatedat:")
                || lowered.startsWith("policynote:")
                || lowered.startsWith("noticetitle:")) {
            return false;
        }
        return line.split("\\s+").length <= 6;
    }

    private void flushParagraph(StringBuilder markdown, List<String> bufferedParagraph) {
        if (bufferedParagraph.isEmpty()) {
            return;
        }
        markdown.append(String.join(" ", bufferedParagraph)).append("\n\n");
        bufferedParagraph.clear();
    }
}
