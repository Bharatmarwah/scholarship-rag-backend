package com.bharat.scholarship_rag_backend.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Embeds text for retrieval.
 *
 * <p>Uses the single {@code embeddingModel} bean from {@code LlmConfig}, which
 * is also what ingestion will use, so query vectors and stored document vectors
 * stay comparable.
 */
@Component
public class QueryEmbedding {

    private final EmbeddingModel embeddingModel;

    public QueryEmbedding(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String query) {
        return embeddingModel.embed(query).content().vector();
    }

}