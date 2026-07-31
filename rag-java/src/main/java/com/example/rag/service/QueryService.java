package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.error.ApiException;
import com.example.rag.openai.OpenAiClient;
import com.example.rag.vectorstore.MetadataFilter;
import com.example.rag.vectorstore.PgVectorStore;
import com.example.rag.vectorstore.ScoredDocument;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Retrieves the most relevant chunks for a question and asks the model to answer from them. */
@Service
public class QueryService {

    private static final String CONTEXT_SEPARATOR = "\n\n---\n\n";

    private final PgVectorStore vectorStore;
    private final OpenAiClient openAiClient;
    private final RagProperties properties;

    public QueryService(PgVectorStore vectorStore, OpenAiClient openAiClient, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.openAiClient = openAiClient;
        this.properties = properties;
    }

    public QueryResult answer(String queryText, Integer k, Double minRelevance, String contextTag) {
        int limit = k != null ? k : properties.defaultK();
        double threshold = minRelevance != null ? minRelevance : properties.defaultMinRelevance();
        MetadataFilter filter = contextTag != null && !contextTag.isBlank()
                ? MetadataFilter.contextTag(contextTag)
                : null;

        List<ScoredDocument> results = vectorStore.similaritySearchWithRelevanceScores(queryText, limit, filter);
        if (results.isEmpty() || results.get(0).score() < threshold) {
            throw ApiException.notFound("Unable to find matching results.");
        }

        String contextText = results.stream()
                .map(result -> result.document().pageContent())
                .collect(Collectors.joining(CONTEXT_SEPARATOR));
        String prompt = PromptTemplate.format(PromptTemplate.PROMPT_TEMPLATE,
                Map.of("context", contextText, "question", queryText));
        String response = openAiClient.chat(prompt);
        List<String> sources = results.stream()
                .map(result -> result.document().source())
                .toList();
        return new QueryResult(prompt, response, sources);
    }
}
