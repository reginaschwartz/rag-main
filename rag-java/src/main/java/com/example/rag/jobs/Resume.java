package com.example.rag.jobs;

/** Candidate resume text loaded from disk and passed to the LLM fitness tool. */
public record Resume(String source, String text) {

    public String summaryForPrompt(int maxChars) {
        String body = text == null ? "" : text.strip();
        if (body.length() > maxChars) {
            body = body.substring(0, maxChars) + "\n...[truncated]";
        }
        return "Resume file: " + source + "\n\n" + body;
    }
}
