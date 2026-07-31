package com.example.rag.vectorstore;

import com.example.rag.config.PgVectorProperties;
import com.example.rag.document.Document;
import com.example.rag.openai.OpenAiClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Vector store on PostgreSQL + pgvector.
 *
 * <p>It reads and writes the {@code langchain_pg_collection} / {@code langchain_pg_embedding} tables
 * used by the previous Python service, so collections indexed by either implementation stay usable.
 * Relevance is reported as {@code 1 - cosine_distance}, the same score LangChain returned from
 * {@code similarity_search_with_relevance_scores}.
 */
@Component
public class PgVectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);

    private static final String CREATE_EXTENSION = """
            SELECT pg_advisory_xact_lock(1573678846307946496);
            CREATE EXTENSION IF NOT EXISTS vector;
            """;

    private static final String CREATE_COLLECTION_TABLE = """
            CREATE TABLE IF NOT EXISTS langchain_pg_collection (
                uuid UUID NOT NULL PRIMARY KEY,
                name VARCHAR NOT NULL UNIQUE,
                cmetadata JSON
            )
            """;

    private static final String CREATE_EMBEDDING_TABLE = """
            CREATE TABLE IF NOT EXISTS langchain_pg_embedding (
                id VARCHAR NOT NULL PRIMARY KEY,
                collection_id UUID REFERENCES langchain_pg_collection(uuid) ON DELETE CASCADE,
                embedding VECTOR,
                document VARCHAR,
                cmetadata JSONB
            )
            """;

    private static final String CREATE_METADATA_INDEX = """
            CREATE INDEX IF NOT EXISTS ix_cmetadata_gin
            ON langchain_pg_embedding USING gin (cmetadata jsonb_path_ops)
            """;

    private static final String CREATE_CONTEXT_TAG_INDEX = """
            DO $$
            BEGIN
                IF to_regclass('public.langchain_pg_embedding') IS NOT NULL THEN
                    CREATE INDEX IF NOT EXISTS ix_langchain_pg_embedding_context_tag
                    ON langchain_pg_embedding ((cmetadata->>'context_tag'));
                END IF;
            END $$;
            """;

    private static final String INSERT_EMBEDDING = """
            INSERT INTO langchain_pg_embedding (id, collection_id, embedding, document, cmetadata)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final PgVectorProperties properties;

    public PgVectorStore(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
            OpenAiClient openAiClient, ObjectMapper objectMapper, PgVectorProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String collectionName() {
        return properties.collection();
    }

    /** Creates the extension, tables and indexes when missing, then makes sure the collection exists. */
    public void initialize() {
        jdbcTemplate.execute(CREATE_EXTENSION);
        jdbcTemplate.execute(CREATE_COLLECTION_TABLE);
        jdbcTemplate.execute(CREATE_EMBEDDING_TABLE);
        jdbcTemplate.execute(CREATE_METADATA_INDEX);
        ensureContextTagIndex();
        getOrCreateCollection();
    }

    public void ensureContextTagIndex() {
        jdbcTemplate.execute(CREATE_CONTEXT_TAG_INDEX);
    }

    /**
     * Embeds and stores the given documents.
     *
     * @param preDeleteCollection when true, the existing collection (and its embeddings) is dropped first
     */
    public void addDocuments(List<Document> documents, boolean preDeleteCollection) {
        List<float[]> embeddings = documents.isEmpty()
                ? List.of()
                : openAiClient.embedDocuments(documents.stream().map(Document::pageContent).toList());

        transactionTemplate.executeWithoutResult(status -> {
            if (preDeleteCollection) {
                deleteCollection();
            }
            UUID collectionId = getOrCreateCollection();
            if (!documents.isEmpty()) {
                insertEmbeddings(collectionId, documents, embeddings);
            }
        });
        ensureContextTagIndex();
    }

    public List<ScoredDocument> similaritySearchWithRelevanceScores(String query, int k, MetadataFilter filter) {
        float[] queryEmbedding = openAiClient.embedQuery(query);
        UUID collectionId = findCollectionId();
        if (collectionId == null) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT document, cmetadata, embedding <=> CAST(? AS vector) AS distance
                FROM langchain_pg_embedding
                WHERE collection_id = ?
                """);
        if (filter != null) {
            // The key is inlined (and validated) so the query can use the metadata expression index.
            sql.append("  AND cmetadata->>'").append(filter.key()).append("' = ?\n");
        }
        sql.append("ORDER BY distance ASC\nLIMIT ?");

        String queryVector = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(sql.toString(), statement -> {
            int parameter = 1;
            statement.setObject(parameter++, queryVector, Types.OTHER);
            statement.setObject(parameter++, collectionId);
            if (filter != null) {
                statement.setString(parameter++, filter.value());
            }
            statement.setInt(parameter, k);
        }, (resultSet, rowNumber) -> {
            Document document = new Document(resultSet.getString("document"),
                    parseMetadata(resultSet.getString("cmetadata")));
            // LangChain reports cosine relevance as 1 - distance.
            return new ScoredDocument(document, 1.0 - resultSet.getDouble("distance"));
        });
    }

    private void insertEmbeddings(UUID collectionId, List<Document> documents, List<float[]> embeddings) {
        jdbcTemplate.batchUpdate(INSERT_EMBEDDING, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                Document document = documents.get(index);
                statement.setString(1, UUID.randomUUID().toString());
                statement.setObject(2, collectionId);
                statement.setObject(3, toVectorLiteral(embeddings.get(index)), Types.OTHER);
                statement.setString(4, document.pageContent());
                statement.setObject(5, writeMetadata(document.metadata()), Types.OTHER);
            }

            @Override
            public int getBatchSize() {
                return documents.size();
            }
        });
        log.info("Stored {} chunks in collection {}.", documents.size(), collectionName());
    }

    private void deleteCollection() {
        jdbcTemplate.update("DELETE FROM langchain_pg_collection WHERE name = ?", collectionName());
    }

    private UUID getOrCreateCollection() {
        UUID existing = findCollectionId();
        if (existing != null) {
            return existing;
        }
        jdbcTemplate.update("""
                INSERT INTO langchain_pg_collection (uuid, name, cmetadata)
                VALUES (?, ?, ?)
                ON CONFLICT (name) DO NOTHING
                """,
                preparedStatement -> {
                    preparedStatement.setObject(1, UUID.randomUUID());
                    preparedStatement.setString(2, collectionName());
                    preparedStatement.setObject(3, "{}", Types.OTHER);
                });
        UUID created = findCollectionId();
        if (created == null) {
            throw new IllegalStateException("Unable to create collection " + collectionName() + ".");
        }
        return created;
    }

    private UUID findCollectionId() {
        List<UUID> ids = jdbcTemplate.query("SELECT uuid FROM langchain_pg_collection WHERE name = ?",
                (resultSet, rowNumber) -> resultSet.getObject("uuid", UUID.class),
                collectionName());
        return ids.isEmpty() ? null : ids.get(0);
    }

    static String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder(embedding.length * 12 + 2);
        builder.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(json, new TypeReference<>() {
            });
            return new LinkedHashMap<>(metadata);
        } catch (JsonProcessingException exception) {
            log.warn("Ignoring unreadable chunk metadata: {}", exception.getOriginalMessage());
            return new LinkedHashMap<>();
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata != null ? metadata : Map.of());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize chunk metadata.", exception);
        }
    }
}
