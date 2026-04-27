package com.eum.rag.document.service;

import com.eum.rag.common.config.properties.RagDocumentProperties;
import com.eum.rag.common.util.IdGenerator;
import com.eum.rag.common.util.TokenEstimator;
import com.eum.rag.document.domain.DocumentChunk;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class HeaderBasedChunkingService {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+.+");

    private final RagDocumentProperties properties;

    public HeaderBasedChunkingService(RagDocumentProperties properties) {
        this.properties = properties;
    }

    public List<DocumentChunk> chunk(String markdown, String documentId) {
        List<Section> sections = splitByHeader(markdown);
        List<DocumentChunk> chunks = new ArrayList<>();

        int chunkIndex = 0;
        for (Section section : sections) {
            List<String> sentences = splitToSentences(section.body());
            StringBuilder current = new StringBuilder();

            for (String sentence : sentences) {
                String candidate = current.length() == 0 ? sentence : current + " " + sentence;
                int tokens = TokenEstimator.estimate(candidate);

                if (tokens > properties.chunkSize() && current.length() > 0) {
                    chunks.add(buildChunk(documentId, chunkIndex++, section.header(), current.toString()));
                    current = new StringBuilder(buildOverlapTail(current.toString()));
                    if (current.length() > 0) {
                        current.append(' ');
                    }
                    current.append(sentence);
                } else {
                    if (current.length() > 0) {
                        current.append(' ');
                    }
                    current.append(sentence);
                }
            }

            if (current.length() > 0) {
                chunks.add(buildChunk(documentId, chunkIndex++, section.header(), current.toString()));
            }
        }

        return chunks;
    }

    private List<Section> splitByHeader(String markdown) {
        List<Section> sections = new ArrayList<>();
        String[] lines = markdown.split("\\R");

        String currentHeader = "## General";
        StringBuilder body = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (HEADER_PATTERN.matcher(line).matches()) {
                if (body.length() > 0) {
                    sections.add(new Section(currentHeader, body.toString().trim()));
                    body = new StringBuilder();
                }
                currentHeader = line;
                continue;
            }

            if (body.length() > 0) {
                body.append(' ');
            }
            body.append(line);
        }

        if (body.length() > 0) {
            sections.add(new Section(currentHeader, body.toString().trim()));
        }

        return sections;
    }

    private List<String> splitToSentences(String text) {
        String[] parts = text.split("(?<=[.!?。])\\s+|\\n");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String buildOverlapTail(String text) {
        String[] tokens = text.split("\\s+");
        int overlap = Math.min(properties.chunkOverlap(), tokens.length);
        if (overlap <= 0) {
            return "";
        }
        int start = tokens.length - overlap;
        return String.join(" ", Arrays.copyOfRange(tokens, start, tokens.length));
    }

    private DocumentChunk buildChunk(String documentId, int chunkIndex, String header, String content) {
        return new DocumentChunk(
                IdGenerator.generateId(),
                documentId,
                chunkIndex,
                header,
                content,
                TokenEstimator.estimate(content),
                OffsetDateTime.now()
        );
    }

    private record Section(String header, String body) {
    }
}
