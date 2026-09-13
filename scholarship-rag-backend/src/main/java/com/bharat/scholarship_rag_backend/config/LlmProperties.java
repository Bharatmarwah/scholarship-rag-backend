package com.bharat.scholarship_rag_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        Provider groq,
        Provider openRouter,
        Provider gemini,
        Provider cerebras
) {

    public record Provider(
            String apiKey,
            String modelName,
            String baseUrl
    ) {
    }
}