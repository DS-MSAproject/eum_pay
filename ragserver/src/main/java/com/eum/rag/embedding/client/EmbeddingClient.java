package com.eum.rag.embedding.client;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);
}
