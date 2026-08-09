package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.document.ContentExtractor;
import com.example.rag.document.DirectoryDocumentLoader;
import com.example.rag.document.Document;
import com.example.rag.document.RecursiveCharacterTextSplitter;
import com.example.rag.error.ApiException;
import com.example.rag.vectorstore.PgVectorStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/** Ingestion flows: bulk indexing from disk and single-file indexing from an upload. */
@Service
@ConditionalOnBean(PgVectorStore.class)
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RagProperties properties;
    private final DirectoryDocumentLoader documentLoader;
    private final ContentExtractor contentExtractor;
    private final PgVectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final RecursiveCharacterTextSplitter textSplitter;

    public IndexingService(RagProperties properties, DirectoryDocumentLoader documentLoader,
            ContentExtractor contentExtractor, PgVectorStore vectorStore, ObjectMapper objectMapper) {
        this.properties = properties;
        this.documentLoader = documentLoader;
        this.contentExtractor = contentExtractor;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.textSplitter = new RecursiveCharacterTextSplitter(properties.chunkSize(), properties.chunkOverlap(), true);
    }

    /** Bulk ingestion of the configured data directory, replacing the collection contents. */
    public IndexingResult generateDataStore() {
        List<Document> documents = loadDocuments();
        List<Document> chunks = splitText(documents);
        saveToPgVector(chunks, true);
        return new IndexingResult(documents.size(), chunks.size(), vectorStore.collectionName());
    }

    public IndexingResult indexUpload(byte[] content, String filename, String metadataJson,
            boolean resetCollection, String contextTag) {
        Map<String, Object> baseMetadata = parseMetadata(metadataJson);
        String resolvedSource = filename != null && !filename.isBlank() ? filename : "api_document";
        baseMetadata.putIfAbsent("source", resolvedSource);

        String text = contentExtractor.extract(content, resolvedSource);
        List<Document> documents = List.of(new Document(text, baseMetadata));
        List<Document> chunks = splitText(documents);
        chunks = setContextTag(chunks, contextTag);
        saveToPgVector(chunks, resetCollection);
        return new IndexingResult(documents.size(), chunks.size(), vectorStore.collectionName());
    }

    public List<Document> loadDocuments() {
        return documentLoader.load(properties.dataPath(), properties.globPattern());
    }

    public List<Document> splitText(List<Document> documents) {
        List<Document> chunks = textSplitter.splitDocuments(documents);
        log.info("Split {} documents into {} chunks.", documents.size(), chunks.size());
        if (!chunks.isEmpty() && log.isDebugEnabled()) {
            Document preview = chunks.get(Math.min(10, chunks.size() - 1));
            log.debug("{}", preview.pageContent());
            log.debug("{}", preview.metadata());
        }
        return chunks;
    }

    public List<Document> setContextTag(List<Document> chunks, String contextTag) {
        if (contextTag == null || contextTag.isBlank()) {
            return chunks;
        }
        for (Document chunk : chunks) {
            chunk.metadata().put("context_tag", contextTag);
        }
        return chunks;
    }

    public void saveToPgVector(List<Document> chunks, boolean preDeleteCollection) {
        vectorStore.addDocuments(chunks, preDeleteCollection);
        log.info("Saved {} chunks to collection {}.", chunks.size(), vectorStore.collectionName());
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(metadataJson);
        } catch (JsonProcessingException exception) {
            throw ApiException.badRequest("metadata_json must be a JSON object.");
        }
        if (!node.isObject()) {
            throw ApiException.badRequest("metadata_json must be a JSON object.");
        }
        Map<String, Object> metadata = objectMapper.convertValue(node, MAP_TYPE);
        return new LinkedHashMap<>(metadata);
    }

    public record IndexingResult(int documents, int chunks, String collection) {
    }
}
