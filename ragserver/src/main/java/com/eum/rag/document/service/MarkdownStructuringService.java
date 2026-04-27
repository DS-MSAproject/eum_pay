package com.eum.rag.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MarkdownStructuringService {

    private static final Pattern ORDERED_TITLE = Pattern.compile("^(\\d+)([.)])\\s+.+");

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

        if (ORDERED_TITLE.matcher(line).matches()) {
            return true;
        }

        if (line.endsWith(":")) {
            return true;
        }

        String lowered = line.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("faq") || lowered.startsWith("q.") || lowered.startsWith("안내") || lowered.startsWith("공지")) {
            return true;
        }

        return !line.contains(".") && !line.contains("?") && line.split("\\s+").length <= 10;
    }

    private void flushParagraph(StringBuilder markdown, List<String> bufferedParagraph) {
        if (bufferedParagraph.isEmpty()) {
            return;
        }
        markdown.append(String.join(" ", bufferedParagraph)).append("\n\n");
        bufferedParagraph.clear();
    }
}
