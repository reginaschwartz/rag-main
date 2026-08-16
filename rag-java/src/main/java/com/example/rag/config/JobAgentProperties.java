package com.example.rag.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the LinkedIn job-alert agent: which mailbox to read, from when, and against which resume. */
@ConfigurationProperties(prefix = "jobs")
public record JobAgentProperties(
        String sender,
        String alertSenders,
        String glassdoorSenders,
        String since,
        String resumePath,
        String source,
        String emlPath,
        Integer maxEmails,
        Integer maxJobs,
        String reportPath,
        Mail mail,
        Notify notification) {

    public static final String SOURCE_IMAP = "imap";
    public static final String SOURCE_EML = "eml";

    public static final List<String> DEFAULT_ALERT_SENDERS = List.of(
            "jobalerts-noreply@linkedin.com",
            "jobs-noreply@linkedin.com");

    public static final List<String> DEFAULT_GLASSDOOR_SENDERS = List.of("noreply@glassdoor.com");

    public JobAgentProperties {
        sender = hasText(sender) ? sender : "jobalerts-noreply@linkedin.com";
        alertSenders = hasText(alertSenders)
                ? alertSenders.strip()
                : String.join(",", DEFAULT_ALERT_SENDERS);
        glassdoorSenders = hasText(glassdoorSenders)
                ? glassdoorSenders.strip()
                : String.join(",", DEFAULT_GLASSDOOR_SENDERS);
        since = hasText(since) ? since.strip() : LocalDate.now().minusDays(7).toString();
        // Prefer repo-root data/; fall back to ../data when the process cwd is rag-java/.
        resumePath = hasText(resumePath)
                ? resumePath
                : firstExisting(
                        "data/Java_Software_Back_End_Developer-General.docx",
                        "../data/Java_Software_Back_End_Developer-General.docx");
        source = hasText(source) ? source.toLowerCase() : SOURCE_IMAP;
        emlPath = hasText(emlPath)
                ? emlPath
                : firstExisting("data/mail-samples", "../data/mail-samples");
        maxEmails = maxEmails != null ? maxEmails : 25;
        maxJobs = maxJobs != null ? maxJobs : 100;
        reportPath = hasText(reportPath) ? reportPath : "reports/job-fitness-report.json";
        mail = mail != null ? mail : new Mail(null, null, null, null, null, null, null, null);
        notification = notification != null ? notification : new Notify(null, null, null, null);
    }

    /** LinkedIn senders whose INBOX mail is moved into {@link Mail#archiveFolder()}. */
    public List<String> alertSenderAddresses() {
        return parseSenders(alertSenders, DEFAULT_ALERT_SENDERS);
    }

    /** Glassdoor senders whose INBOX mail is moved into {@link Mail#glassdoorFolder()}. */
    public List<String> glassdoorSenderAddresses() {
        return parseSenders(glassdoorSenders, DEFAULT_GLASSDOOR_SENDERS);
    }

    public static List<String> parseAlertSenders(String raw) {
        return parseSenders(raw, DEFAULT_ALERT_SENDERS);
    }

    public static List<String> parseGlassdoorSenders(String raw) {
        return parseSenders(raw, DEFAULT_GLASSDOOR_SENDERS);
    }

    public static List<String> parseSenders(String raw, List<String> defaults) {
        if (raw == null || raw.isBlank()) {
            return defaults;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String part : raw.split("[,;]")) {
            String address = part.strip().toLowerCase(Locale.ROOT);
            if (!address.isEmpty()) {
                unique.add(address);
            }
        }
        return unique.isEmpty() ? defaults : List.copyOf(new ArrayList<>(unique));
    }

    public LocalDate sinceDate() {
        try {
            return LocalDate.parse(since);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "jobs.since must be an ISO date (yyyy-MM-dd), got: " + since, exception);
        }
    }

    public boolean readsFromEmlFolder() {
        return SOURCE_EML.equals(source);
    }

    public record Mail(
            String host,
            Integer port,
            String protocol,
            String username,
            String password,
            String folder,
            String archiveFolder,
            String glassdoorFolder) {

        public Mail {
            protocol = hasText(protocol) ? protocol : "imaps";
            port = port != null ? port : 993;
            folder = hasText(folder) ? folder : "INBOX";
            archiveFolder = hasText(archiveFolder) ? archiveFolder : "jobalerts_linkdin";
            glassdoorFolder = hasText(glassdoorFolder) ? glassdoorFolder : "glassdoor";
        }

        public boolean isConfigured() {
            return hasText(host) && hasText(username);
        }
    }

    /** Outbound email for jobs whose fitness rank is at/above {@code minRank}. */
    public record Notify(Boolean enabled, String to, Integer minRank, Smtp smtp) {

        public Notify {
            enabled = enabled == null || enabled;
            to = hasText(to) ? to : "rinatschwartz770@gmail.com";
            minRank = minRank != null ? minRank : 80;
            smtp = smtp != null ? smtp : new Smtp(null, null, null, null, null, null);
        }
    }

    public record Smtp(
            String host,
            Integer port,
            Boolean startTls,
            String username,
            String password,
            String from) {

        public Smtp {
            host = hasText(host) ? host : "smtp.gmail.com";
            port = port != null ? port : 587;
            startTls = startTls == null || startTls;
            // From defaults to the SMTP username when not set explicitly.
            from = hasText(from) ? from : username;
        }

        public boolean isConfigured() {
            return hasText(host) && hasText(username) && hasText(password);
        }

        public String fromAddress() {
            return hasText(from) ? from : username;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstExisting(String primary, String fallback) {
        if (Files.exists(Path.of(primary))) {
            return primary;
        }
        if (Files.exists(Path.of(fallback))) {
            return fallback;
        }
        return primary;
    }
}
