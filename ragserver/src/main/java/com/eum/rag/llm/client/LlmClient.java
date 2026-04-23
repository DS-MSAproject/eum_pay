package com.eum.rag.llm.client;

public interface LlmClient {

    String complete(String systemPrompt, String userPrompt);
}
