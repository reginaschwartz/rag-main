package com.example.rag.service;

import java.util.List;

/** Outcome of a retrieval-augmented answer, including the prompt that produced it. */
public record QueryResult(String prompt, String response, List<String> sources) {
}
