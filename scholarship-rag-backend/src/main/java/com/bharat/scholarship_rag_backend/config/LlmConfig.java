package com.bharat.scholarship_rag_backend.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    private final LlmProperties properties;

    public LlmConfig(LlmProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OpenAiChatModel chatModel() {
        LlmProperties.Provider groq = properties.groq();
        return OpenAiChatModel.builder()
                .modelName(groq.modelName())
                .baseUrl(groq.baseUrl())
                .apiKey(groq.apiKey())
                .maxTokens(1000)
                .temperature(0.8)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        LlmProperties.Provider gemini = properties.gemini();
        return GoogleAiEmbeddingModel.builder()
                .apiKey(gemini.apiKey())
                .modelName(gemini.modelName())
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
                .outputDimensionality(768)
                .build();
    }
}