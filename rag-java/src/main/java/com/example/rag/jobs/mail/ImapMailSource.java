package com.example.rag.jobs.mail;

import com.example.rag.config.JobAgentProperties;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reads job alerts straight from an IMAP mailbox. Use an app password, not the account password. */
@Component
@ConditionalOnProperty(name = "jobs.source", havingValue = JobAgentProperties.SOURCE_IMAP, matchIfMissing = true)
public class ImapMailSource implements MailSource {

    private final JobAgentProperties.Mail settings;

    public ImapMailSource(JobAgentProperties properties) {
        this.settings = properties.mail();
    }

    @Override
    public List<EmailMessage> fetch(String sender, Instant since, int maxEmails) {
        if (!settings.isConfigured()) {
            throw new IllegalStateException(
                    "IMAP is not configured. Set JOBS_MAIL_HOST and JOBS_MAIL_USERNAME, "
                            + "or switch to the offline reader with --jobs.source=eml.");
        }

        Properties config = new Properties();
        config.put("mail.store.protocol", settings.protocol());
        config.put("mail." + settings.protocol() + ".host", settings.host());
        config.put("mail." + settings.protocol() + ".port", String.valueOf(settings.port()));
        config.put("mail." + settings.protocol() + ".ssl.enable", "true");
        config.put("mail." + settings.protocol() + ".connectiontimeout", "20000");
        config.put("mail." + settings.protocol() + ".timeout", "30000");

        Session session = Session.getInstance(config);
        try (Store store = session.getStore(settings.protocol())) {
            store.connect(settings.host(), settings.port(), settings.username(), settings.password());
            Folder folder = store.getFolder(settings.folder());
            folder.open(Folder.READ_ONLY);
            try {
                return read(folder, sender, since, maxEmails);
            } finally {
                folder.close(false);
            }
        } catch (MessagingException exception) {
            throw new IllegalStateException("Unable to read mailbox " + settings.username() + ": "
                    + exception.getMessage(), exception);
        }
    }

    private List<EmailMessage> read(Folder folder, String sender, Instant since, int maxEmails)
            throws MessagingException {
        // IMAP date search has day granularity and ignores the time, so results are filtered again below.
        SearchTerm term = new AndTerm(
                new FromStringTerm(sender),
                new ReceivedDateTerm(ComparisonTerm.GE, Date.from(since)));

        Message[] found = folder.search(term);
        List<Message> newestFirst = new ArrayList<>(Arrays.asList(found));
        newestFirst.sort(Comparator.comparing(ImapMailSource::receivedAtQuietly).reversed());

        List<EmailMessage> messages = new ArrayList<>();
        for (Message message : newestFirst) {
            if (messages.size() >= maxEmails) {
                break;
            }
            try {
                EmailMessage email = MimeSupport.toEmailMessage(message, "imap-" + message.getMessageNumber());
                if (!email.receivedAt().isBefore(since)) {
                    messages.add(email);
                }
            } catch (IOException | MessagingException exception) {
                throw new IllegalStateException("Unable to read message " + message.getMessageNumber(), exception);
            }
        }
        return messages;
    }

    private static Instant receivedAtQuietly(Message message) {
        try {
            return MimeSupport.receivedAt(message);
        } catch (MessagingException exception) {
            return Instant.EPOCH;
        }
    }

    @Override
    public String describe() {
        return "IMAP %s@%s/%s".formatted(settings.username(), settings.host(), settings.folder());
    }
}
