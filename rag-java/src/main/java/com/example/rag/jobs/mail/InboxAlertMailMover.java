package com.example.rag.jobs.mail;

import com.example.rag.config.JobAgentProperties;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * Moves job-alert messages from the IMAP inbox into dedicated Gmail labels/folders.
 *
 * <ul>
 *   <li>LinkedIn → {@code jobalerts_linkdin}
 *   <li>Glassdoor → {@code glassdoor}
 * </ul>
 */
@Component
public class InboxAlertMailMover {

    private final JobAgentProperties.Mail settings;
    private final String sourceFolder;
    private final List<MoveRule> rules;

    public InboxAlertMailMover(JobAgentProperties properties) {
        this.settings = properties.mail();
        this.sourceFolder = properties.mail().folder();
        this.rules = List.of(
                new MoveRule(properties.alertSenderAddresses(), properties.mail().archiveFolder()),
                new MoveRule(properties.glassdoorSenderAddresses(), properties.mail().glassdoorFolder()));
    }

    /** Apply all configured move rules (LinkedIn + Glassdoor) in one IMAP session. */
    public BatchMoveResult moveAll() {
        if (!settings.isConfigured()) {
            throw new IllegalStateException(
                    "IMAP is not configured. Set JOBS_MAIL_HOST, JOBS_MAIL_USERNAME, and JOBS_MAIL_PASSWORD.");
        }

        Properties config = new Properties();
        config.put("mail.store.protocol", settings.protocol());
        config.put("mail." + settings.protocol() + ".host", settings.host());
        config.put("mail." + settings.protocol() + ".port", String.valueOf(settings.port()));
        config.put("mail." + settings.protocol() + ".ssl.enable", "true");
        config.put("mail." + settings.protocol() + ".connectiontimeout", "20000");
        config.put("mail." + settings.protocol() + ".timeout", "60000");

        Session session = Session.getInstance(config);
        try (Store store = session.getStore(settings.protocol())) {
            store.connect(settings.host(), settings.port(), settings.username(), settings.password());
            return moveAll(store);
        } catch (MessagingException exception) {
            throw new IllegalStateException(
                    "Unable to move inbox alerts for " + settings.username() + ": " + exception.getMessage(),
                    exception);
        }
    }

    private BatchMoveResult moveAll(Store store) throws MessagingException {
        Folder inbox = store.getFolder(sourceFolder);
        if (!inbox.exists()) {
            throw new IllegalStateException("Source folder does not exist: " + sourceFolder);
        }
        inbox.open(Folder.READ_WRITE);
        try {
            List<MoveResult> results = new ArrayList<>(rules.size());
            for (MoveRule rule : rules) {
                results.add(moveRule(store, inbox, rule));
            }
            return new BatchMoveResult(sourceFolder, List.copyOf(results));
        } finally {
            if (inbox.isOpen()) {
                inbox.close(false);
            }
        }
    }

    private MoveResult moveRule(Store store, Folder inbox, MoveRule rule) throws MessagingException {
        Folder archive = ensureFolder(store, rule.targetFolder());
        archive.open(Folder.READ_WRITE);
        try {
            Message[] matches = inbox.search(senderTerm(rule.senders()));
            if (matches.length == 0) {
                return new MoveResult(sourceFolder, rule.targetFolder(), rule.senders(), 0, List.of());
            }

            List<MovedMessage> moved = summarize(matches);
            inbox.copyMessages(matches, archive);
            inbox.setFlags(matches, new Flags(Flags.Flag.DELETED), true);
            inbox.expunge();
            return new MoveResult(sourceFolder, rule.targetFolder(), rule.senders(), moved.size(), moved);
        } finally {
            if (archive.isOpen()) {
                archive.close(false);
            }
        }
    }

    private static Folder ensureFolder(Store store, String name) throws MessagingException {
        Folder folder = store.getFolder(name);
        if (!folder.exists() && !folder.create(Folder.HOLDS_MESSAGES)) {
            throw new IllegalStateException("Unable to create IMAP folder/label: " + name);
        }
        return folder;
    }

    static SearchTerm senderTerm(List<String> senders) {
        if (senders == null || senders.isEmpty()) {
            throw new IllegalArgumentException("At least one sender is required");
        }
        SearchTerm[] terms = senders.stream()
                .map(FromStringTerm::new)
                .toArray(SearchTerm[]::new);
        return terms.length == 1 ? terms[0] : new OrTerm(terms);
    }

    private static List<MovedMessage> summarize(Message[] messages) throws MessagingException {
        List<MovedMessage> moved = new ArrayList<>(messages.length);
        for (Message message : messages) {
            String from = firstFrom(message);
            String subject = message.getSubject() == null ? "" : message.getSubject();
            moved.add(new MovedMessage(from, subject));
        }
        return moved;
    }

    private static String firstFrom(Message message) throws MessagingException {
        if (message.getFrom() == null || message.getFrom().length == 0) {
            return "";
        }
        return message.getFrom()[0].toString();
    }

    public record MoveRule(List<String> senders, String targetFolder) {
    }

    public record MovedMessage(String from, String subject) {
    }

    public record MoveResult(
            String sourceFolder,
            String targetFolder,
            List<String> senders,
            int movedCount,
            List<MovedMessage> messages) {
    }

    public record BatchMoveResult(String sourceFolder, List<MoveResult> results) {
        public int totalMoved() {
            return results.stream().mapToInt(MoveResult::movedCount).sum();
        }
    }
}
