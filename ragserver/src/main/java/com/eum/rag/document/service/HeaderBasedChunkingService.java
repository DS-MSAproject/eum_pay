package com.eum.rag.document.service;

import com.eum.rag.common.config.properties.RagDocumentProperties;
import com.eum.rag.common.util.IdGenerator;
import com.eum.rag.common.util.TokenEstimator;
import com.eum.rag.document.domain.DocumentChunk;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class HeaderBasedChunkingService {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+.+");
    private static final int DEFAULT_TARGET_TOKENS = 320;
    private static final int DEFAULT_MIN_TOKENS = 180;
    private static final int DEFAULT_MAX_TOKENS = 480;
    private static final int ABSOLUTE_MAX_TOKENS = 500;
    private static final int ULTRA_TINY_TOKENS = 20;

    private final RagDocumentProperties properties;

    public HeaderBasedChunkingService(RagDocumentProperties properties) {
        this.properties = properties;
    }

    public List<DocumentChunk> chunk(String markdown, String documentId) {
        List<Section> sections = splitByHeader(markdown);
        List<DocumentChunk> chunks = new ArrayList<>();
        int targetTokens = sanitizeTargetTokens(properties.chunkSize());
        int maxTokens = sanitizeMaxTokens(properties.chunkMaxSize());
        if (targetTokens > maxTokens) {
            targetTokens = maxTokens;
        }
        int minTokens = Math.min(sanitizeMinTokens(properties.chunkMinSize()), targetTokens);

        int chunkIndex = 0;
        for (Section section : sections) {
            List<String> sentences = splitToSentences(section.body());
            StringBuilder current = new StringBuilder();

            for (String sentence : sentences) {
                if (TokenEstimator.estimate(sentence) > maxTokens) {
                    if (current.length() > 0) {
                        chunks.add(buildChunk(documentId, chunkIndex++, section.header(), current.toString()));
                        current = new StringBuilder();
                    }
                    List<String> parts = splitByTokenLimit(sentence, maxTokens, overlapSize(maxTokens));
                    for (String part : parts) {
                        chunks.add(buildChunk(documentId, chunkIndex++, section.header(), part));
                    }
                    continue;
                }

                String candidate = current.length() == 0 ? sentence : current + " " + sentence;
                int tokens = TokenEstimator.estimate(candidate);

                if (tokens > targetTokens && current.length() > 0) {
                    chunks.add(buildChunk(documentId, chunkIndex++, section.header(), current.toString()));
                    current = new StringBuilder(buildOverlapTail(current.toString()));
                    if (current.length() > 0) {
                        current.append(' ');
                    }
                    current.append(sentence);
                    if (TokenEstimator.estimate(current.toString()) > maxTokens) {
                        List<String> parts = splitByTokenLimit(current.toString(), maxTokens, overlapSize(maxTokens));
                        for (String part : parts) {
                            chunks.add(buildChunk(documentId, chunkIndex++, section.header(), part));
                        }
                        current = new StringBuilder();
                    }
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

        List<DocumentChunk> merged = mergeSmallChunks(documentId, chunks, minTokens, maxTokens);
        return absorbUltraTinyChunks(documentId, merged, maxTokens);
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
        int overlap = Math.min(overlapSize(tokens.length), tokens.length);
        if (overlap <= 0) {
            return "";
        }
        int start = tokens.length - overlap;
        return String.join(" ", Arrays.copyOfRange(tokens, start, tokens.length));
    }

    private int overlapSize(int tokenSize) {
        double ratio = Math.max(0.0d, Math.min(0.5d, properties.chunkOverlapRatio()));
        int overlap = (int) Math.round(tokenSize * ratio);
        return overlap <= 0 ? 0 : overlap;
    }

    private List<String> splitByTokenLimit(String text, int maxTokens, int overlapTokens) {
        String[] tokens = text.split("\\s+");
        List<String> parts = new ArrayList<>();
        if (tokens.length == 0) {
            return parts;
        }

        int start = 0;
        while (start < tokens.length) {
            int end = Math.min(start + maxTokens, tokens.length);
            String slice = String.join(" ", Arrays.copyOfRange(tokens, start, end)).trim();
            if (!slice.isEmpty()) {
                parts.add(slice);
            }
            if (end == tokens.length) {
                break;
            }

            int step = Math.max(1, maxTokens - Math.max(0, overlapTokens));
            start += step;
        }

        return parts;
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

    private List<DocumentChunk> mergeSmallChunks(String documentId, List<DocumentChunk> chunks, int minTokens, int maxTokens) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        int mergeThreshold = Math.max(24, Math.min(120, Math.max(1, minTokens / 2)));

        List<DocumentChunk> merged = new ArrayList<>();
        merged.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            DocumentChunk current = chunks.get(i);
            DocumentChunk last = merged.get(merged.size() - 1);

            boolean sameHeader = normalizeHeader(last.header()).equals(normalizeHeader(current.header()));
            boolean sameMajorSection = sameMajorSection(last.header(), current.header());
            int mergedTokenCount = TokenEstimator.estimate(last.content() + " " + current.content());
            boolean shouldMerge = (sameHeader || sameMajorSection)
                    && (current.tokenCount() < mergeThreshold || last.tokenCount() < mergeThreshold)
                    && mergedTokenCount <= maxTokens;

            if (!shouldMerge) {
                merged.add(current);
                continue;
            }

            String joined = (last.content() + " " + current.content()).trim();
            DocumentChunk mergedChunk = new DocumentChunk(
                    IdGenerator.generateId(),
                    documentId,
                    last.chunkIndex(),
                    last.header(),
                    joined,
                    mergedTokenCount,
                    OffsetDateTime.now()
            );
            merged.set(merged.size() - 1, mergedChunk);
        }

        List<DocumentChunk> reindexed = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            DocumentChunk chunk = merged.get(i);
            reindexed.add(new DocumentChunk(
                    chunk.chunkId(),
                    chunk.documentId(),
                    i,
                    chunk.header(),
                    chunk.content(),
                    chunk.tokenCount(),
                    chunk.createdAt()
            ));
        }
        return reindexed;
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase(Locale.ROOT);
    }

    private List<DocumentChunk> absorbUltraTinyChunks(String documentId, List<DocumentChunk> chunks, int maxTokens) {
        if (chunks.size() <= 1) {
            return chunks;
        }

        List<DocumentChunk> working = new ArrayList<>(chunks);
        int i = 0;
        while (i < working.size()) {
            DocumentChunk chunk = working.get(i);
            if (chunk.tokenCount() >= ULTRA_TINY_TOKENS) {
                i++;
                continue;
            }

            Integer prevIndex = i > 0 ? i - 1 : null;
            Integer nextIndex = i < working.size() - 1 ? i + 1 : null;
            Integer targetIndex = pickMergeTarget(working, chunk, prevIndex, nextIndex, maxTokens);

            if (targetIndex == null) {
                i++;
                continue;
            }

            DocumentChunk target = working.get(targetIndex);
            String tinyText = chunk.header() + " " + chunk.content();
            String mergedText;
            if (targetIndex < i) {
                mergedText = (target.content() + " " + tinyText).trim();
            } else {
                mergedText = (tinyText + " " + target.content()).trim();
            }

            DocumentChunk mergedChunk = new DocumentChunk(
                    IdGenerator.generateId(),
                    documentId,
                    target.chunkIndex(),
                    target.header(),
                    mergedText,
                    TokenEstimator.estimate(mergedText),
                    OffsetDateTime.now()
            );

            working.set(targetIndex, mergedChunk);
            working.remove(i);
        }

        List<DocumentChunk> reindexed = new ArrayList<>();
        for (int idx = 0; idx < working.size(); idx++) {
            DocumentChunk c = working.get(idx);
            reindexed.add(new DocumentChunk(
                    c.chunkId(),
                    c.documentId(),
                    idx,
                    c.header(),
                    c.content(),
                    c.tokenCount(),
                    c.createdAt()
            ));
        }
        return reindexed;
    }

    private Integer pickMergeTarget(List<DocumentChunk> chunks, DocumentChunk tiny, Integer prevIndex, Integer nextIndex, int maxTokens) {
        Integer bestIndex = null;
        int bestScore = Integer.MAX_VALUE;

        if (prevIndex != null) {
            int score = mergeScore(chunks.get(prevIndex), tiny, maxTokens);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                bestIndex = prevIndex;
            }
        }

        if (nextIndex != null) {
            int score = mergeScore(chunks.get(nextIndex), tiny, maxTokens);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                bestIndex = nextIndex;
            }
        }
        return bestIndex;
    }

    private int mergeScore(DocumentChunk target, DocumentChunk tiny, int maxTokens) {
        String merged = target.content() + " " + tiny.header() + " " + tiny.content();
        int tokens = TokenEstimator.estimate(merged);
        if (tokens > maxTokens) {
            return -1;
        }
        boolean sameHeader = normalizeHeader(target.header()).equals(normalizeHeader(tiny.header()));
        boolean sameMajorSection = sameMajorSection(target.header(), tiny.header());
        if (sameHeader) {
            return tokens;
        }
        if (sameMajorSection) {
            return tokens + 20;
        }
        return tokens + 50;
    }

    private boolean sameMajorSection(String headerA, String headerB) {
        Optional<String> keyA = extractMajorKey(headerA);
        Optional<String> keyB = extractMajorKey(headerB);
        return keyA.isPresent() && keyA.equals(keyB);
    }

    private Optional<String> extractMajorKey(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String normalized = header.replaceFirst("^#+\\s*", "").trim().toLowerCase(Locale.ROOT);

        java.util.regex.Matcher chapterMatcher = java.util.regex.Pattern.compile("^(\\d+)\\.").matcher(normalized);
        if (chapterMatcher.find()) {
            return Optional.of("chapter:" + chapterMatcher.group(1));
        }

        java.util.regex.Matcher articleMatcher = java.util.regex.Pattern.compile("^제(\\d+)조").matcher(normalized);
        if (articleMatcher.find()) {
            return Optional.of("article:" + articleMatcher.group(1));
        }
        return Optional.empty();
    }

    private int sanitizeTargetTokens(int configured) {
        int value = configured > 0 ? configured : DEFAULT_TARGET_TOKENS;
        return Math.max(200, Math.min(ABSOLUTE_MAX_TOKENS, value));
    }

    private int sanitizeMinTokens(int configured) {
        int value = configured > 0 ? configured : DEFAULT_MIN_TOKENS;
        return Math.max(80, Math.min(400, value));
    }

    private int sanitizeMaxTokens(int configured) {
        int value = configured > 0 ? configured : DEFAULT_MAX_TOKENS;
        return Math.max(220, Math.min(ABSOLUTE_MAX_TOKENS, value));
    }

    private record Section(String header, String body) {
    }
}
