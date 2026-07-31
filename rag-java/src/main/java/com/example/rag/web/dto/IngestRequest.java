package com.example.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IngestRequest(@JsonProperty("context_tag") String contextTag) {
}
