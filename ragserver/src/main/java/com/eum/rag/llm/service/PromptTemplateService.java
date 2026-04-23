package com.eum.rag.llm.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Slf4j
@Getter
@Service
public class PromptTemplateService {

    private String answerSystemPrompt;
    private String queryRewritePrompt;

    @PostConstruct
    void init() {
        this.answerSystemPrompt = read("prompts/answer-system-prompt.txt");
        this.queryRewritePrompt = read("prompts/query-rewrite-prompt.txt");
        log.info("Prompt templates loaded: answer={}, rewrite={}",
                answerSystemPrompt.length(), queryRewritePrompt.length());
    }

    private String read(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read prompt file: " + path, exception);
        }
    }
}
