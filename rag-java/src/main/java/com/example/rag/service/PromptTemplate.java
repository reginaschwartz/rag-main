package com.example.rag.service;

import java.util.Map;

/** The prompt used to answer questions from retrieved context. */
public final class PromptTemplate {

    public static final String PROMPT_TEMPLATE = """

            Answer the question based only on the following context:

            {context}

            ---

            Answer the question based on the above context: {question}
            """;

    private PromptTemplate() {
    }

    public static String format(String template, Map<String, String> values) {
        StringBuilder result = new StringBuilder(template.length());
        int cursor = 0;
        while (cursor < template.length()) {
            char character = template.charAt(cursor);
            if (character != '{') {
                result.append(character);
                cursor++;
                continue;
            }
            int closing = template.indexOf('}', cursor);
            if (closing < 0) {
                result.append(template, cursor, template.length());
                break;
            }
            String key = template.substring(cursor + 1, closing);
            // Substitution happens in a single pass, so braces inside values are never re-expanded.
            if (values.containsKey(key)) {
                result.append(values.get(key));
            } else {
                result.append(template, cursor, closing + 1);
            }
            cursor = closing + 1;
        }
        return result.toString();
    }
}
