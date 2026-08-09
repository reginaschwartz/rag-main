package com.example.rag.jobs;

import com.example.rag.openai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Tool the job agent calls for each posting: asks the LLM to rank how well the resume fits the job
 * on a 0–100 scale and return a short rationale.
 */
@Component
public class JobFitnessTool {

    private static final String SYSTEM = """
            You are a recruiting assistant. Score how well the candidate resume fits the job posting.
            Return ONLY valid JSON with this exact shape:
            {"fitnessRank": <integer 0-100>, "rationale": "<one or two sentences>"}
            Scoring guide:
            - 80-100: strong match on core skills, seniority, and domain
            - 60-79: good match with some gaps
            - 40-59: partial match; would need stretch
            - 0-39: weak match
            Be strict. Prefer lower scores when the job is a different role family than the resume.
            """;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public JobFitnessTool(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public JobFitnessResult checkFitness(Resume resume, JobPosting job) {
        String userPrompt = """
                ## Candidate Resume
                %s

                ## Job posting
                Title: %s
    
                Job description: %s
                """.formatted(
                resume.summaryForPrompt(12_000),
                job.title(),

                job.jobDescription().isBlank() ? "" : job.jobDescription());

        String raw = openAiClient.chat(SYSTEM, userPrompt, 0.0);
        return JobFitnessResult.from(job, parseRank(raw), parseRationale(raw));
    }

    private int parseRank(String raw) {
        JsonNode node = parseJson(raw);
        if (node != null && node.has("fitnessRank")) {
            return node.get("fitnessRank").asInt(0);
        }
        throw new IllegalStateException("LLM fitness response missing fitnessRank: " + raw);
    }

    private String parseRationale(String raw) {
        JsonNode node = parseJson(raw);
        if (node != null && node.has("rationale")) {
            return node.get("rationale").asText("");
        }
        return "";
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed.substring(start, end + 1));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse LLM fitness JSON: " + raw, exception);
        }
    }
}
