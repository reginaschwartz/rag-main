package com.example.rag.jobs;

import com.example.rag.config.JobAgentProperties;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emails jobs whose LLM fitness rank is at or above the configured threshold
 * (default 80) to the configured recipient.
 */
@Component
public class HighFitJobNotifier {

    private static final Logger log = LoggerFactory.getLogger(HighFitJobNotifier.class);

    private final JobAgentProperties properties;

    public HighFitJobNotifier(JobAgentProperties properties) {
        this.properties = properties;
    }

    /**
     * @return number of jobs included in the notification email (0 if nothing was sent)
     */
    public int notifyHighFits(FitnessReport report) {
        JobAgentProperties.Notify settings = properties.notification();
        if (!settings.enabled()) {
            log.info("High-fit email notification is disabled.");
            return 0;
        }

        List<JobFitnessResult> highFits = report.results().stream()
                .filter(result -> result.fitnessRank() >= settings.minRank())
                .toList();
        if (highFits.isEmpty()) {
            log.info("No jobs at or above fitness rank {}; skipping email.", settings.minRank());
            return 0;
        }

        if (!settings.smtp().isConfigured()) {
            log.warn(
                    "Found {} job(s) with rank >= {} for {}, but SMTP is not configured. "
                            + "Set JOBS_NOTIFY_SMTP_USERNAME and JOBS_NOTIFY_SMTP_PASSWORD "
                            + "(Gmail app password; host defaults to smtp.gmail.com).",
                    highFits.size(),
                    settings.minRank(),
                    settings.to());
            System.out.printf(
                    "SKIPPED email of %d high-fit job(s) to %s — configure JOBS_NOTIFY_SMTP_USERNAME / JOBS_NOTIFY_SMTP_PASSWORD.%n",
                    highFits.size(),
                    settings.to());
            return 0;
        }

        send(settings, highFits);
        log.info("Sent {} high-fit job(s) to {}", highFits.size(), settings.to());
        System.out.printf("Emailed %d high-fit job(s) to %s%n", highFits.size(), settings.to());
        return highFits.size();
    }

    private void send(JobAgentProperties.Notify settings, List<JobFitnessResult> highFits) {
        JobAgentProperties.Smtp smtp = settings.smtp();
        Properties config = new Properties();
        config.put("mail.smtp.host", smtp.host());
        config.put("mail.smtp.port", String.valueOf(smtp.port()));
        config.put("mail.smtp.auth", "true");
        config.put("mail.smtp.starttls.enable", String.valueOf(smtp.startTls()));
        config.put("mail.smtp.connectiontimeout", "20000");
        config.put("mail.smtp.timeout", "30000");

        Session session = Session.getInstance(config, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtp.username(), smtp.password());
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtp.fromAddress()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(settings.to(), false));
            message.setSubject("High-fit LinkedIn jobs (%d+%%) — %d match(es)"
                    .formatted(settings.minRank(), highFits.size()));
            message.setText(buildBody(settings.minRank(), highFits), "utf-8");
            Transport.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException(
                    "Unable to send high-fit job email to " + settings.to() + ": " + exception.getMessage(),
                    exception);
        }
    }

    static String buildBody(int minRank, List<JobFitnessResult> highFits) {
        StringBuilder body = new StringBuilder();
        body.append("Jobs with fitness rank >= ").append(minRank).append("%:\n\n");
        int index = 1;
        for (JobFitnessResult job : highFits) {
            body.append(index++).append(". ").append(job.jobTitle()).append('\n');
            if (!job.company().isBlank()) {
                body.append("   Company: ").append(job.company()).append('\n');
            }
            if (!job.location().isBlank()) {
                body.append("   Location: ").append(job.location()).append('\n');
            }
            body.append("   Fitness rank: ").append(job.fitnessRank()).append("%\n");
            body.append("   Apply: ").append(job.applyUrl()).append('\n');
            if (!job.rationale().isBlank()) {
                body.append("   Why: ").append(job.rationale()).append('\n');
            }
            body.append('\n');
        }
        return body.toString();
    }
}
