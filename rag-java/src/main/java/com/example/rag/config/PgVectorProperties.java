package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pgvector")
public record PgVectorProperties(String connection, String collection) {

    public PgVectorProperties {
        collection = collection != null && !collection.isBlank() ? collection : "default";
    }
}
