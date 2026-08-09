package com.example.rag.jobs.mail;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;

/** Flattens a MIME tree into the plain html/text bodies the parser needs. */
final class MimeSupport {

    private MimeSupport() {
    }

    static EmailMessage toEmailMessage(Message message, String fallbackId) throws MessagingException, IOException {
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();
        collect(message, html, text);

        return new EmailMessage(
                messageId(message, fallbackId),
                message.getSubject() != null ? message.getSubject() : "",
                firstFrom(message),
                receivedAt(message),
                html.toString(),
                text.toString());
    }

    private static void collect(Part part, StringBuilder html, StringBuilder text)
            throws MessagingException, IOException {
        if (part.isMimeType("text/html")) {
            html.append(asString(part.getContent()));
        } else if (part.isMimeType("text/plain")) {
            text.append(asString(part.getContent()));
        } else if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), html, text);
            }
        }
    }

    private static String asString(Object content) {
        return content != null ? content.toString() : "";
    }

    private static String messageId(Message message, String fallbackId) throws MessagingException {
        String[] header = message.getHeader("Message-ID");
        return header != null && header.length > 0 ? header[0] : fallbackId;
    }

    static String firstFrom(Message message) throws MessagingException {
        Address[] senders = message.getFrom();
        if (senders == null || senders.length == 0) {
            return "";
        }
        if (senders[0] instanceof InternetAddress internetAddress && internetAddress.getAddress() != null) {
            return internetAddress.getAddress();
        }
        return senders[0].toString();
    }

    static Instant receivedAt(Message message) throws MessagingException {
        Date received = message.getReceivedDate();
        if (received == null) {
            received = message.getSentDate();     // .eml files off disk usually carry no Received date
        }
        return received != null ? received.toInstant() : Instant.EPOCH;
    }
}
