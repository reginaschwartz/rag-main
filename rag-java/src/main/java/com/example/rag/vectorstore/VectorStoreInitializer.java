package com.example.rag.vectorstore;

import com.example.rag.config.RagProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates the pgvector extension, tables, indexes and collection during startup, before the HTTP
 * server accepts traffic. The Python service did the same from its container entrypoint.
 */
@Component
public class VectorStoreInitializer {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreInitializer.class);

    private final PgVectorStore vectorStore;
    private final RagProperties properties;

    public VectorStoreInitializer(PgVectorStore vectorStore, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        if (!properties.initSchemaOnStartup()) {
            return;
        }
        vectorStore.initialize();
        log.info("pgvector collection {} is ready.", vectorStore.collectionName());
    }
}
