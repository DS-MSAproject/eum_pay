package com.eum.rag.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DocumentCleaningService {

    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern PAGE_NUMBER = Pattern.compile("^(?:-\\s*)?(?:page|p)\\s*\\d+(?:\\s*/\\s*\\d+)?$", Pattern.CASE_INSENSITIVE);

    public String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String normalized = rawText
                .replace("\u00A0", " ")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String[] lines = normalized.split("\\n");
        List<String> cleanedLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = MULTI_SPACE.matcher(line).replaceAll(" ").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (PAGE_NUMBER.matcher(trimmed).matches()) {
                continue;
            }
            cleanedLines.add(trimmed);
        }

        return String.join("\n", cleanedLines);
    }
}
