package com.example.rag.openai;

import com.example.rag.config.OpenAiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Minimal OpenAI client covering the two calls the RAG flows need: embeddings and chat completion. */
@Component
public class OpenAiClient {

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiClient(RestClient openAiRestClient, OpenAiProperties properties) {
        this.restClient = openAiRestClient;
        this.properties = properties;
    }

    public float[] embedQuery(String text) {
        return embedDocuments(List.of(text)).get(0);
    }

    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>(texts.size());
        int batchSize = properties.embeddingBatchSize();
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
            embeddings.addAll(embedBatch(batch));
        }
        return embeddings;
    }

    private List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = post("/embeddings",
                new EmbeddingRequest(properties.embeddingModel(), texts),
                EmbeddingResponse.class);
        if (response == null || response.data() == null || response.data().size() != texts.size()) {
            throw new IllegalStateException("OpenAI returned an unexpected number of embeddings.");
        }
        return response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingData::index))
                .map(EmbeddingData::embedding)
                .toList();
    }

    public String chat(String prompt) {
        return chat(null, prompt, properties.temperature());
    }

    /** Chat with an optional system message and an explicit temperature (e.g. 0 for scoring tools). */
    public String chat(String systemPrompt, String userPrompt, Double temperature) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", userPrompt));
        ChatResponse response = post("/chat/completions",
                new ChatRequest(properties.chatModel(), messages, temperature),
                ChatResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI returned no chat completion choices.");
        }
        Message message = response.choices().get(0).message();
        return message != null && message.content() != null ? message.content() : "";
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set.");
        }
        try {
            return restClient.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, clientResponse) -> {
                        if (clientResponse.getStatusCode().isError()) {
                            String errorBody = new String(clientResponse.getBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                            throw new IllegalStateException("OpenAI request to " + path + " failed with status "
                                    + clientResponse.getStatusCode().value() + ": " + errorBody);
                        }
                        return clientResponse.bodyTo(responseType);
                    });
        } catch (RestClientException exception) {
            throw new IllegalStateException("OpenAI request to " + path + " failed: " + exception.getMessage(),
                    exception);
        }
    }

    record EmbeddingRequest(String model, List<String> input) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(int index, float[] embedding) {
    }

    record ChatRequest(String model, List<Message> messages, Double temperature) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }
}
