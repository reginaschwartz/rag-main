package com.example.rag.jobs.mail;

import java.time.Instant;

/** One fetched message, reduced to the parts the job agent cares about. */
public record EmailMessage(
        String id,
        String subject,
        String from,
        Instant receivedAt,
        String htmlBody,
        String textBody) {

    public String bestBody() {
        return htmlBody != null && !htmlBody.isBlank() ? htmlBody : textBody;
    }
}
