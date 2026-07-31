package com.example.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record QueryRequest(
        @JsonProperty("query_text") @NotBlank String queryText,
        @JsonProperty("k") @Positive Integer k,
        @JsonProperty("min_relevance") Double minRelevance,
        @JsonProperty("context_tag") String contextTag) {
}
