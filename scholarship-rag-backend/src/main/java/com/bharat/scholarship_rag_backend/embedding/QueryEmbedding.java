package com.bharat.scholarship_rag_backend.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class QueryEmbedding {

    private final EmbeddingModel embeddingModel;

    public QueryEmbedding(EmbeddingModel embeddingModel){
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String query) {
        return embeddingModel.embed(query).content().vector();
    }

}
