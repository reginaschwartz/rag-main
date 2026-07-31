package com.example.rag.web.dto;

import java.util.List;

public record QueryResponse(String response, List<String> sources) {
}
