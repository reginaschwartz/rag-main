package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        String dataPath,
        String globPattern,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer defaultK,
        Double defaultMinRelevance,
        Boolean initSchemaOnStartup) {

    public RagProperties {
        dataPath = hasText(dataPath) ? dataPath : "data/books";
        globPattern = hasText(globPattern) ? globPattern : "*.md";
        chunkSize = chunkSize != null ? chunkSize : 300;
        chunkOverlap = chunkOverlap != null ? chunkOverlap : 100;
        defaultK = defaultK != null ? defaultK : 3;
        defaultMinRelevance = defaultMinRelevance != null ? defaultMinRelevance : 0.7;
        initSchemaOnStartup = initSchemaOnStartup == null || initSchemaOnStartup;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
