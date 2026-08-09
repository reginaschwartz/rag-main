package com.example.rag.jobs.mail;

import com.example.rag.config.JobAgentProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Replays .eml files from a folder, so the agent can be exercised without mailbox credentials. */
@Component
@ConditionalOnProperty(name = "jobs.source", havingValue = JobAgentProperties.SOURCE_EML)
public class EmlFolderMailSource implements MailSource {

    private final Path folder;

    public EmlFolderMailSource(JobAgentProperties properties) {
        this.folder = Path.of(properties.emlPath());
    }

    @Override
    public List<EmailMessage> fetch(String sender, Instant since, int maxEmails) {
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("No such folder of .eml files: " + folder.toAbsolutePath());
        }

        Session session = Session.getInstance(new Properties());
        List<EmailMessage> messages = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> emlFiles = files
                    .filter(path -> path.toString().toLowerCase().endsWith(".eml"))
                    .sorted()
                    .toList();

            for (Path file : emlFiles) {
                try (InputStream in = Files.newInputStream(file)) {
                    EmailMessage email = MimeSupport.toEmailMessage(
                            new MimeMessage(session, in), file.getFileName().toString());
                    if (matches(email, sender, since)) {
                        messages.add(email);
                    }
                }
            }
        } catch (IOException | MessagingException exception) {
            throw new IllegalStateException("Unable to read .eml files from " + folder, exception);
        }

        return messages.stream()
                .sorted(Comparator.comparing(EmailMessage::receivedAt).reversed())
                .limit(maxEmails)
                .toList();
    }

    private static boolean matches(EmailMessage email, String sender, Instant since) {
        return email.from() != null
                && email.from().equalsIgnoreCase(sender)
                && !email.receivedAt().isBefore(since);
    }

    @Override
    public String describe() {
        return ".eml folder " + folder.toAbsolutePath();
    }
}
