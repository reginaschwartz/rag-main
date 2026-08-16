package com.example.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;

/** One analytics event published to Kafka and landed in BigQuery. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Event(
        String eventId,
        String userId,
        String eventType,
        Instant eventTs,
        Double amount,
        String page) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static Event random(int sequence) {
        String[] types = {"page_view", "add_to_cart", "purchase", "signup"};
        String type = types[sequence % types.length];
        String userId = "user-" + (sequence % 5);
        Double amount = "purchase".equals(type) ? 10.0 + (sequence % 7) * 3.5 : null;
        String page = "page_view".equals(type) ? "/page/" + (sequence % 4) : null;
        return new Event(UUID.randomUUID().toString(), userId, type, Instant.now(), amount, page);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize event", exception);
        }
    }

    public static Event fromJson(String json) {
        try {
            return MAPPER.readValue(json, Event.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid event JSON: " + json, exception);
        }
    }
}
