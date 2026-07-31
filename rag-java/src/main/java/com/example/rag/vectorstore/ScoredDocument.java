package com.example.rag.vectorstore;

import com.example.rag.document.Document;

/** A search hit: the stored document plus its relevance score in the {@code [0, 1]} range. */
public record ScoredDocument(Document document, double score) {
}
