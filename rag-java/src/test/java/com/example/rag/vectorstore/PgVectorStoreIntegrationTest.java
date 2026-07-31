package com.example.rag.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.example.rag.document.Document;
import com.example.rag.openai.OpenAiClient;
import org.assertj.core.data.Offset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Exercises the real pgvector SQL. Opt in by pointing {@code RAG_IT_CONNECTION} at a database with
 * the pgvector extension available, for example:
 *
 * <pre>
 * docker compose up -d postgres
 * RAG_IT_CONNECTION=postgresql://raguser:ragpass@localhost:5432/ragdb mvn test
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"pgvector.collection=rag_integration_test", "openai.api-key=test-key"})
@EnabledIfEnvironmentVariable(named = "RAG_IT_CONNECTION", matches = ".+")
class PgVectorStoreIntegrationTest {

    private static final Map<String, float[]> EMBEDDINGS = Map.of(
            "alpha", new float[] {1f, 0f, 0f},
            "beta", new float[] {0f, 1f, 0f},
            "gamma", new float[] {0f, 0f, 1f});

    @DynamicPropertySource
    static void connection(DynamicPropertyRegistry registry) {
        registry.add("pgvector.connection", () -> System.getenv("RAG_IT_CONNECTION"));
    }

    @MockitoBean
    private OpenAiClient openAiClient;

    @Autowired
    private PgVectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void stubEmbeddings() {
        given(openAiClient.embedDocuments(anyList())).willAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(PgVectorStoreIntegrationTest::embed).toList();
        });
        given(openAiClient.embedQuery(anyString()))
                .willAnswer(invocation -> embed(invocation.getArgument(0)));
    }

    @Test
    void createsTheLangChainCompatibleSchema() {
        vectorStore.initialize();
        // Startup already initialized the store, so this also proves the DDL is repeatable.
        vectorStore.initialize();

        assertThat(tableExists("langchain_pg_collection")).isTrue();
        assertThat(tableExists("langchain_pg_embedding")).isTrue();
        assertThat(indexExists("ix_langchain_pg_embedding_context_tag")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM langchain_pg_collection WHERE name = ?", Integer.class,
                vectorStore.collectionName())).isEqualTo(1);
    }

    @Test
    void storesChunksAndRanksThemByCosineRelevance() {
        vectorStore.addDocuments(List.of(chunk("alpha", "alice.md", "book"), chunk("beta", "notes.md", null)), true);

        List<ScoredDocument> results = vectorStore.similaritySearchWithRelevanceScores("alpha", 10, null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).document().pageContent()).isEqualTo("alpha");
        assertThat(results.get(0).score()).isCloseTo(1.0, Offset.offset(1e-6));
        assertThat(results.get(0).document().metadata()).containsEntry("source", "alice.md");
        assertThat(results.get(0).document().metadata()).containsEntry("context_tag", "book");
        assertThat(results.get(1).document().pageContent()).isEqualTo("beta");
        assertThat(results.get(1).score()).isCloseTo(0.0, Offset.offset(1e-6));
    }

    @Test
    void filtersByContextTag() {
        vectorStore.addDocuments(List.of(chunk("alpha", "alice.md", "book"), chunk("beta", "notes.md", "notes")), true);

        assertThat(vectorStore.similaritySearchWithRelevanceScores("alpha", 10,
                MetadataFilter.contextTag("notes")))
                .singleElement()
                .satisfies(result -> assertThat(result.document().pageContent()).isEqualTo("beta"));
        assertThat(vectorStore.similaritySearchWithRelevanceScores("alpha", 10,
                MetadataFilter.contextTag("missing"))).isEmpty();
    }

    @Test
    void resetsTheCollectionWhenRequested() {
        vectorStore.addDocuments(List.of(chunk("alpha", "alice.md", null), chunk("beta", "notes.md", null)), true);
        vectorStore.addDocuments(List.of(chunk("gamma", "later.md", null)), true);

        List<ScoredDocument> results = vectorStore.similaritySearchWithRelevanceScores("gamma", 10, null);

        assertThat(results).singleElement()
                .satisfies(result -> assertThat(result.document().pageContent()).isEqualTo("gamma"));
    }

    @Test
    void appendsWhenTheCollectionIsKept() {
        vectorStore.addDocuments(List.of(chunk("alpha", "alice.md", null)), true);
        vectorStore.addDocuments(List.of(chunk("beta", "notes.md", null)), false);

        assertThat(vectorStore.similaritySearchWithRelevanceScores("alpha", 10, null)).hasSize(2);
    }

    @Test
    void honoursTheResultLimit() {
        vectorStore.addDocuments(
                List.of(chunk("alpha", "a.md", null), chunk("beta", "b.md", null), chunk("gamma", "c.md", null)), true);

        assertThat(vectorStore.similaritySearchWithRelevanceScores("alpha", 2, null)).hasSize(2);
    }

    private static Document chunk(String content, String source, String contextTag) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", source);
        metadata.put("start_index", 0);
        if (contextTag != null) {
            metadata.put("context_tag", contextTag);
        }
        return new Document(content, metadata);
    }

    private static float[] embed(String text) {
        float[] embedding = EMBEDDINGS.get(text);
        if (embedding == null) {
            throw new IllegalArgumentException("No stub embedding for " + text);
        }
        return embedding;
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table));
    }

    private boolean indexExists(String index) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT count(*) > 0 FROM pg_indexes WHERE indexname = ?", Boolean.class, index));
    }
}
