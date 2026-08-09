package com.example.rag.jobs;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Follows the "View job:" URL from an alert snippet and pulls the LinkedIn "About the job"
 * ({@code aboutTheJob}) body into {@link JobPosting#jobDescription()}.
 */
@Component
public class LinkedInJobDescriptionFetcher {

    private static final Logger log = LoggerFactory.getLogger(LinkedInJobDescriptionFetcher.class);

    private static final Pattern VIEW_JOB_URL = Pattern.compile(
            "(?i)View\\s+job\\s*:\\s*(https?://\\S+)");
    private static final Pattern JOB_ID = Pattern.compile("/jobs/view/(\\d+)");

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15_000;

    /**
     * Returns {@code job} with {@code jobDescription} filled from the LinkedIn page when a View-job
     * URL (or apply URL) can be resolved. On fetch/parse failure the original posting is returned.
     */
    public JobPosting enrich(JobPosting job) {
        String url = extractViewJobUrl(job.snippet(), job.id()).orElse(job.applyUrl());
        if (url == null || url.isBlank()) {
            log.warn("No View job URL for job {}", job.id());
            return job;
        }

        try {
            String description = fetchAboutTheJob(url, job.id());
            if (description.isBlank()) {
                log.warn("Empty aboutTheJob for {} ({})", job.title(), url);
                return job;
            }
            log.info("Fetched jobDescription ({} chars) for {}", description.length(), job.title());
            return job.withJobDescription(description);
        } catch (Exception exception) {
            log.warn("Failed to fetch jobDescription for {} ({}): {}", job.title(), url, exception.toString());
            return job;
        }
    }

    /**
     * Public for unit tests — pulls {@code View job: <url>} from alert text. When {@code jobId} is
     * set, prefers the URL that points at that posting (alerts often list several jobs).
     */
    static Optional<String> extractViewJobUrl(String snippet, String jobId) {
        if (snippet == null || snippet.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = VIEW_JOB_URL.matcher(snippet);
        Optional<String> first = Optional.empty();
        while (matcher.find()) {
            String url = matcher.group(1).replaceAll("[)\\]>,.;\"']+$", "");
            if (first.isEmpty()) {
                first = Optional.of(url);
            }
            if (jobId != null && !jobId.isBlank() && url.contains(jobId)) {
                return Optional.of(url);
            }
        }
        return jobId == null || jobId.isBlank() ? first : Optional.empty();
    }

    private String fetchAboutTheJob(String viewUrl, String jobId) throws IOException {
        String id = jobIdFrom(viewUrl).orElse(jobId);
        // Guest detail endpoint is the stable public HTML for the About-the-job body.
        if (id != null && !id.isBlank()) {
            String guestUrl = "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/" + id;
            Document guest = fetch(guestUrl);
            String fromGuest = parseAboutTheJob(guest);
            if (!fromGuest.isBlank()) {
                return fromGuest;
            }
        }
        return parseAboutTheJob(fetch(viewUrl));
    }

    private Document fetch(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get();
    }

    /** Extracts the About-the-job / {@code aboutTheJob} description body from a LinkedIn job page. */
    static String parseAboutTheJob(Document document) {
        if (document == null) {
            return "";
        }

        Element byClass = document.selectFirst(".show-more-less-html__markup, .description__text");
        if (byClass != null) {
            String text = clean(byClass.wholeText());
            if (!text.isBlank()) {
                return text;
            }
        }

        // Some layouts label the section "About the job" (camelCase aboutTheJob in product copy).
        for (Element heading : document.select("h2, h3")) {
            String title = clean(heading.text());
            if (title.equalsIgnoreCase("About the job") || title.equalsIgnoreCase("aboutTheJob")) {
                Element section = heading.closest("section");
                Element content = section != null
                        ? section.selectFirst(".core-section-container__content, .description__text, .show-more-less-html__markup")
                        : heading.nextElementSibling();
                if (content != null) {
                    String text = clean(content.wholeText());
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }

        Element jsonLd = document.selectFirst("script[type=application/ld+json]");
        if (jsonLd != null) {
            String raw = jsonLd.data();
            Matcher matcher = Pattern.compile("\"description\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(raw);
            if (matcher.find()) {
                return clean(Jsoup.parse(unescapeJson(matcher.group(1))).wholeText());
            }
        }
        return "";
    }

    private static Optional<String> jobIdFrom(String url) {
        if (url == null) {
            return Optional.empty();
        }
        Matcher matcher = JOB_ID.matcher(url);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').replaceAll("[ \\t\\x0B\\f]+", " ").replaceAll("\\R{3,}", "\n\n").trim();
    }
}
