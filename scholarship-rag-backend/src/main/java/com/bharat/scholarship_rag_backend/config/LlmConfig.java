package com.bharat.scholarship_rag_backend.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Value("${groq.model.name}")
    private String groqModelName;

    @Value("${groq.base.url}")
    private String groqBaseUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model.name}")
    private String geminiModelName;

    @Value("${embedding.dimension}")
    private Integer embeddingDimension;

    @Bean
    public OpenAiChatModel chatModel() {
        return OpenAiChatModel.builder()
                .modelName(groqModelName)
                .baseUrl(groqBaseUrl)
                .apiKey(groqApiKey)
                .maxTokens(1000)
                .temperature(0.0)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(geminiApiKey)
                .modelName(geminiModelName)
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
                .outputDimensionality(embeddingDimension)
                .build();
    }

    @Bean
    public OpenAiStreamingChatModel streamingChatModel(){
        return OpenAiStreamingChatModel
                .builder()
                .modelName(groqModelName)
                .baseUrl(groqBaseUrl)
                .apiKey(groqApiKey)
                .maxTokens(1000)
                .temperature(0.0)
                .build();
    }
}