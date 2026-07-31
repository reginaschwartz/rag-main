package com.example.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    @Test
    void fillsContextAndQuestion() {
        String prompt = PromptTemplate.format(PromptTemplate.PROMPT_TEMPLATE,
                Map.of("context", "Alice met a rabbit.", "question", "Who did Alice meet?"));

        assertThat(prompt).isEqualTo("""

                Answer the question based only on the following context:

                Alice met a rabbit.

                ---

                Answer the question based on the above context: Who did Alice meet?
                """);
    }

    @Test
    void leavesBracesInsideValuesUntouched() {
        String prompt = PromptTemplate.format("{context}|{question}",
                Map.of("context", "{question}", "question", "q"));

        assertThat(prompt).isEqualTo("{question}|q");
    }

    @Test
    void keepsUnknownPlaceholders() {
        assertThat(PromptTemplate.format("{unknown} {question}", Map.of("question", "q")))
                .isEqualTo("{unknown} q");
    }
}
