package com.example.rag.document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A chunk of text plus its metadata, equivalent to LangChain's {@code Document}.
 *
 * <p>The metadata map is mutable so callers can enrich chunks after splitting (for example with a
 * {@code context_tag}).
 */
public record Document(String pageContent, Map<String, Object> metadata) {

    public Document {
        metadata = metadata != null ? metadata : new LinkedHashMap<>();
    }

    public Document(String pageContent) {
        this(pageContent, new LinkedHashMap<>());
    }

    public String source() {
        Object source = metadata.get("source");
        return source != null ? String.valueOf(source) : "";
    }
}
