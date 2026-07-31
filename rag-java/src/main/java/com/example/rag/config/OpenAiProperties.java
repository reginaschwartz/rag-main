package com.example.rag.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String embeddingModel,
        String chatModel,
        Double temperature,
        Duration timeout,
        Integer embeddingBatchSize) {

    public OpenAiProperties {
        baseUrl = hasText(baseUrl) ? stripTrailingSlash(baseUrl) : "https://api.openai.com/v1";
        embeddingModel = hasText(embeddingModel) ? embeddingModel : "text-embedding-ada-002";
        chatModel = hasText(chatModel) ? chatModel : "gpt-4o-mini";
        temperature = temperature != null ? temperature : 0.7;
        timeout = timeout != null ? timeout : Duration.ofSeconds(60);
        embeddingBatchSize = embeddingBatchSize != null && embeddingBatchSize > 0 ? embeddingBatchSize : 200;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
