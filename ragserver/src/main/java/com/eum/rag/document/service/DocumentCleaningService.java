package com.eum.rag.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DocumentCleaningService {

    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern PAGE_NUMBER = Pattern.compile("^(?:-\\s*)?(?:page|p)\\s*\\d+(?:\\s*/\\s*\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFD]");
    private static final Pattern FILE_URI = Pattern.compile("(?i)^file:///.*");
    private static final Pattern DATE_FOOTER = Pattern.compile("^\\d{2}\\.\\s*\\d{1,2}\\.\\s*\\d{1,2}\\..*");
    private static final Pattern SOURCE_LINE = Pattern.compile("(?i)^[^\\p{L}\\p{N}]*source\\s*:\\s*https?://.*");

    public String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String normalized = rawText
                .replace("\u00A0", " ")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\uFFFD', ' ')
                .replace('\u0000', ' ');

        String[] lines = normalized.split("\\n");
        List<String> cleanedLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = CONTROL_CHARS.matcher(line).replaceAll(" ");
            trimmed = MULTI_SPACE.matcher(trimmed).replaceAll(" ").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (PAGE_NUMBER.matcher(trimmed).matches()) {
                continue;
            }
            if (FILE_URI.matcher(trimmed).matches()) {
                continue;
            }
            if (DATE_FOOTER.matcher(trimmed).matches()) {
                continue;
            }
            if (SOURCE_LINE.matcher(trimmed).matches()) {
                continue;
            }
            if (isCssNoise(trimmed)) {
                continue;
            }
            cleanedLines.add(trimmed);
        }

        return String.join("\n", cleanedLines);
    }

    private boolean isCssNoise(String line) {
        String lowered = line.toLowerCase();
        return lowered.contains("p { margin:")
                || lowered.contains("max-width:")
                || lowered.contains("height: auto")
                || lowered.contains("display: block; margin: 0 auto")
                || lowered.equals("isplay: block; margin: 0 auto !important; max-width: 100%; h")
                || lowered.equals("eight: auto; }");
    }
}
