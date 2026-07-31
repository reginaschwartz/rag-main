package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fallback connection details, used when {@code pgvector.connection} is not provided.
 */
@ConfigurationProperties(prefix = "postgres")
public record PostgresProperties(
        String host,
        String port,
        String db,
        String user,
        String password) {
}
