package com.example.rag.vectorstore;

import java.util.regex.Pattern;

/** Equality filter on a single metadata key, e.g. {@code context_tag = "book"}. */
public record MetadataFilter(String key, String value) {

    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9_]+$");

    public MetadataFilter {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Unsupported metadata filter key: " + key);
        }
    }

    public static MetadataFilter contextTag(String value) {
        return new MetadataFilter("context_tag", value);
    }
}
